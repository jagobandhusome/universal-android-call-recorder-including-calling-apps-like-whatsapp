package com.androidcallrecorder.app.record

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.RecordingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    fun write(context: Context, item: RecordingItem, dest: Uri) {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f; isAntiAlias = true }
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
        var y = 48f
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        fun line(p: Paint, text: String) {
            if (y > 800) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(text, 40f, y, p)
            y += p.textSize + 8f
        }
        val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(item.startedAt))
        line(title, item.displayName)
        line(paint, "$date · ${item.source} · ${FileNamer.formatDuration(item.durationMs)}")
        if (item.notes.isNotBlank()) {
            line(title, "Notes")
            item.notes.chunked(88).forEach { line(paint, it) }
        }
        if (item.summary.isNotBlank()) {
            line(title, "Summary")
            item.summary.split('\n').forEach { line(paint, it) }
        }
        line(title, "Transcript")
        val body = item.transcript.ifBlank { "(No transcript yet)" }
        body.chunked(88).forEach { line(paint, it) }
        doc.finishPage(page)
        context.contentResolver.openOutputStream(dest)?.use { doc.writeTo(it) }
        doc.close()
    }
}
