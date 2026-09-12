package com.androidcallrecorder.app.record

import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.VoiceTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUi(
    val active: Boolean = false,
    val paused: Boolean = false,
    val mode: RecordMode = RecordMode.AUDIO,
    val source: String = "phone",
    val contact: String = "",
    val number: String = "",
    val direction: CallDirection = CallDirection.UNKNOWN,
    val voiceTrack: VoiceTrack = VoiceTrack.BOTH,
    val startedAt: Long = 0L,
    val elapsedMs: Long = 0L,
    val message: String = "",
    val liveTranscript: String = "",
)

object RecordingSession {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    fun update(transform: (SessionUi) -> SessionUi) {
        _ui.value = transform(_ui.value)
    }

    fun reset(message: String = "") {
        _ui.value = SessionUi(message = message)
    }
}
