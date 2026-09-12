package com.androidcallrecorder.app.record

import android.os.Environment
import android.os.StatFs
import com.androidcallrecorder.app.data.ConvertTarget
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.StorageHelper
import java.io.File

object StorageGuard {
    fun freeMb(): Long {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.availableBytes / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
    }

    fun isLow(): Boolean {
        val limit = SettingsStore.current().lowStorageMb
        return freeMb() in 1 until limit
    }

    fun compressOldest(maxFiles: Int = 3): Int {
        val candidates = RecordingStore.all()
            .filter { !it.locked && !it.encrypted && !it.missed && File(it.filePath).exists() }
            .sortedBy { it.startedAt }
            .take(maxFiles)
        var n = 0
        candidates.forEach { item ->
            try {
                FormatConverter.convert(item, ConvertTarget.M4A)
                n++
            } catch (_: Exception) {
            }
        }
        return n
    }

    fun usedLabel(dir: File): String {
        val bytes = StorageHelper.folderSize(dir)
        return "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }
}
