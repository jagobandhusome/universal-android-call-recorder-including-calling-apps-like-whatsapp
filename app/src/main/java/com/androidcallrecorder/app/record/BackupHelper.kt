package com.androidcallrecorder.app.record

import android.content.Context
import android.net.Uri
import com.androidcallrecorder.app.data.RecordingStore
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupHelper {
    fun exportToFile(context: Context, file: File): Int {
        file.parentFile?.mkdirs()
        return file.outputStream().use { writeZip(context, it) }
    }

    fun exportZip(context: Context, dest: Uri): Int {
        return context.contentResolver.openOutputStream(dest)?.use { writeZip(context, it) }
            ?: error("Could not open backup destination")
    }

    private fun writeZip(context: Context, raw: java.io.OutputStream): Int {
        val items = RecordingStore.all()
        var count = 0
        ZipOutputStream(raw).use { zip ->
            items.forEach { item ->
                val file = File(item.filePath)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry(file.name))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
            zip.putNextEntry(ZipEntry("recordings_index.json"))
            val index = File(context.filesDir, "recordings_index.json")
            if (index.exists()) {
                FileInputStream(index).use { it.copyTo(zip) }
            }
            zip.closeEntry()
        }
        return count
    }

    fun restoreZip(context: Context, src: Uri): Int {
        var count = 0
        val destDir = com.androidcallrecorder.app.data.StorageHelper.defaultDir(context)
        context.contentResolver.openInputStream(src)?.use { raw ->
            java.util.zip.ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        if (name == "recordings_index.json") {
                            File(context.filesDir, "recordings_index.json").outputStream().use { zip.copyTo(it) }
                        } else {
                            File(destDir, name).outputStream().use { zip.copyTo(it) }
                            count++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Could not open backup")
        RecordingStore.init(context)
        RecordingStore.reload()
        return count
    }
}
