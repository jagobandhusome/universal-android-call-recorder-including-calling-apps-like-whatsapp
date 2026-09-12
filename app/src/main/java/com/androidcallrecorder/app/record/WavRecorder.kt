package com.androidcallrecorder.app.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import com.androidcallrecorder.app.data.VoiceTrack
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class WavRecorder(
    private val sampleRate: Int,
    private val voiceTrack: VoiceTrack,
    private val projection: MediaProjection?,
) {
    private var record: AudioRecord? = null
    @Volatile private var running = false
    @Volatile private var writing = true
    private var worker: Thread? = null
    private var bytesWritten = 0

    @SuppressLint("MissingPermission")
    fun start(output: File) {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)
        val builder = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channel)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)

        if (voiceTrack == VoiceTrack.REMOTE_ONLY && projection != null && Build.VERSION.SDK_INT >= 29) {
            val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .build()
            builder.setAudioPlaybackCaptureConfig(capture)
        } else {
            val source = when (voiceTrack) {
                VoiceTrack.LOCAL_ONLY -> MediaRecorder.AudioSource.MIC
                VoiceTrack.REMOTE_ONLY -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                VoiceTrack.BOTH -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
            builder.setAudioSource(source)
        }

        val rec = builder.build()
        record = rec
        output.parentFile?.mkdirs()
        val raf = RandomAccessFile(output, "rw")
        writeHeader(raf, sampleRate, 0)
        running = true
        writing = true
        rec.startRecording()
        worker = thread(name = "wav-recorder") {
            val buf = ByteArray(bufferSize)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0 && writing) {
                        raf.write(buf, 0, n)
                        bytesWritten += n
                    }
                }
            } finally {
                writeHeader(raf, sampleRate, bytesWritten)
                raf.close()
            }
        }
    }

    fun setWriting(enabled: Boolean) {
        writing = enabled
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        record?.release()
        record = null
        worker?.join(1500)
        worker = null
    }

    private fun writeHeader(raf: RandomAccessFile, rate: Int, dataBytes: Int) {
        val byteRate = rate * 2
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataBytes)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1)
        raf.writeShortLE(1)
        raf.writeIntLE(rate)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(2)
        raf.writeShortLE(16)
        raf.writeBytes("data")
        raf.writeIntLE(dataBytes)
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
        write(value shr 16 and 0xff)
        write(value shr 24 and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
    }
}
