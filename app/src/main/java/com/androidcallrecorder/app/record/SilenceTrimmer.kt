package com.androidcallrecorder.app.record

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.abs

object SilenceTrimmer {
    fun removeSilence(item: RecordingItem, threshold: Int = 600): RecordingItem {
        val src = File(item.filePath)
        require(src.exists()) { "File missing" }
        val pcm = decodePcm(src)
        val kept = ByteArray(pcm.size)
        var w = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
            val signed = sample.toShort().toInt()
            if (abs(signed) >= threshold) {
                kept[w++] = pcm[i]
                kept[w++] = pcm[i + 1]
            }
            i += 2
        }
        val dest = File(src.parentFile, "${src.nameWithoutExtension}-nosilence.wav")
        writeWav(dest, kept.copyOf(w), 16_000)
        val out = item.copy(
            id = RecordingStore.newId(),
            filePath = dest.absolutePath,
            displayName = dest.name,
            sizeBytes = dest.length(),
            kind = MediaKind.AUDIO,
            format = "wav",
        )
        RecordingStore.add(out)
        return out
    }

    fun amplitudes(path: String, buckets: Int = 80): List<Float> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        return try {
            val pcm = decodePcm(file)
            if (pcm.size < 4) return emptyList()
            val step = (pcm.size / 2 / buckets).coerceAtLeast(1)
            buildList {
                var i = 0
                while (size < buckets && i + 1 < pcm.size) {
                    var peak = 0
                    repeat(step) {
                        if (i + 1 >= pcm.size) return@repeat
                        val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
                        peak = maxOf(peak, abs(sample.toShort().toInt()))
                        i += 2
                    }
                    add((peak / 32768f).coerceIn(0f, 1f))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun nextLoudOffset(path: String, fromMs: Long, durationMs: Long): Long {
        val amps = amplitudes(path, 120)
        if (amps.isEmpty() || durationMs <= 0) return fromMs
        val start = ((fromMs.toDouble() / durationMs) * amps.size).toInt().coerceIn(0, amps.lastIndex)
        val idx = (start + 1 until amps.size).firstOrNull { amps[it] > 0.08f } ?: start
        return (idx.toLong() * durationMs / amps.size).coerceIn(0, durationMs)
    }

    private fun decodePcm(src: File): ByteArray {
        if (src.extension.equals("wav", true) && src.length() > 44) {
            return src.readBytes().copyOfRange(44, src.length().toInt())
        }
        val extractor = MediaExtractor()
        extractor.setDataSource(src.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
        } ?: error("No audio")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val out = ArrayList<Byte>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(8_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 8_000)
            if (outIndex >= 0) {
                val buf = codec.getOutputBuffer(outIndex)!!
                val chunk = ByteArray(info.size)
                buf.position(info.offset)
                buf.get(chunk)
                out.addAll(chunk.toList())
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return out.toByteArray()
    }

    private fun writeWav(dest: File, pcm: ByteArray, rate: Int) {
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            writeInt(raf, 36 + pcm.size)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeInt(raf, 16)
            writeShort(raf, 1)
            writeShort(raf, 1)
            writeInt(raf, rate)
            writeInt(raf, rate * 2)
            writeShort(raf, 2)
            writeShort(raf, 16)
            raf.writeBytes("data")
            writeInt(raf, pcm.size)
            raf.write(pcm)
        }
    }

    private fun writeInt(raf: RandomAccessFile, v: Int) {
        raf.write(v and 0xff); raf.write(v shr 8 and 0xff)
        raf.write(v shr 16 and 0xff); raf.write(v shr 24 and 0xff)
    }

    private fun writeShort(raf: RandomAccessFile, v: Int) {
        raf.write(v and 0xff); raf.write(v shr 8 and 0xff)
    }
}
