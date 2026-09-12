package com.androidcallrecorder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.androidcallrecorder.app.data.RecordMode
import com.androidcallrecorder.app.data.SettingsStore
import com.androidcallrecorder.app.record.RecordingService
import com.androidcallrecorder.app.record.RecordingSession

class TrampolineActivity : ComponentActivity() {
    private var appId = "app"
    private var memo = false
    private var wantVoip = false

    private val projection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val mode = if (SettingsStore.current().videoCallAsAudio) {
                RecordMode.VIDEO_AS_AUDIO
            } else {
                RecordMode.VIDEO_CALL
            }
            RecordingService.start(
                this,
                mode,
                appId,
                resultCode = result.resultCode,
                resultData = result.data,
            )
        } else {
            Toast.makeText(this, "Screen audio denied — using microphone / speaker", Toast.LENGTH_LONG).show()
            RecordingService.start(this, RecordMode.AUDIO, appId)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        memo = intent.getBooleanExtra(EXTRA_MEMO, false)
        wantVoip = intent.getBooleanExtra(MainActivity.EXTRA_START_VOIP, false)
        appId = intent.getStringExtra(MainActivity.EXTRA_VOIP_APP) ?: "app"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (RecordingSession.ui.value.active) {
            RecordingService.stop(this)
            finish()
            return
        }

        when {
            memo -> {
                RecordingService.start(this, RecordMode.MEMO, "memo")
                finish()
            }
            wantVoip -> {
                com.androidcallrecorder.app.record.CallPrompt.show(this, source = appId)
                finish()
            }
            else -> {
                RecordingService.start(this, RecordMode.AUDIO, "phone")
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_MEMO = "memo"
    }
}
