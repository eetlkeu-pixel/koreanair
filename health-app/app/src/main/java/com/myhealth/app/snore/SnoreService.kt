package com.myhealth.app.snore

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.myhealth.app.core.DetectedSnore
import com.myhealth.app.core.SnoreDetector
import com.myhealth.app.core.SnoreMath
import com.myhealth.app.core.SnoreParams
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.SnoreEvent
import com.myhealth.app.data.SnoreSession
import com.myhealth.app.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * 밤새 마이크로 듣고 코골이 구간만 기록하는 foreground service.
 * 전체 녹음은 저장하지 않는다. 최근 12초만 메모리에 두었다가 코골이가 확정되면 10초 샘플만 파일로 남긴다.
 */
class SnoreService : Service() {
    companion object {
        const val ACTION_STOP = "com.myhealth.app.snore.STOP"
        const val EXTRA_DELAY_MIN = "delayMin"
        const val MAX_CLIPS = 20
        const val CLIP_KEEP_DAYS = 30L
        /** 잠금 해제로 자동 종료되기 위한 최소 측정 시간 */
        const val AUTO_STOP_AFTER_MS = 3 * 3_600_000L
        private const val MAX_RUN_MS = 12 * 3_600_000L
        private const val RATE = 16_000
        private const val HOP = 1600
        private const val CHANNEL = "snore"
        private const val NOTIF_ID = 41

        /** 측정 중인 세션 id (없으면 null) */
        val running = MutableStateFlow<Long?>(null)

        fun start(ctx: Context, delayMin: Int) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SnoreService::class.java).putExtra(EXTRA_DELAY_MIN, delayMin))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, SnoreService::class.java).setAction(ACTION_STOP))
        }

        fun isCharging(ctx: Context): Boolean {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
            return i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }

        fun clipDir(ctx: Context) = File(ctx.filesDir, "snore-clips").apply { mkdirs() }

        /** 30일 지난 샘플 파일 삭제 (수치 기록은 유지) */
        suspend fun cleanOldClips(ctx: Context) {
            val cutoff = System.currentTimeMillis() - CLIP_KEEP_DAYS * 86_400_000L
            val dao = AppDb.get(ctx).snore()
            dao.eventList().filter { it.clipPath != null && it.startAt < cutoff }.forEach {
                File(it.clipPath!!).delete(); dao.dropClip(it.id)
            }
        }

        /** 앱이 강제 종료되어 '측정 중'으로 남은 세션을 '중단됨'으로 정리 */
        suspend fun closeOrphans(ctx: Context) {
            val dao = AppDb.get(ctx).snore()
            dao.sessionList().filter { it.running && it.id != running.value }
                .forEach { dao.putSession(it.copy(running = false, interrupted = true)) }
        }
    }

    @Volatile private var stopRequested = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // 아침에 폰을 집어 잠금을 풀면 자동 종료. 밤중에 잠깐 확인한 것은 무시한다
            if (System.currentTimeMillis() - startedAt >= AUTO_STOP_AFTER_MS) stopRequested = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            if (worker == null) stopSelf()
            return START_NOT_STICKY
        }
        if (worker != null) return START_NOT_STICKY
        val delayMin = intent?.getIntExtra(EXTRA_DELAY_MIN, 0) ?: 0
        startedAt = System.currentTimeMillis()
        startInForeground("코골이 측정 중", "화면을 꺼도 계속 측정합니다")
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myhealth:snore").apply { acquire(MAX_RUN_MS + 60_000) }
        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
        worker = thread(name = "snore") {
            try { measure(delayMin) } finally {
                running.value = null
                runCatching { unregisterReceiver(unlockReceiver) }
                runCatching { wakeLock?.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "코골이 측정", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).putExtra("tab", 2), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, SnoreService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "측정 종료", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    @SuppressLint("MissingPermission")
    private fun measure(delayMin: Int) = runBlocking {
        val dao = AppDb.get(this@SnoreService).snore()
        val zone = ZoneId.systemDefault()
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        val measureFrom = startedAt + delayMin * 60_000L
        var session = SnoreSession(0, dayOf(startedAt), startedAt, measureFrom, startedAt,
            charging = isCharging(this@SnoreService), running = true)
        session = session.copy(id = dao.putSession(session))
        running.value = session.id

        // 잠들기 전 소리를 빼기 위한 지연
        while (!stopRequested && System.currentTimeMillis() < measureFrom) Thread.sleep(500)

        // 라벨이 쌓였으면 판별 기준을 조정
        val labels = dao.eventList().filter { it.label != null }.map { it.confidence to (it.label == 1) }
        val params = SnoreParams(confThreshold = SnoreMath.tuneThreshold(labels))

        // 최근 12초 링버퍼 (샘플 클립용)
        val ring = ShortArray(RATE * 12)
        var ringPos = 0L
        var clipDueAt = -1L
        var pendingClip: ShortArray? = null
        val saved = ArrayList<SnoreEvent>()   // 클립이 있는 구간들

        fun grabClip(): ShortArray {
            val n = minOf(ringPos, RATE * 10L).toInt()
            val out = ShortArray(n / 2)   // 8kHz로 줄여 저장
            val from = ringPos - n
            for (i in out.indices) {
                val a = ring[((from + i * 2L) % ring.size).toInt()]; val b = ring[((from + i * 2L + 1) % ring.size).toInt()]
                out[i] = ((a + b) / 2).toShort()
            }
            return out
        }

        val detector = SnoreDetector(params,
            onConfirmed = { first -> clipDueAt = first + 10_000 },
            onEvent = { e: DetectedSnore ->
                var ev = SnoreEvent(0, session.id, e.start, e.end, e.intensity, e.meanDb, e.peakDb, e.confidence)
                val pcm = pendingClip ?: grabClip()
                pendingClip = null; clipDueAt = -1
                // 강한 순으로 최대 20개만 샘플을 남긴다
                val weakest = saved.minByOrNull { it.meanDb }
                if (saved.size < MAX_CLIPS || (weakest != null && e.meanDb > weakest.meanDb)) {
                    val f = File(clipDir(this@SnoreService), "s${session.id}_${e.start}.wav")
                    runCatching { Wav.write(f, pcm, RATE / 2); ev = ev.copy(clipPath = f.path) }
                    if (saved.size >= MAX_CLIPS && weakest != null) {
                        File(weakest.clipPath!!).delete(); runBlocking { dao.dropClip(weakest.id) }; saved.remove(weakest)
                    }
                }
                ev = ev.copy(id = runBlocking { dao.putEvent(ev) })
                if (ev.clipPath != null) saved.add(ev)
            })

        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        // 가능하면 자동 음량 조절이 없는 원음으로 받는다 (크기 비교가 정확해짐)
        val source = if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val rec = runCatching {
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE * 2))
                .also { check(it.state == AudioRecord.STATE_INITIALIZED); it.startRecording() }
        }.getOrNull()

        var lastSave = System.currentTimeMillis()
        var failed = rec == null
        if (rec != null) {
            val hop = ShortArray(HOP)
            var fed = 0L
            val t0 = System.currentTimeMillis()
            while (!stopRequested) {
                var got = 0
                while (got < HOP) {
                    val r = rec.read(hop, got, HOP - got)
                    if (r <= 0) { failed = true; break }
                    got += r
                }
                if (failed) break
                // 시각은 받은 샘플 수로 계산해 처리 지연의 영향을 받지 않게 한다
                val t = t0 + fed * 1000 / RATE
                fed += HOP
                for (s in hop) { ring[(ringPos % ring.size).toInt()] = s; ringPos++ }
                detector.feed(hop, t)
                if (clipDueAt in 1..t) { pendingClip = grabClip(); clipDueAt = -1 }
                val now = System.currentTimeMillis()
                if (now - lastSave >= 60_000) {
                    // 강제 종료에 대비해 1분마다 진행 상황 저장
                    lastSave = now
                    session = session.copy(endAt = now, baselineDb = detector.initialBaselineDb.takeIf { !it.isNaN() }, apneaLike = detector.apneaLike)
                    dao.putSession(session.copy(tags = dao.session(session.id)?.tags ?: ""))
                }
                if (now - startedAt > MAX_RUN_MS) break
            }
            detector.finish()
            runCatching { rec.stop() }
            rec.release()
        }
        val end = System.currentTimeMillis()
        val current = dao.session(session.id)
        dao.putSession(session.copy(
            day = dayOf(end), endAt = end, running = false, interrupted = failed,
            baselineDb = detector.initialBaselineDb.takeIf { !it.isNaN() }, apneaLike = detector.apneaLike,
            tags = current?.tags ?: "",
        ))
        runCatching { cleanOldClips(this@SnoreService) }
    }
}

/** 16bit mono PCM WAV */
object Wav {
    fun write(f: File, pcm: ShortArray, rate: Int) {
        val data = pcm.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (s in pcm) b.putShort(s)
        f.writeBytes(b.array())
    }
}
