package com.androidcallrecorder.app.record

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.androidcallrecorder.app.data.ConvertTarget
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

object FormatConverter {
    fun convert(item: RecordingItem, target: ConvertTarget): RecordingItem {
        val src = File(item.filePath)
        require(src.exists()) { "File missing" }
        val dest = File(src.parentFile, "${src.nameWithoutExtension}-converted.${target.extension}")
        when (target) {
            ConvertTarget.WAV -> decodeToWav(src, dest)
            ConvertTarget.M4A, ConvertTarget.AAC, ConvertTarget.MP3, ConvertTarget.OGG ->
                extractOrRemuxAac(src, dest)
            ConvertTarget.MP4, ConvertTarget.AVI, ConvertTarget.MKV -> {
                if (item.kind == MediaKind.AUDIO) extractOrRemuxAac(src, dest)
                else src.copyTo(dest, overwrite = true)
            }
        }
        val kind = if (target == ConvertTarget.WAV || item.kind == MediaKind.AUDIO) MediaKind.AUDIO else item.kind
        val out = item.copy(
            id = RecordingStore.newId(),
            filePath = dest.absolutePath,
            displayName = dest.name,
            sizeBytes = dest.length(),
            kind = kind,
            format = target.extension,
        )
        RecordingStore.add(out)
        return out
    }

    fun videoToAudio(item: RecordingItem, target: ConvertTarget): RecordingItem {
        return convert(item, target)
    }

    private fun decodeToWav(src: File, dest: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(src.absolutePath)
        val track = selectAudioTrack(extractor) ?: error("No audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val raf = RandomAccessFile(dest, "rw")
        writeWavHeader(raf, sampleRate, 0)
        var dataBytes = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                val chunk = ByteArray(info.size)
                outBuf.position(info.offset)
                outBuf.get(chunk)
                raf.write(chunk)
                dataBytes += chunk.size
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        writeWavHeader(raf, sampleRate, dataBytes)
        raf.close()
        codec.stop()
        codec.release()
        extractor.release()
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun writeWavHeader(raf: RandomAccessFile, rate: Int, dataBytes: Int) {
        raf.seek(0)
        raf.writeBytes("RIFF")
        writeIntLE(raf, 36 + dataBytes)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1)
        writeShortLE(raf, 1)
        writeIntLE(raf, rate)
        writeIntLE(raf, rate * 2)
        writeShortLE(raf, 2)
        writeShortLE(raf, 16)
        raf.writeBytes("data")
        writeIntLE(raf, dataBytes)
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value shr 8 and 0xff)
        raf.write(value shr 16 and 0xff)
        raf.write(value shr 24 and 0xff)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value shr 8 and 0xff)
    }

    fun durationLabel(ms: Long) = FileNamer.formatDuration(ms)

    fun trim(item: RecordingItem, startMs: Long, endMs: Long): RecordingItem {
        val src = File(item.filePath)
        require(src.exists()) { "File missing" }
        val dest = File(src.parentFile, "${src.nameWithoutExtension}-trim.${src.extension}")
        extractOrRemuxAac(src, dest, startMs * 1000, endMs * 1000)
        val out = item.copy(
            id = RecordingStore.newId(),
            filePath = dest.absolutePath,
            displayName = dest.name,
            durationMs = (endMs - startMs).coerceAtLeast(0),
            sizeBytes = dest.length(),
        )
        RecordingStore.add(out)
        return out
    }

    fun merge(first: RecordingItem, second: RecordingItem): RecordingItem {
        val a = File(first.filePath)
        val b = File(second.filePath)
        require(a.exists() && b.exists()) { "File missing" }
        val dest = File(a.parentFile, "${a.nameWithoutExtension}-merge.wav")
        decodeToWav(a, dest)
        val tmp = File(a.parentFile, "merge-b.wav")
        decodeToWav(b, tmp)
        appendWav(dest, tmp)
        tmp.delete()
        val out = first.copy(
            id = RecordingStore.newId(),
            filePath = dest.absolutePath,
            displayName = dest.name,
            durationMs = first.durationMs + second.durationMs,
            sizeBytes = dest.length(),
            kind = MediaKind.AUDIO,
            format = "wav",
        )
        RecordingStore.add(out)
        return out
    }

    private fun extractOrRemuxAac(
        src: File,
        dest: File,
        startUs: Long = 0,
        endUs: Long = Long.MAX_VALUE,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(src.absolutePath)
        val track = selectAudioTrack(extractor) ?: error("No audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val destTrack = muxer.addTrack(format)
        muxer.start()
        val buffer = ByteBuffer.allocate(1024 * 256)
        val info = MediaCodec.BufferInfo()
        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            val time = extractor.sampleTime
            if (time >= 0 && time > endUs) break
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = time
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(destTrack, buffer, info)
            extractor.advance()
        }
        muxer.stop()
        muxer.release()
        extractor.release()
    }

    private fun appendWav(target: File, extra: File) {
        val extraBytes = extra.readBytes().let { if (it.size > 44) it.copyOfRange(44, it.size) else ByteArray(0) }
        RandomAccessFile(target, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(extraBytes)
            val data = (raf.length() - 44).toInt().coerceAtLeast(0)
            raf.seek(4)
            writeIntLE(raf, 36 + data)
            raf.seek(40)
            writeIntLE(raf, data)
        }
    }
}
