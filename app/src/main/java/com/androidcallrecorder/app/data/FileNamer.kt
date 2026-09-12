package com.androidcallrecorder.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FileNamer {
    private val timeFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun render(
        pattern: String,
        app: String,
        name: String,
        number: String,
        startedAt: Long,
        durationMs: Long,
    ): String {
        val duration = formatDuration(durationMs)
        val time = timeFmt.format(Date(startedAt))
        val raw = pattern
            .replace("{app}", sanitize(app.ifBlank { "app" }))
            .replace("{name}", sanitize(name.ifBlank { number.ifBlank { "unknown" } }))
            .replace("{number}", sanitize(number.ifBlank { name.ifBlank { "unknown" } }))
            .replace("{time}", time)
            .replace("{duration}", duration)
        return sanitize(raw)
    }

    fun formatDuration(ms: Long): String {
        val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%dh%02dm%02ds", h, m, s)
        else String.format(Locale.US, "%02dm%02ds", m, s)
    }

    fun sanitize(value: String): String {
        return value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
            .trim('-')
            .ifBlank { "recording" }
    }
}
