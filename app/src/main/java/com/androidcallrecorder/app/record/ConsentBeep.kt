package com.androidcallrecorder.app.record

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.sin

object ConsentBeep {
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "consent-beep") {
            while (running) {
                playTone()
                var waited = 0
                while (running && waited < 15_000) {
                    Thread.sleep(200)
                    waited += 200
                }
            }
        }
    }

    fun stop() {
        running = false
        worker = null
    }

    private fun playTone() {
        val rate = 16_000
        val duration = 0.18
        val samples = (rate * duration).toInt()
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val env = when {
                i < samples / 8 -> i / (samples / 8.0)
                i > samples * 7 / 8 -> (samples - i) / (samples / 8.0)
                else -> 1.0
            }
            buf[i] = (sin(2.0 * Math.PI * 880.0 * i / rate) * 0.35 * env * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(buf, 0, buf.size)
            track.play()
            Thread.sleep(220)
        } catch (_: Exception) {
        } finally {
            track.stop()
            track.release()
        }
    }
}
