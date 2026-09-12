package com.androidcallrecorder.app.data

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class CallDirection { INCOMING, OUTGOING, UNKNOWN }

enum class AutoRecordMode { ALL, INCOMING, OUTGOING, OFF }

enum class VoiceTrack { BOTH, LOCAL_ONLY, REMOTE_ONLY }

enum class RecordMode { AUDIO, VIDEO_CALL, SCREEN, VIDEO_AS_AUDIO, MEMO }

enum class MediaKind { AUDIO, VIDEO, SCREEN }

enum class AudioSourceOption(val labelEn: String, val labelBn: String) {
    MIC("Mic", "মাইক"),
    VOICE_CALL("Voice call", "ভয়েস কল"),
    VOICE_RECOGNITION("Voice recognition", "ভয়েস রিকগনিশন"),
    VOICE_COMMUNICATION("Voice communication", "ভয়েস কমিউনিকেশন"),
}

enum class ChannelMode { MONO, STEREO }

enum class FolderLayout { FLAT, BY_DATE, BY_CONTACT, BY_APP }

enum class VideoResolution(val width: Int, val height: Int, val label: String) {
    P480(854, 480, "480p"),
    P720(1280, 720, "720p"),
    P1080(1920, 1080, "1080p"),
}

enum class VideoFps(val fps: Int) { F24(24), F30(30), F60(60) }

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

enum class AccentOption { BLUE, CYAN, VIOLET }

enum class AppLang { EN, BN }

enum class SortMode { NEWEST, OLDEST, LARGEST, LONGEST }

enum class GroupMode { NONE, CONTACT, DATE, APP }

enum class AudioContainer(val extension: String, val label: String) {
    M4A("m4a", "M4A"),
    AAC("aac", "AAC"),
    WAV("wav", "WAV"),
    THREE_GP("3gp", "3GP"),
    AMR("amr", "AMR"),
    OPUS("ogg", "OGG"),
    MP3("mp3", "MP3"),
}

enum class QualityPreset(val label: String, val bitRate: Int, val sampleRate: Int) {
    LOW("Low", 64_000, 16_000),
    MEDIUM("Medium", 96_000, 22_050),
    HIGH("High", 128_000, 44_100),
    ULTRA("Ultra", 192_000, 48_000),
}

enum class DurationLimit(val minutes: Int, val label: String) {
    FIVE(5, "5m"),
    FIFTEEN(15, "15m"),
    THIRTY(30, "30m"),
    SIXTY(60, "1h"),
    UNLIMITED(0, "Unlimited"),
}

enum class ContactScope { ALL, SPECIFIC, UNKNOWN_ONLY, EXCLUDE }

enum class AutoDeleteAfter(val days: Int, val label: String) {
    OFF(0, "Off"),
    DAYS_7(7, "7 days"),
    DAYS_15(15, "15 days"),
    DAYS_30(30, "30 days"),
    DAYS_60(60, "60 days"),
    DAYS_90(90, "90 days"),
    DAYS_180(180, "180 days"),
}

enum class ConvertTarget(val extension: String, val label: String) {
    MP3("mp3", "MP3"),
    AAC("aac", "AAC"),
    WAV("wav", "WAV"),
    OGG("ogg", "OGG"),
    M4A("m4a", "M4A"),
    MP4("mp4", "MP4"),
    AVI("avi", "AVI"),
    MKV("mkv", "MKV"),
}

enum class BackupSchedule { OFF, DAILY, WEEKLY }

data class RecordingItem(
    val id: String,
    val filePath: String,
    val displayName: String,
    val source: String,
    val contact: String,
    val number: String,
    val direction: CallDirection,
    val startedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val kind: MediaKind,
    val format: String,
    val starred: Boolean = false,
    val locked: Boolean = false,
    val missed: Boolean = false,
    val notes: String = "",
    val transcript: String = "",
    val summary: String = "",
    val encrypted: Boolean = false,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accent: AccentOption = AccentOption.BLUE,
    val language: AppLang = AppLang.EN,
    val bitRate: Int = 128_000,
    val sampleRate: Int = 44_100,
    val quality: QualityPreset = QualityPreset.HIGH,
    val container: AudioContainer = AudioContainer.M4A,
    val durationLimit: DurationLimit = DurationLimit.UNLIMITED,
    val channels: ChannelMode = ChannelMode.MONO,
    val audioSource: AudioSourceOption = AudioSourceOption.VOICE_COMMUNICATION,
    val phoneNamePattern: String = "phone-call-{number}-{time}-{duration}",
    val appNamePattern: String = "{app}-{name}-{time}-{duration}",
    val autoRecord: AutoRecordMode = AutoRecordMode.ALL,
    val voiceTrack: VoiceTrack = VoiceTrack.BOTH,
    val recordMode: RecordMode = RecordMode.AUDIO,
    val enabledSources: Set<String> = defaultSources(),
    val pauseButtonEnabled: Boolean = true,
    val muteMyMic: Boolean = false,
    val contactScope: ContactScope = ContactScope.ALL,
    val specificNumbers: List<String> = emptyList(),
    val excludeNumbers: List<String> = emptyList(),
    val videoResolution: VideoResolution = VideoResolution.P720,
    val videoFps: VideoFps = VideoFps.F30,
    val orientation: OrientationMode = OrientationMode.AUTO,
    val screenWithAudio: Boolean = true,
    val videoCallAsAudio: Boolean = false,
    val folderLayout: FolderLayout = FolderLayout.FLAT,
    val saveTreeUri: String = "",
    val autoDelete: AutoDeleteAfter = AutoDeleteAfter.OFF,
    val keepStarred: Boolean = true,
    val keepLocked: Boolean = true,
    val warnBeforeDelete: Boolean = true,
    val backupSchedule: BackupSchedule = BackupSchedule.OFF,
    val lockOnOpen: Boolean = false,
    val lockList: Boolean = false,
    val lockSettings: Boolean = false,
    val lockDelete: Boolean = false,
    val lockShare: Boolean = false,
    val useBiometric: Boolean = false,
    val pinHash: String = "",
    val patternHash: String = "",
    val watchCallingApps: Boolean = true,
    val autoOfferVoip: Boolean = true,
    val disclaimerAccepted: Boolean = false,
    val shakeToRecord: Boolean = false,
    val consentBeep: Boolean = false,
    val rootMode: Boolean = false,
    val autoTranscribe: Boolean = true,
    val lowStorageMb: Int = 500,
    val promptOnCall: Boolean = true,
)

fun defaultSources(): Set<String> = SupportedApps.all.map { it.id }.toSet()
