package com.androidcallrecorder.app.record

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.androidcallrecorder.app.data.AudioContainer
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.FileNamer
import com.androidcallrecorder.app.data.MediaKind
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.RecordingItem
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.StorageHelper
import com.androidcallrecorder.app.data.VoiceTrack
import java.io.File
import java.util.Timer
import java.util.TimerTask

class RecordingService : Service() {
    private var mediaRecorder: MediaRecorder? = null
    private var wavRecorder: WavRecorder? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var tempFile: File? = null
    private var timer: Timer? = null
    private var baseElapsed = 0L
    private var runningSince = 0L
    private var usingWav = false
    private var transcriber: LiveTranscriber? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensure(this)
        RecordingStore.init(this)
        SettingsStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAndSave()
            ACTION_START -> startFromIntent(intent)
            else -> startFromIntent(intent)
        }
        return START_STICKY
    }

    private fun startFromIntent(intent: Intent?) {
        if (intent == null) return
        if (RecordingSession.ui.value.active) return
        val settings = SettingsStore.current()
        val mode = RecordMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: settings.recordMode.name)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "phone"
        val contact = intent.getStringExtra(EXTRA_CONTACT).orEmpty()
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val direction = runCatching {
            CallDirection.valueOf(intent.getStringExtra(EXTRA_DIRECTION) ?: CallDirection.UNKNOWN.name)
        }.getOrDefault(CallDirection.UNKNOWN)
        val voice = if (settings.muteMyMic) {
            VoiceTrack.REMOTE_ONLY
        } else {
            runCatching {
                VoiceTrack.valueOf(intent.getStringExtra(EXTRA_VOICE) ?: settings.voiceTrack.name)
            }.getOrDefault(settings.voiceTrack)
        }

        RecordingSession.update {
            it.copy(
                active = true,
                paused = false,
                mode = mode,
                source = source,
                contact = contact,
                number = number,
                direction = direction,
                voiceTrack = voice,
                startedAt = System.currentTimeMillis(),
                elapsedMs = 0,
                message = "",
            )
        }

        val notification = Notifications.recording(
            this,
            "Recording ${modeLabel(mode)}",
            visibleStatus(source, voice),
            paused = false,
        )
        val hasProjection = intent.hasExtra(EXTRA_RESULT_DATA)
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if ((mode == RecordMode.VIDEO_CALL || mode == RecordMode.SCREEN) && hasProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(Notifications.RECORD_ID, notification, type)
        } else {
            startForeground(Notifications.RECORD_ID, notification)
        }

        try {
            when (mode) {
                RecordMode.AUDIO, RecordMode.VIDEO_AS_AUDIO, RecordMode.MEMO -> startAudio(mode, voice, intent)
                RecordMode.VIDEO_CALL, RecordMode.SCREEN -> {
                    if (hasProjection) startProjectionCapture(mode, voice, intent)
                    else {
                        startAudio(RecordMode.AUDIO, voice, intent)
                        RecordingSession.update {
                            it.copy(mode = RecordMode.AUDIO, message = "Microphone / speaker fallback")
                        }
                    }
                }
            }
            runningSince = SystemClock.elapsedRealtime()
            baseElapsed = 0
            startTimer()
            if (settings.autoTranscribe) {
                transcriber = LiveTranscriber(this).also { it.start() }
            }
            if (settings.consentBeep) ConsentBeep.start()
        } catch (e: Exception) {
            try {
                startAudio(RecordMode.AUDIO, VoiceTrack.BOTH, intent)
                RecordingSession.update {
                    it.copy(mode = RecordMode.AUDIO, message = "Microphone / speaker fallback: ${e.message}")
                }
                runningSince = SystemClock.elapsedRealtime()
                baseElapsed = 0
                startTimer()
                if (settings.autoTranscribe) {
                    transcriber = LiveTranscriber(this).also { it.start() }
                }
            } catch (e2: Exception) {
                RecordingSession.reset("Could not start: ${e2.message}")
                stopSelf()
            }
        }
    }

    private fun startAudio(mode: RecordMode, voice: VoiceTrack, intent: Intent) {
        val settings = SettingsStore.current()
        val useInternal = mode == RecordMode.VIDEO_AS_AUDIO || voice == VoiceTrack.REMOTE_ONLY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (useInternal && data != null) {
            projection = projectionManager().getMediaProjection(resultCode, data)
        }
        val needWav = settings.container == AudioContainer.WAV ||
            (useInternal && projection != null && voice == VoiceTrack.REMOTE_ONLY)
        if (needWav) {
            usingWav = true
            tempFile = StorageHelper.createTempFile(this, "wav")
            wavRecorder = WavRecorder(settings.sampleRate, voice, projection).also {
                it.start(tempFile!!)
            }
            return
        }
        usingWav = false
        val ext = when (settings.container) {
            AudioContainer.WAV -> "wav"
            AudioContainer.THREE_GP -> "3gp"
            AudioContainer.AMR -> "amr"
            AudioContainer.OPUS -> "ogg"
            AudioContainer.AAC -> "aac"
            AudioContainer.MP3, AudioContainer.M4A -> "m4a"
        }
        tempFile = StorageHelper.createTempFile(this, ext)
        val sources = buildList {
            if (mode == RecordMode.MEMO) {
                add(android.media.MediaRecorder.AudioSource.MIC)
            } else {
                add(audioSourceFor(voice))
                add(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                add(android.media.MediaRecorder.AudioSource.MIC)
            }
        }.distinct()
        var last: Exception? = null
        for (src in sources) {
            try {
                val rec = createRecorder()
                rec.setAudioSource(src)
                applyAudioOutput(rec, settings.container)
                rec.setAudioEncodingBitRate(settings.bitRate)
                rec.setAudioSamplingRate(settings.sampleRate)
                applyMaxDuration(rec)
                rec.setOutputFile(tempFile!!.absolutePath)
                rec.prepare()
                rec.start()
                mediaRecorder = rec
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Could not start microphone")
    }

    private fun startProjectionCapture(mode: RecordMode, voice: VoiceTrack, intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: error("Screen capture permission is required")
        projection = projectionManager().getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val settings = SettingsStore.current()
        val (width, height) = videoSize(metrics, settings)
        tempFile = StorageHelper.createTempFile(this, "mp4")
        val rec = createRecorder()
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        if (settings.screenWithAudio || mode == RecordMode.VIDEO_CALL) {
            rec.setAudioSource(audioSourceFor(voice))
        }
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (settings.screenWithAudio || mode == RecordMode.VIDEO_CALL) {
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        rec.setVideoSize(width, height)
        rec.setVideoFrameRate(settings.videoFps.fps)
        rec.setVideoEncodingBitRate(if (settings.videoResolution == com.androidcallrecorder.app.data.VideoResolution.P1080) 10_000_000 else 6_000_000)
        rec.setAudioEncodingBitRate(SettingsStore.current().bitRate)
        rec.setAudioSamplingRate(SettingsStore.current().sampleRate)
        applyMaxDuration(rec)
        rec.setOutputFile(tempFile!!.absolutePath)
        rec.prepare()
        virtualDisplay = projection?.createVirtualDisplay(
            "call-recorder",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            rec.surface,
            null,
            null,
        )
        rec.start()
        mediaRecorder = rec
        if (mode == RecordMode.SCREEN) {
            // already configured as video+mic
        }
    }

    private fun pause() {
        val ui = RecordingSession.ui.value
        if (!ui.active || ui.paused) return
        try {
            if (usingWav) wavRecorder?.setWriting(false) else mediaRecorder?.pause()
        } catch (_: Exception) {
        }
        baseElapsed += SystemClock.elapsedRealtime() - runningSince
        RecordingSession.update { it.copy(paused = true, elapsedMs = baseElapsed) }
        refreshNotification(true)
    }

    private fun resume() {
        val ui = RecordingSession.ui.value
        if (!ui.active || !ui.paused) return
        try {
            if (usingWav) wavRecorder?.setWriting(true) else mediaRecorder?.resume()
        } catch (_: Exception) {
        }
        runningSince = SystemClock.elapsedRealtime()
        RecordingSession.update { it.copy(paused = false) }
        refreshNotification(false)
    }

    private fun stopAndSave() {
        timer?.cancel()
        timer = null
        val ui = RecordingSession.ui.value
        val elapsed = if (ui.paused) ui.elapsedMs
        else baseElapsed + (if (runningSince > 0) SystemClock.elapsedRealtime() - runningSince else 0)
        try {
            if (usingWav) {
                wavRecorder?.stop()
            } else {
                mediaRecorder?.stop()
            }
        } catch (_: Exception) {
        }
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        wavRecorder = null
        val transcript = transcriber?.text().orEmpty().ifBlank { ui.liveTranscript }
        transcriber?.stop()
        transcriber = null
        ConsentBeep.stop()
        val summary = Summarizer.threeLines(transcript)
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null

        val temp = tempFile
        tempFile = null
        if (temp != null && temp.exists() && temp.length() > 44) {
            val settings = SettingsStore.current()
            val pattern = if (ui.source == "phone") settings.phoneNamePattern else settings.appNamePattern
            val base = FileNamer.render(
                pattern = pattern,
                app = ui.source,
                name = ui.contact,
                number = ui.number,
                startedAt = ui.startedAt,
                durationMs = elapsed,
            )
            val ext = temp.extension
            val dest = StorageHelper.finalizeFile(
                this,
                temp,
                "$base.$ext",
                source = ui.source,
                contact = ui.contact.ifBlank { ui.number },
                startedAt = ui.startedAt,
            )
            StorageHelper.copyToTreeIfNeeded(this, dest)
            val kind = when (ui.mode) {
                RecordMode.SCREEN -> MediaKind.SCREEN
                RecordMode.VIDEO_CALL -> MediaKind.VIDEO
                else -> MediaKind.AUDIO
            }
            RecordingStore.add(
                RecordingItem(
                    id = RecordingStore.newId(),
                    filePath = dest.absolutePath,
                    displayName = dest.name,
                    source = ui.source,
                    contact = ui.contact.ifBlank { ui.number },
                    number = ui.number,
                    direction = ui.direction,
                    startedAt = ui.startedAt,
                    durationMs = elapsed,
                    sizeBytes = dest.length(),
                    kind = kind,
                    format = ext,
                    transcript = transcript,
                    summary = summary,
                ),
            )
            RecordingSession.reset("Saved ${dest.name}")
        } else {
            temp?.delete()
            RecordingSession.reset("Recording was too short or failed")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    val ui = RecordingSession.ui.value
                    if (!ui.active || ui.paused) return
                    val elapsed = baseElapsed + SystemClock.elapsedRealtime() - runningSince
                    RecordingSession.update { it.copy(elapsedMs = elapsed) }
                }
            },
            500,
            500,
        )
    }

    private fun refreshNotification(paused: Boolean) {
        val ui = RecordingSession.ui.value
        val n = Notifications.recording(
            this,
            if (paused) "Paused" else "Recording ${modeLabel(ui.mode)}",
            visibleStatus(ui.source, ui.voiceTrack),
            paused,
        )
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(Notifications.RECORD_ID, n)
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
    }

    private fun audioSourceFor(track: VoiceTrack): Int {
        if (track == VoiceTrack.LOCAL_ONLY) return MediaRecorder.AudioSource.MIC
        val settings = SettingsStore.current()
        if (settings.rootMode && RootAccess.isDeviceRooted()) {
            return MediaRecorder.AudioSource.VOICE_CALL
        }
        return when (settings.audioSource) {
            com.androidcallrecorder.app.data.AudioSourceOption.MIC -> MediaRecorder.AudioSource.MIC
            com.androidcallrecorder.app.data.AudioSourceOption.VOICE_CALL -> MediaRecorder.AudioSource.VOICE_CALL
            com.androidcallrecorder.app.data.AudioSourceOption.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            com.androidcallrecorder.app.data.AudioSourceOption.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    private fun videoSize(
        metrics: DisplayMetrics,
        settings: com.androidcallrecorder.app.data.AppSettings,
    ): Pair<Int, Int> {
        val portrait = metrics.heightPixels >= metrics.widthPixels
        val targetW = settings.videoResolution.width
        val targetH = settings.videoResolution.height
        return when (settings.orientation) {
            com.androidcallrecorder.app.data.OrientationMode.PORTRAIT ->
                if (portrait) targetH to targetW else targetH to targetW
            com.androidcallrecorder.app.data.OrientationMode.LANDSCAPE -> targetW to targetH
            com.androidcallrecorder.app.data.OrientationMode.AUTO ->
                if (portrait) targetH to targetW else targetW to targetH
        }
    }

    private fun applyAudioOutput(rec: MediaRecorder, container: AudioContainer) {
        when (container) {
            AudioContainer.THREE_GP -> {
                rec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
            AudioContainer.AMR -> {
                rec.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
            AudioContainer.OPUS -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    rec.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    rec.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                } else {
                    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }
            }
            AudioContainer.WAV -> {
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            AudioContainer.AAC, AudioContainer.M4A, AudioContainer.MP3 -> {
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
        }
    }

    private fun applyMaxDuration(rec: MediaRecorder) {
        val minutes = SettingsStore.current().durationLimit.minutes
        if (minutes > 0) rec.setMaxDuration(minutes * 60 * 1000)
    }

    private fun projectionManager(): MediaProjectionManager {
        return getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun modeLabel(mode: RecordMode) = when (mode) {
        RecordMode.AUDIO -> "audio"
        RecordMode.VIDEO_AS_AUDIO -> "call audio"
        RecordMode.VIDEO_CALL -> "video call"
        RecordMode.SCREEN -> "screen"
        RecordMode.MEMO -> "memo"
    }

    private fun visibleStatus(source: String, voice: VoiceTrack): String {
        val who = when (voice) {
            VoiceTrack.BOTH -> "both sides (best effort)"
            VoiceTrack.LOCAL_ONLY -> "your mic only"
            VoiceTrack.REMOTE_ONLY -> "other side (best effort)"
        }
        return "$source · $who"
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.androidcallrecorder.app.START"
        const val ACTION_PAUSE = "com.androidcallrecorder.app.PAUSE"
        const val ACTION_RESUME = "com.androidcallrecorder.app.RESUME"
        const val ACTION_STOP = "com.androidcallrecorder.app.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(
            context: Context,
            mode: RecordMode,
            source: String,
            contact: String = "",
            number: String = "",
            direction: CallDirection = CallDirection.UNKNOWN,
            voice: VoiceTrack = SettingsStore.current().voiceTrack,
            resultCode: Int = Activity.RESULT_CANCELED,
            resultData: Intent? = null,
        ) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_CONTACT, contact)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_DIRECTION, direction.name)
                .putExtra(EXTRA_VOICE, voice.name)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
            if (resultData != null) i.putExtra(EXTRA_RESULT_DATA, resultData)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
