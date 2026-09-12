package com.androidcallrecorder.app.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androidcallrecorder.app.data.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        SettingsStore.init(context)
        CleanupWorker.schedule(context)
        if (SettingsStore.current().watchCallingApps) {
            AppWatcherService.start(context)
        }
    }
}
