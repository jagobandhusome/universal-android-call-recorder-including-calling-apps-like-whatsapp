package com.androidcallrecorder.app.record

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.androidcallrecorder.app.TrampolineActivity
import com.androidcallrecorder.app.data.SettingsStore
import kotlin.math.sqrt

class ShakeMonitor(private val context: Context) : SensorEventListener {
    private val mgr = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var last = 0L

    fun start() {
        mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            mgr.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        mgr.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!SettingsStore.current().shakeToRecord) return
        val e = event ?: return
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (g < 18f) return
        val now = System.currentTimeMillis()
        if (now - last < 2500) return
        last = now
        if (RecordingSession.ui.value.active) {
            RecordingService.stop(context)
        } else {
            val i = Intent(context, TrampolineActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(TrampolineActivity.EXTRA_MEMO, true)
            context.startActivity(i)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
