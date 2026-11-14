package com.myhealth.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.myhealth.app.R
import com.myhealth.app.collect.Collector
import com.myhealth.app.core.WRow
import com.myhealth.app.core.WeightModel
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.Prefs
import com.myhealth.app.ui.MainActivity
import com.myhealth.app.ui.clock
import com.myhealth.app.ui.dur
import com.myhealth.app.ui.kg
import com.myhealth.app.ui.today
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** 홈 화면 위젯: 어젯밤 수면시간, 7일 평균, 최신 체중과 목표까지 남은 무게 */
class HealthWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread {
            try {
                runBlocking {
                    // 앱을 열지 않아도 위젯이 갱신될 때 수면을 다시 수집한다
                    runCatching { Collector.collect(ctx) }
                    runCatching { render(ctx) }
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HealthWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        suspend fun build(ctx: Context): RemoteViews {
            val db = AppDb.get(ctx)
            val st = Prefs.get(ctx).flow.value
            val today = today()
            val week = db.sleep().list().filter { it.valid && it.day in today - 6..today }
            val last = week.firstOrNull { it.day == today }
            val avg = if (week.isEmpty()) null else week.sumOf { it.minutes }.toDouble() / week.size
            val w = WeightModel.daily(db.weight().list().map { WRow(it.day, it.kg, it.device, it.savedAt) }).lastOrNull()

            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_sleep, last?.let { dur(it.minutes) } ?: "기록 없음")
            v.setTextViewText(R.id.w_times, listOfNotNull(
                last?.let { "${clock(it.bed!!)}–${clock(it.wake!!)}" },
                avg?.let { "7일 평균 ${dur(it.roundToInt())}" },
            ).joinToString(" · ").ifEmpty { "앱을 열어 권한을 확인하세요" })
            v.setTextViewText(R.id.w_weight, when {
                w == null -> "체중 기록 없음"
                w.kg > st.goal1 -> "체중 ${kg(w.kg)}kg · ${kg(st.goal1)}까지 ${kg(w.kg - st.goal1)}kg"
                w.kg > st.goal2 -> "체중 ${kg(w.kg)}kg · ${kg(st.goal2)}까지 ${kg(w.kg - st.goal2)}kg"
                else -> "체중 ${kg(w.kg)}kg · 목표 달성"
            })
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0,
                Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }
    }
}
