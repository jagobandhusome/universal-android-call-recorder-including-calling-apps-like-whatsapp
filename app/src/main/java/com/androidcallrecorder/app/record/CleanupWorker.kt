package com.androidcallrecorder.app.record

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.androidcallrecorder.app.data.RecordingStore
import com.androidcallrecorder.app.data.SettingsStore
import java.util.concurrent.TimeUnit

class CleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        SettingsStore.init(applicationContext)
        RecordingStore.init(applicationContext)
        val days = SettingsStore.current().autoDelete.days
        if (days > 0) RecordingStore.deleteOlderThan(days)
        return Result.success()
    }

    companion object {
        private const val NAME = "smart-cleanup"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
