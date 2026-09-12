package com.androidcallrecorder.app.record

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidcallrecorder.app.R
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.SupportedApps
import java.util.Timer
import java.util.TimerTask

class AppWatcherService : Service() {
    private var timer: Timer? = null
    private var lastOffered = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensure(this)
        val n = NotificationCompat.Builder(this, Notifications.WATCH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Watching calling apps")
            .setContentText("You will get a visible record prompt")
            .setOngoing(true)
            .setSilent(true)
            .setColor(getColor(R.color.notification_color))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifications.WATCH_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifications.WATCH_ID, n)
        } else {
            startForeground(Notifications.WATCH_ID, n)
        }
        timer = Timer()
        timer?.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() = scan()
            },
            2_000,
            3_000,
        )
    }

    private fun scan() {
        if (!SettingsStore.current().watchCallingApps) return
        if (!SettingsStore.current().promptOnCall && !SettingsStore.current().autoOfferVoip) return
        if (RecordingSession.ui.value.active) return
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15_000, now)
        val top = stats.maxByOrNull { it.lastTimeUsed }
        if (top == null) {
            lastOffered = ""
            return
        }
        val app = SupportedApps.match(top.packageName)
        if (app == null || app.id == "phone") {
            lastOffered = ""
            return
        }
        val listeners = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this)
        if (listeners.contains(packageName)) return
        if (app.id == lastOffered) return
        lastOffered = app.id
        CallPrompt.show(this, source = app.id, contact = app.label)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: android.content.Context) {
            val i = Intent(context, AppWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, AppWatcherService::class.java))
        }
    }
}
