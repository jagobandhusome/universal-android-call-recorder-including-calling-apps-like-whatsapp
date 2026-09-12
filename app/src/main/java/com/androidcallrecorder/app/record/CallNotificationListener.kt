package com.androidcallrecorder.app.record

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.SupportedApps

class CallNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        SettingsStore.init(this)
        val settings = SettingsStore.current()
        if (!settings.promptOnCall && !settings.autoOfferVoip) return

        val extras = n.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val sub = extras.getCharSequence("android.subText")?.toString().orEmpty()
        val cat = n.notification.category.orEmpty()
        val blob = "$title $text $sub ${n.notification.tickerText ?: ""}".lowercase()
        if (!looksLikeCall(cat, blob)) return

        val known = SupportedApps.match(n.packageName)
        if (known?.id == "phone" || isPhoneStack(n.packageName)) return

        val source = known?.id ?: n.packageName
        val label = known?.label ?: appLabel(n.packageName)
        val video = blob.contains("video") || blob.contains("ভিডিও")
        val direction = when {
            blob.contains("outgoing") || blob.contains("calling") || blob.contains("আউটগোয়িং") ->
                CallDirection.OUTGOING
            blob.contains("incoming") || blob.contains("ringing") || blob.contains("ইনকামিং") ->
                CallDirection.INCOMING
            else -> CallDirection.UNKNOWN
        }
        CallPrompt.show(
            context = this,
            source = source,
            contact = title.ifBlank { label },
            direction = direction,
            preferVideo = video,
        )
    }

    private fun looksLikeCall(category: String, blob: String): Boolean {
        if (category == Notification.CATEGORY_CALL) return true
        if (blob.contains("missed call") || blob.contains("মিসড")) return false
        val phrases = listOf(
            "incoming call",
            "outgoing call",
            "ongoing call",
            "ongoing voice",
            "voice call",
            "video call",
            "is calling",
            "incoming voice",
            "incoming video",
            "ringing",
            "calling…",
            "calling...",
            "call in progress",
            "in call",
            "on a call",
            "ইনকামিং",
            "আউটগোয়িং",
            "ভয়েস কল",
            "ভিডিও কল",
            "কল আসছে",
        )
        if (phrases.any { blob.contains(it) }) return true
        return blob.contains(" call") || blob.startsWith("call") || blob.contains("কল")
    }

    private fun isPhoneStack(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("incallui") ||
            p.contains(".dialer") ||
            p.contains("telecom") ||
            p == "com.android.phone" ||
            p.endsWith(".phone")
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }
}
