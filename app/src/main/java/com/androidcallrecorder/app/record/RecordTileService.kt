package com.androidcallrecorder.app.record

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.androidcallrecorder.app.TrampolineActivity

class RecordTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (RecordingSession.ui.value.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (RecordingSession.ui.value.active) "Stop rec" else "Record"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, TrampolineActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (RecordingSession.ui.value.active) {
            RecordingService.stop(this)
            onStartListening()
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 9, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
