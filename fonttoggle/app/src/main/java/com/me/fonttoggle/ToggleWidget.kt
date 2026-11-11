package com.me.fonttoggle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 홈 화면 1x1 위젯. 누르면 토글. */
class ToggleWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        update(ctx, mgr, ids)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (FontScaler.canWrite(ctx)) {
                FontScaler.toggle(ctx)
            } else {
                android.widget.Toast.makeText(
                    ctx, "글자토글 앱을 열어 권한을 먼저 허용해 주세요", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.me.fonttoggle.TOGGLE"

        fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val large = FontScaler.isLarge(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            views.setTextViewText(R.id.widget_text, "가")
            views.setTextViewTextSize(
                R.id.widget_text, android.util.TypedValue.COMPLEX_UNIT_DIP, if (large) 30f else 18f
            )
            views.setTextViewText(R.id.widget_sub, if (large) "크게" else "작게")
            val intent = Intent(ctx, ToggleWidget::class.java).setAction(ACTION_TOGGLE)
            val pi = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(ids, views)
        }
    }
}
