package com.myhealth.app.collect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myhealth.app.core.EvType
import com.myhealth.app.core.SleepDetector
import com.myhealth.app.core.UsageEvent
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.Prefs
import com.myhealth.app.data.SRC_AUTO
import com.myhealth.app.data.Settings
import com.myhealth.app.data.SleepRec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** OS에 쌓인 잠금 해제·화면 꺼짐 기록을 읽어 지난 7일의 수면을 다시 계산해 저장한다. */
object Collector {
    private const val LOOKBACK_MS = 6 * 3_600_000L
    private val lock = Mutex()

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 기상일 day의 탐색 구간 [시작, 끝] (epoch millis) */
    fun window(day: Long, s: Settings, zone: ZoneId): Pair<Long, Long> {
        val d = LocalDate.ofEpochDay(day)
        val end = d.atTime(LocalTime.ofSecondOfDay(s.winEndMin * 60L)).atZone(zone).toInstant().toEpochMilli()
        val startDate = if (s.winStartMin >= s.winEndMin) d.minusDays(1) else d
        val start = startDate.atTime(LocalTime.ofSecondOfDay(s.winStartMin * 60L)).atZone(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun readEvents(ctx: Context, from: Long, to: Long): List<UsageEvent> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.KEYGUARD_HIDDEN -> out.add(UsageEvent(e.timeStamp, EvType.UNLOCK))
                // 화면 꺼짐, 기기 종료(26)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, 26 -> out.add(UsageEvent(e.timeStamp, EvType.SCREEN_OFF))
            }
        }
        return out
    }

    /** @return 새로 저장·갱신한 날 수. 권한이 없으면 -1 */
    suspend fun collect(ctx: Context, days: Int = 7): Int = withContext(Dispatchers.IO) {
        if (!hasPermission(ctx)) return@withContext -1
        lock.withLock {
            val s = Prefs.get(ctx).flow.value
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val today = LocalDate.now(zone).toEpochDay()
            val first = today - (days - 1)
            val all = readEvents(ctx, window(first, s, zone).first - LOOKBACK_MS, now)
            val dao = AppDb.get(ctx).sleep()
            var changed = 0
            for (day in first..today) {
                val (ws, weFull) = window(day, s, zone)
                if (ws >= now) continue
                val we = minOf(weFull, now)
                val ev = all.filter { it.time >= ws - LOOKBACK_MS && it.time <= we }
                // 감지 실패(OS 보존 기간 경과 포함)면 기존 기록을 건드리지 않는다
                val span = SleepDetector.detect(ev, ws, we, s.detect) ?: continue
                val old = dao.get(day)
                val rec = when {
                    old == null -> SleepRec(day, span.bedtime, span.wake, SRC_AUTO, span.bedtime, span.wake)
                    // 수동 수정·삭제한 날은 덮어쓰지 않고 자동 원본만 갱신
                    old.source != SRC_AUTO || old.deleted -> old.copy(autoBed = span.bedtime, autoWake = span.wake)
                    else -> old.copy(bed = span.bedtime, wake = span.wake, autoBed = span.bedtime, autoWake = span.wake)
                }
                if (rec != old) { dao.put(rec); changed++ }
            }
            changed
        }
    }

    /** 매일 오전 11시쯤 하루 한 번 자동 수집 */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(11, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<CollectWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily-collect", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class CollectWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Collector.collect(applicationContext)
        runCatching { com.myhealth.app.widget.HealthWidget.render(applicationContext) }
        return Result.success()
    }
}
