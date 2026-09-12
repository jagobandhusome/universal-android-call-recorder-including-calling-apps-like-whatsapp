package com.androidcallrecorder.app.record

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.androidcallrecorder.app.R
import com.androidcallrecorder.app.TrampolineActivity

class RecordWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            val intent = Intent(context, TrampolineActivity::class.java)
                .putExtra(TrampolineActivity.EXTRA_MEMO, true)
            val pi = PendingIntent.getActivity(
                context,
                11,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_rec, pi)
            manager.updateAppWidget(id, views)
        }
    }
}
