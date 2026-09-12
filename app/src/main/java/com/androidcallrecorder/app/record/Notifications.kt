package com.androidcallrecorder.app.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.androidcallrecorder.app.MainActivity
import com.androidcallrecorder.app.R

object Notifications {
    const val RECORD_CHANNEL = "recording"
    const val WATCH_CHANNEL = "watch"
    const val RECORD_ID = 41
    const val WATCH_ID = 42

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                RECORD_CHANNEL,
                context.getString(R.string.recording_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.recording_channel_desc) },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                WATCH_CHANNEL,
                context.getString(R.string.watch_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.watch_channel_desc) },
        )
    }

    fun recording(context: Context, title: String, text: String, paused: Boolean): Notification {
        val open = pending(context, 1, Intent(context, MainActivity::class.java))
        val pause = pending(
            context,
            2,
            Intent(context, RecordingService::class.java).setAction(
                if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
            ),
        )
        val stop = pending(
            context,
            3,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
        )
        return NotificationCompat.Builder(context, RECORD_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                pause,
            )
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setColor(context.getColor(R.color.notification_color))
            .build()
    }

    fun promptAction(
        context: Context,
        req: Int,
        source: String,
        contact: String,
        video: Boolean,
    ): PendingIntent {
        val intent = Intent(context, com.androidcallrecorder.app.CallPromptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(com.androidcallrecorder.app.CallPromptActivity.EXTRA_SOURCE, source)
            .putExtra(com.androidcallrecorder.app.CallPromptActivity.EXTRA_CONTACT, contact)
            .putExtra(com.androidcallrecorder.app.CallPromptActivity.EXTRA_AUTO, if (video) "video" else "audio")
        return pending(context, req, intent)
    }

    fun watchOffer(context: Context, appLabel: String): Notification {
        val start = pending(
            context,
            4,
            Intent(context, com.androidcallrecorder.app.CallPromptActivity::class.java)
                .putExtra(com.androidcallrecorder.app.CallPromptActivity.EXTRA_SOURCE, appLabel),
        )
        return NotificationCompat.Builder(context, WATCH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$appLabel is open")
            .setContentText("Tap to start a visible recording")
            .setContentIntent(start)
            .setAutoCancel(true)
            .build()
    }

    private fun pending(context: Context, req: Int, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (intent.component?.className?.contains("RecordingService") == true) {
            PendingIntent.getService(context, req, intent, flags)
        } else {
            PendingIntent.getActivity(context, req, intent, flags)
        }
    }
}
