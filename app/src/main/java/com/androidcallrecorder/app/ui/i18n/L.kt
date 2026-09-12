package com.androidcallrecorder.app.ui.i18n

import com.androidcallrecorder.app.data.AppLang
import com.androidcallrecorder.app.data.SettingsStore

object L {
    fun t(en: String, bn: String): String =
        if (SettingsStore.current().language == AppLang.BN) bn else en
}
