package com.androidcallrecorder.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object SettingsStore {
    private const val PREFS = "call_recorder_settings"

    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = read(p)
    }

    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        write(next)
    }

    fun sourceEnabled(id: String): Boolean = current().enabledSources.contains(id)

    fun reset(keepUnlock: Boolean = true) {
        val prev = current()
        val next = AppSettings(
            disclaimerAccepted = prev.disclaimerAccepted,
            pinHash = if (keepUnlock) prev.pinHash else "",
            patternHash = if (keepUnlock) prev.patternHash else "",
        )
        _settings.value = next
        write(next)
    }

    private fun read(p: SharedPreferences): AppSettings {
        val legacyAuto = p.getBoolean("auto_phone", true)
        val legacyDir = p.getString("direction", "BOTH")
        val migratedAuto = when {
            p.contains("auto_record") -> enumValueOr(p.getString("auto_record", null), AutoRecordMode.ALL)
            !legacyAuto -> AutoRecordMode.OFF
            legacyDir == "INCOMING_ONLY" -> AutoRecordMode.INCOMING
            legacyDir == "OUTGOING_ONLY" -> AutoRecordMode.OUTGOING
            else -> AutoRecordMode.ALL
        }
        val sources = readList(p.getString("enabled_sources", null)).toSet()
            .ifEmpty { defaultSources() }
        return AppSettings(
            themeMode = enumValueOr(p.getString("theme", null), ThemeMode.LIGHT),
            accent = enumValueOr(p.getString("accent", null), AccentOption.BLUE),
            language = enumValueOr(p.getString("language", null), AppLang.EN),
            bitRate = p.getInt("bit_rate", 128_000),
            sampleRate = p.getInt("sample_rate", 44_100),
            quality = enumValueOr(p.getString("quality", null), QualityPreset.HIGH),
            container = enumValueOr(p.getString("container", null), AudioContainer.M4A),
            durationLimit = enumValueOr(p.getString("duration", null), DurationLimit.UNLIMITED),
            channels = enumValueOr(p.getString("channels", null), ChannelMode.MONO),
            audioSource = enumValueOr(p.getString("audio_source", null), AudioSourceOption.VOICE_COMMUNICATION),
            phoneNamePattern = p.getString("phone_pattern", "phone-call-{number}-{time}-{duration}")
                ?: "phone-call-{number}-{time}-{duration}",
            appNamePattern = p.getString("app_pattern", "{app}-{name}-{time}-{duration}")
                ?: "{app}-{name}-{time}-{duration}",
            autoRecord = migratedAuto,
            voiceTrack = enumValueOr(p.getString("voice_track", null), VoiceTrack.BOTH),
            recordMode = enumValueOr(p.getString("record_mode", null), RecordMode.AUDIO),
            enabledSources = sources,
            pauseButtonEnabled = p.getBoolean("pause_button", true),
            muteMyMic = p.getBoolean("mute_my_mic", false),
            contactScope = enumValueOr(p.getString("contact_scope", null), ContactScope.ALL),
            specificNumbers = readList(p.getString("specific_numbers", "[]")),
            excludeNumbers = readList(p.getString("exclude_numbers", "[]")),
            videoResolution = enumValueOr(p.getString("video_res", null), VideoResolution.P720),
            videoFps = enumValueOr(p.getString("video_fps", null), VideoFps.F30),
            orientation = enumValueOr(p.getString("orientation", null), OrientationMode.AUTO),
            screenWithAudio = p.getBoolean("screen_audio", true),
            videoCallAsAudio = p.getBoolean("video_as_audio", false),
            folderLayout = enumValueOr(p.getString("folder_layout", null), FolderLayout.FLAT),
            saveTreeUri = p.getString("save_tree_uri", "").orEmpty(),
            autoDelete = enumValueOr(p.getString("auto_delete", null), AutoDeleteAfter.OFF),
            keepStarred = p.getBoolean("keep_starred", true),
            keepLocked = p.getBoolean("keep_locked", true),
            warnBeforeDelete = p.getBoolean("warn_delete", true),
            backupSchedule = enumValueOr(p.getString("backup_schedule", null), BackupSchedule.OFF),
            lockOnOpen = p.getBoolean("lock_open", false),
            lockList = p.getBoolean("lock_list", false),
            lockSettings = p.getBoolean("lock_settings", false),
            lockDelete = p.getBoolean("lock_delete", false),
            lockShare = p.getBoolean("lock_share", false),
            useBiometric = p.getBoolean("use_biometric", false),
            pinHash = p.getString("pin_hash", "").orEmpty(),
            patternHash = p.getString("pattern_hash", "").orEmpty(),
            watchCallingApps = p.getBoolean("watch_apps", true),
            autoOfferVoip = p.getBoolean("auto_offer_voip", true),
            disclaimerAccepted = p.getBoolean("disclaimer", false),
            shakeToRecord = p.getBoolean("shake_record", false),
            consentBeep = p.getBoolean("consent_beep", false),
            rootMode = p.getBoolean("root_mode", false),
            autoTranscribe = p.getBoolean("auto_transcribe", true),
            lowStorageMb = p.getInt("low_storage_mb", 500),
            promptOnCall = p.getBoolean("prompt_on_call", true),
        )
    }

    private fun write(s: AppSettings) {
        prefs?.edit()
            ?.putString("theme", s.themeMode.name)
            ?.putString("accent", s.accent.name)
            ?.putString("language", s.language.name)
            ?.putInt("bit_rate", s.bitRate)
            ?.putInt("sample_rate", s.sampleRate)
            ?.putString("quality", s.quality.name)
            ?.putString("container", s.container.name)
            ?.putString("duration", s.durationLimit.name)
            ?.putString("channels", s.channels.name)
            ?.putString("audio_source", s.audioSource.name)
            ?.putString("phone_pattern", s.phoneNamePattern)
            ?.putString("app_pattern", s.appNamePattern)
            ?.putString("auto_record", s.autoRecord.name)
            ?.putString("voice_track", s.voiceTrack.name)
            ?.putString("record_mode", s.recordMode.name)
            ?.putString("enabled_sources", JSONArray(s.enabledSources.toList()).toString())
            ?.putBoolean("pause_button", s.pauseButtonEnabled)
            ?.putBoolean("mute_my_mic", s.muteMyMic)
            ?.putString("contact_scope", s.contactScope.name)
            ?.putString("specific_numbers", JSONArray(s.specificNumbers).toString())
            ?.putString("exclude_numbers", JSONArray(s.excludeNumbers).toString())
            ?.putString("video_res", s.videoResolution.name)
            ?.putString("video_fps", s.videoFps.name)
            ?.putString("orientation", s.orientation.name)
            ?.putBoolean("screen_audio", s.screenWithAudio)
            ?.putBoolean("video_as_audio", s.videoCallAsAudio)
            ?.putString("folder_layout", s.folderLayout.name)
            ?.putString("save_tree_uri", s.saveTreeUri)
            ?.putString("auto_delete", s.autoDelete.name)
            ?.putBoolean("keep_starred", s.keepStarred)
            ?.putBoolean("keep_locked", s.keepLocked)
            ?.putBoolean("warn_delete", s.warnBeforeDelete)
            ?.putString("backup_schedule", s.backupSchedule.name)
            ?.putBoolean("lock_open", s.lockOnOpen)
            ?.putBoolean("lock_list", s.lockList)
            ?.putBoolean("lock_settings", s.lockSettings)
            ?.putBoolean("lock_delete", s.lockDelete)
            ?.putBoolean("lock_share", s.lockShare)
            ?.putBoolean("use_biometric", s.useBiometric)
            ?.putString("pin_hash", s.pinHash)
            ?.putString("pattern_hash", s.patternHash)
            ?.putBoolean("watch_apps", s.watchCallingApps)
            ?.putBoolean("auto_offer_voip", s.autoOfferVoip)
            ?.putBoolean("disclaimer", s.disclaimerAccepted)
            ?.putBoolean("shake_record", s.shakeToRecord)
            ?.putBoolean("consent_beep", s.consentBeep)
            ?.putBoolean("root_mode", s.rootMode)
            ?.putBoolean("auto_transcribe", s.autoTranscribe)
            ?.putInt("low_storage_mb", s.lowStorageMb)
            ?.putBoolean("prompt_on_call", s.promptOnCall)
            ?.apply()
    }

    private fun readList(raw: String?): List<String> {
        return try {
            val arr = JSONArray(raw ?: "[]")
            buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOr(raw: String?, fallback: T): T {
        return raw?.let { runCatching { java.lang.Enum.valueOf(T::class.java, it) }.getOrNull() } ?: fallback
    }
}
