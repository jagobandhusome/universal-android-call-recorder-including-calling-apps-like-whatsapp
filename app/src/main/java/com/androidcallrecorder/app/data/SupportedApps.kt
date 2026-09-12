package com.androidcallrecorder.app.data

data class CallingApp(
    val id: String,
    val label: String,
    val packages: List<String>,
)

object SupportedApps {
    val all = listOf(
        CallingApp("phone", "Phone", listOf("com.android.phone", "com.google.android.dialer", "com.samsung.android.dialer")),
        CallingApp("whatsapp", "WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
        CallingApp("messenger", "Messenger", listOf("com.facebook.orca", "com.facebook.mlite")),
        CallingApp("telegram", "Telegram", listOf("org.telegram.messenger", "org.telegram.messenger.web")),
        CallingApp("viber", "Viber", listOf("com.viber.voip")),
        CallingApp("imo", "IMO", listOf("com.imo.android.imoim")),
        CallingApp("wechat", "WeChat", listOf("com.tencent.mm")),
        CallingApp("signal", "Signal", listOf("org.thoughtcrime.securesms")),
        CallingApp("skype", "Skype", listOf("com.skype.raider")),
        CallingApp("meet", "Meet", listOf("com.google.android.apps.tachyon")),
        CallingApp("zoom", "Zoom", listOf("us.zoom.videomeetings")),
        CallingApp("discord", "Discord", listOf("com.discord")),
        CallingApp("line", "LINE", listOf("jp.naver.line.android")),
        CallingApp("instagram", "Instagram", listOf("com.instagram.android")),
        CallingApp("teams", "Teams", listOf("com.microsoft.teams", "com.microsoft.skype.teams")),
        CallingApp("botim", "BOTIM", listOf("im.thebot.messenger")),
        CallingApp("snapchat", "Snapchat", listOf("com.snapchat.android")),
        CallingApp("voice", "Google Voice", listOf("com.google.android.apps.googlevoice")),
        CallingApp("truecaller", "Truecaller", listOf("com.truecaller")),
        CallingApp("facebook", "Facebook", listOf("com.facebook.katana")),
        CallingApp("telegramx", "Telegram X", listOf("org.thunderdog.challegram")),
    )

    fun match(packageName: String): CallingApp? {
        return all.firstOrNull { app -> app.packages.any { it.equals(packageName, true) } }
    }

    fun labelFor(idOrPackage: String): String {
        all.firstOrNull { it.id == idOrPackage }?.let { return it.label }
        match(idOrPackage)?.let { return it.label }
        return idOrPackage.substringAfterLast('.').replaceFirstChar { it.titlecase() }
    }
}
