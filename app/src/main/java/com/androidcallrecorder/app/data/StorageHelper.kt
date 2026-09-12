package com.androidcallrecorder.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageHelper {
    fun defaultDir(context: Context): File {
        val ext = context.getExternalFilesDir("Recordings")
        val dir = ext ?: File(context.filesDir, "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun folderFor(context: Context, source: String, contact: String, startedAt: Long): File {
        val root = defaultDir(context)
        val sub = when (SettingsStore.current().folderLayout) {
            FolderLayout.FLAT -> ""
            FolderLayout.BY_DATE -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startedAt))
            FolderLayout.BY_CONTACT -> FileNamer.sanitize(contact.ifBlank { "unknown" })
            FolderLayout.BY_APP -> FileNamer.sanitize(source.ifBlank { "app" })
        }
        val dir = if (sub.isBlank()) root else File(root, sub)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun createTempFile(context: Context, extension: String): File {
        val dir = defaultDir(context)
        return File(dir, "in-progress-${System.currentTimeMillis()}.$extension")
    }

    fun finalizeFile(
        context: Context,
        temp: File,
        finalName: String,
        source: String = "phone",
        contact: String = "",
        startedAt: Long = System.currentTimeMillis(),
    ): File {
        val destDir = folderFor(context, source, contact, startedAt)
        var dest = File(destDir, finalName)
        var i = 2
        while (dest.exists()) {
            dest = File(destDir, "${finalName.substringBeforeLast('.')}-$i.${finalName.substringAfterLast('.')}")
            i++
        }
        if (temp.renameTo(dest)) return dest
        temp.copyTo(dest, overwrite = false)
        temp.delete()
        copyToTreeIfNeeded(context, dest)
        return dest
    }

    fun copyToTreeIfNeeded(context: Context, file: File) {
        val tree = SettingsStore.current().saveTreeUri
        if (tree.isBlank()) return
        val root = DocumentFile.fromTreeUri(context, Uri.parse(tree)) ?: return
        val existing = root.findFile(file.name)
        val target = existing ?: root.createFile(mimeFor(file), file.name) ?: return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
            FileInputStream(file).use { it.copyTo(out) }
        }
    }

    fun mimeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "3gp" -> "audio/3gpp"
            "amr" -> "audio/amr"
            "ogg", "opus" -> "audio/ogg"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
