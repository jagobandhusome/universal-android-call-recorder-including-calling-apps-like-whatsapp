package com.androidcallrecorder.app.record

import java.io.File

object RootAccess {
    fun isDeviceRooted(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
        )
        return paths.any { File(it).exists() }
    }
}
