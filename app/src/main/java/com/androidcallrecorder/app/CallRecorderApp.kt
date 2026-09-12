package com.androidcallrecorder.app

import android.app.Application
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.record.CleanupWorker
import com.androidcallrecorder.app.record.Notifications
import com.androidcallrecorder.app.record.PhoneStateMonitor
import com.androidcallrecorder.app.record.ShakeMonitor

class CallRecorderApp : Application() {
    lateinit var phoneMonitor: PhoneStateMonitor
        private set
    private var shakeMonitor: ShakeMonitor? = null

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        RecordingStore.init(this)
        Notifications.ensure(this)
        CleanupWorker.schedule(this)
        phoneMonitor = PhoneStateMonitor(this).also { it.start() }
        shakeMonitor = ShakeMonitor(this).also { it.start() }
    }
}
