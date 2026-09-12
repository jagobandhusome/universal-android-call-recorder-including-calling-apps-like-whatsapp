package com.androidcallrecorder.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidcallrecorder.app.data.CallDirection
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.data.ThemeMode
import com.androidcallrecorder.app.record.RecordingService
import com.androidcallrecorder.app.ui.i18n.L
import com.androidcallrecorder.app.ui.theme.AppScreenBackground
import com.androidcallrecorder.app.ui.theme.CallRecorderTheme
import com.androidcallrecorder.app.ui.theme.accentColor

class CallPromptActivity : FragmentActivity() {
    private var source = "phone"
    private var contact = ""
    private var number = ""
    private var direction = CallDirection.UNKNOWN

    private val projection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            RecordingService.start(
                this,
                RecordMode.VIDEO_CALL,
                source,
                contact = contact,
                number = number,
                direction = direction,
                resultCode = result.resultCode,
                resultData = result.data,
            )
        } else {
            Toast.makeText(this, L.t("Using microphone for audio", "মাইক দিয়ে অডিও"), Toast.LENGTH_LONG).show()
            startAudio()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        SettingsStore.init(this)
        readExtras(intent)
        val auto = intent.getStringExtra(EXTRA_AUTO)
        if (auto == "audio") {
            startAudio(); finish(); return
        }
        if (auto == "video") {
            startVideo(); return
        }
        setContent {
            val settings = SettingsStore.settings.collectAsStateWithLifecycle()
            val dark = when (settings.value.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> null
            }
            CallRecorderTheme(darkTheme = dark, accent = accentColor(settings.value.accent)) {
                AppScreenBackground {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val appLabel = if (source == "phone") L.t("Phone call", "ফোন কল")
                        else com.androidcallrecorder.app.data.SupportedApps.labelFor(source)
                        val dir = when (direction) {
                            CallDirection.INCOMING -> L.t("Incoming", "ইনকামিং")
                            CallDirection.OUTGOING -> L.t("Outgoing", "আউটগোয়িং")
                            else -> ""
                        }
                        Text(
                            L.t("Record this call?", "এই কল রেকর্ড করবেন?"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            listOf(dir, appLabel, contact.ifBlank { number }).filter { it.isNotBlank() }.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            L.t(
                                "Audio records sound only (mic / speaker). Video records the screen — use that for video calls.",
                                "অডিও শুধু শব্দ রাখে। ভিডিও স্ক্রিন রাখে — ভিডিও কলের জন্য।",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { startAudio(); finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(L.t("Record audio", "অডিও রেকর্ড"))
                        }
                        OutlinedButton(onClick = { startVideo() }, modifier = Modifier.fillMaxWidth()) {
                            Text(L.t("Record video", "ভিডিও রেকর্ড"))
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(L.t("Skip", "বাদ দিন"))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent) {
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "phone"
        contact = intent.getStringExtra(EXTRA_CONTACT).orEmpty()
        number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        direction = runCatching {
            CallDirection.valueOf(intent.getStringExtra(EXTRA_DIRECTION) ?: CallDirection.UNKNOWN.name)
        }.getOrDefault(CallDirection.UNKNOWN)
    }

    private fun startAudio() {
        RecordingService.start(
            this,
            RecordMode.AUDIO,
            source,
            contact = contact,
            number = number,
            direction = direction,
        )
    }

    private fun startVideo() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(mgr.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_PREFER_VIDEO = "prefer_video"
        const val EXTRA_AUTO = "auto_mode"
    }
}
