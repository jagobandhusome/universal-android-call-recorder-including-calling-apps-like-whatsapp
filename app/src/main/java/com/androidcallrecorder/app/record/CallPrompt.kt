package com.androidcallrecorder.app.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.androidcallrecorder.app.CallPromptActivity
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.SupportedApps

object CallPrompt {
    @Volatile private var lastShownAt = 0L
    @Volatile private var lastKey = ""

    fun show(
        context: Context,
        source: String,
        contact: String = "",
        number: String = "",
        direction: CallDirection = CallDirection.UNKNOWN,
        preferVideo: Boolean = false,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (RecordingSession.ui.value.active) return
        val key = "$source|$number|$direction"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastShownAt < 8_000) return
        lastKey = key
        lastShownAt = now
        val intent = Intent(context, CallPromptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CallPromptActivity.EXTRA_SOURCE, source)
            .putExtra(CallPromptActivity.EXTRA_CONTACT, contact)
            .putExtra(CallPromptActivity.EXTRA_NUMBER, number)
            .putExtra(CallPromptActivity.EXTRA_DIRECTION, direction.name)
            .putExtra(CallPromptActivity.EXTRA_PREFER_VIDEO, preferVideo)
        context.startActivity(intent)
        notify(context, source, contact, preferVideo)
    }

    private fun notify(context: Context, source: String, contact: String, preferVideo: Boolean) {
        Notifications.ensure(context)
        val audio = Notifications.promptAction(context, 21, source, contact, video = false)
        val video = Notifications.promptAction(context, 22, source, contact, video = true)
        val label = SupportedApps.labelFor(source)
        val n = androidx.core.app.NotificationCompat.Builder(context, Notifications.WATCH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$label call")
            .setContentText(contact.ifBlank { "Record audio or video?" })
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_btn_speak_now, "Audio", audio)
            .addAction(android.R.drawable.ic_menu_camera, "Video", video)
            .setAutoCancel(true)
            .setColor(context.getColor(com.androidcallrecorder.app.R.color.notification_color))
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mgr.notify(Notifications.WATCH_ID + 11, n)
        }
        if (preferVideo) {
            // hint only — user still chooses
        }
    }
}
