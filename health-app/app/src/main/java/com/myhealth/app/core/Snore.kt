package com.myhealth.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** 코골이 감지 순수 로직. Android 의존성 없음. 입력은 16kHz mono PCM16, 100ms(1600샘플) 단위. */

data class SnoreParams(
    val sampleRate: Int = 16_000,
    val hopMs: Int = 100,
    /** 1차 필터: 기준 소음보다 이만큼 커야 분석 */
    val gateDb: Double = 10.0,
    /** 2차 판별: 코골이 유사도(0~1) 기준. 라벨이 쌓이면 자동 조정된다 */
    val confThreshold: Double = 0.5,
    /** 들숨 한 번 길이 */
    val minBurstMs: Int = 300,
    val maxBurstMs: Int = 3000,
    /** 3차 확정: 이 간격으로 minBursts회 이상 반복 */
    val minPeriodMs: Int = 2000,
    val maxPeriodMs: Int = 6000,
    val minBursts: Int = 3,
    /** 긴 코골이는 이 길이로 잘라 구간을 나눈다 */
    val maxEventMs: Int = 60_000,
    val baselineInitMs: Int = 10_000,
)

data class SnoreBurst(val start: Long, val end: Long, val meanDb: Double, val peakDb: Double, val conf: Double)

/** 확정된 코골이 구간. meanDb·peakDb는 기준 소음 대비 dB */
data class DetectedSnore(
    val start: Long, val end: Long, val bursts: Int,
    val meanDb: Double, val peakDb: Double, val confidence: Double,
) {
    val intensity: Int get() = SnoreMath.intensity(meanDb)
}

object SnoreMath {
    /** 강도: 기준 대비 +10~20dB 조용(1), +20~30dB 보통(2), +30dB 이상 큼(3) */
    fun intensity(dbAboveBaseline: Double): Int = when {
        dbAboveBaseline >= 30 -> 3
        dbAboveBaseline >= 20 -> 2
        else -> 1
    }

    /** Snore Score = Σ(구간 길이(분) × 강도 가중치) ÷ 측정 시간(시간). parts = (구간 길이 초, 강도) */
    fun score(parts: List<Pair<Double, Int>>, measuredSec: Double): Double {
        if (measuredSec <= 0) return 0.0
        return parts.sumOf { (sec, w) -> sec / 60.0 * w } / (measuredSec / 3600.0)
    }

    /**
     * 사용자 라벨(코골이 유사도, 맞음 여부)로 판별 기준을 조정한다.
     * 라벨은 이미 감지된 구간에만 붙으므로 기준은 올리는 방향(오탐 줄이기)으로만 움직인다.
     */
    fun tuneThreshold(labels: List<Pair<Double, Boolean>>, base: Double = 0.5, minLabels: Int = 20): Double {
        if (labels.size < minLabels) return base
        var best = base
        var bestAcc = -1.0
        var t = base
        while (t <= 0.8 + 1e-9) {
            val acc = labels.count { (c, yes) -> (c >= t) == yes }.toDouble() / labels.size
            if (acc > bestAcc + 1e-9) { bestAcc = acc; best = t }
            t += 0.05
        }
        return Math.round(best * 100) / 100.0
    }

    fun rmsDb(x: ShortArray, n: Int = x.size): Double {
        var s = 0.0
        for (i in 0 until n) { val v = x[i] / 32768.0; s += v * v }
        return 10 * log10(maxOf(s / maxOf(1, n), 1e-9))
    }
}

/** 한 프레임의 주파수 특징으로 코골이 유사도를 낸다 (규칙 기반 2차 판별). */
class SpectralScorer(private val sampleRate: Int, private val n: Int = 1024) {
    private val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }

    private fun fft() {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val step = n / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cosT[k * step]; val wi = -sinT[k * step]
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi; val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
            }
            len = len shl 1
        }
    }

    /**
     * 코골이 유사도 0~1.
     * - 에너지가 80~1,000Hz 저주파 대역에 몰려 있을수록 높음
     * - spectral centroid가 낮을수록 높음 (말소리·TV는 중심이 더 위)
     */
    fun score(x: ShortArray): Double {
        val off = maxOf(0, (x.size - n) / 2)
        for (i in 0 until n) { re[i] = if (off + i < x.size) x[off + i] / 32768.0 * window[i] else 0.0; im[i] = 0.0 }
        fft()
        val binHz = sampleRate.toDouble() / n
        var low = 0.0; var total = 0.0; var weighted = 0.0
        for (k in 1 until n / 2) {
            val f = k * binHz
            if (f < 60) continue
            val p = re[k] * re[k] + im[k] * im[k]
            total += p; weighted += p * f
            if (f in 80.0..1000.0) low += p
        }
        if (total <= 0) return 0.0
        val lowRatio = low / total
        val centroid = weighted / total
        val s1 = ((lowRatio - 0.4) / 0.4).coerceIn(0.0, 1.0)
        val s2 = ((1500 - centroid) / 1000).coerceIn(0.0, 1.0)
        return 0.6 * s1 + 0.4 * s2
    }
}

/**
 * 처리 흐름: [1차] 기준 소음 + gateDb 미만은 버림 → [2차] 코골이 유사도 → [3차] 주기성 검사.
 * 기준 소음은 첫 10초로 정한 뒤 밤새 추적한다(조용해지면 빨리, 시끄러워지면 천천히 따라감).
 * 그래서 선풍기·에어컨처럼 계속 나는 소리는 몇 분 안에 기준 소음으로 흡수된다.
 */
class SnoreDetector(
    private val p: SnoreParams = SnoreParams(),
    private val onConfirmed: (firstBurstStart: Long) -> Unit = {},
    private val onEvent: (DetectedSnore) -> Unit = {},
) {
    private val scorer = SpectralScorer(p.sampleRate)
    private val init = ArrayList<Double>()
    var baselineDb: Double = Double.NaN; private set
    /** 측정 시작 직후 정한 방 소음 (dBFS) */
    var initialBaselineDb: Double = Double.NaN; private set
    /** 코골이 뒤 10초 이상 조용하다가 큰 소리가 이어진 횟수 (숨이 멈춘 듯한 패턴) */
    var apneaLike: Int = 0; private set
    var eventCount: Int = 0; private set

    // 진행 중인 소리 덩어리(run)
    private var runStart = -1L
    private var runLast = -1L
    private var runSum = 0.0; private var runPeak = 0.0; private var runConf = 0.0; private var runN = 0
    private val episode = ArrayList<SnoreBurst>()
    private var confirmed = false
    private var lastEventEnd = -1L
    private var gaspArmed = false

    private val initHops get() = p.baselineInitMs / p.hopMs

    fun feed(hop: ShortArray, tMs: Long) {
        val db = SnoreMath.rmsDb(hop)
        if (init.size < initHops) {
            init.add(db)
            if (init.size == initHops) {
                baselineDb = init.sorted()[init.size / 2]
                initialBaselineDb = baselineDb
            }
            return
        }
        val level = db - baselineDb
        // 기준 소음 추적: 아래로는 빠르게, 위로는 느리게
        baselineDb += (if (db < baselineDb) 0.05 else 0.002) * (db - baselineDb)

        if (level >= p.gateDb) {
            val conf = scorer.score(hop)
            if (runStart < 0) { runStart = tMs; runSum = 0.0; runPeak = 0.0; runConf = 0.0; runN = 0 }
            runLast = tMs
            runSum += level; runPeak = maxOf(runPeak, level); runConf += conf; runN++
        } else if (runStart >= 0 && tMs - runLast > p.hopMs) {
            closeRun()
        }
        // 마지막 들숨 뒤 주기가 끊기면 구간 종료
        if (episode.isNotEmpty() && runStart < 0 && tMs - episode.last().end > p.maxPeriodMs) closeEpisode()
    }

    private fun closeRun() {
        val end = runLast + p.hopMs
        val dur = end - runStart
        val burst = SnoreBurst(runStart, end, runSum / runN, runPeak, runConf / runN)
        runStart = -1

        // 숨이 멈춘 듯한 패턴: 코골이가 끝나고 10~60초 조용한 뒤 큰 소리
        if (gaspArmed && lastEventEnd > 0) {
            val quiet = burst.start - lastEventEnd
            if (quiet in 10_000..60_000 && burst.peakDb >= 20) apneaLike++
            gaspArmed = false
        }

        if (dur < p.minBurstMs || dur > p.maxBurstMs || burst.conf < p.confThreshold) return
        val prev = episode.lastOrNull()
        if (prev != null) {
            val gap = burst.start - prev.start
            if (gap < p.minPeriodMs) return                    // 너무 촘촘함: 무시
            if (gap > p.maxPeriodMs) closeEpisode()
            else if (burst.end - episode.first().start > p.maxEventMs) closeEpisode(keepConfirmed = true)
        }
        episode.add(burst)
        if (!confirmed && episode.size >= p.minBursts) {
            confirmed = true
            onConfirmed(episode.first().start)
        }
    }

    private fun closeEpisode(keepConfirmed: Boolean = false) {
        if (episode.size >= p.minBursts || (keepConfirmed && confirmed && episode.isNotEmpty())) {
            val e = DetectedSnore(
                episode.first().start, episode.last().end, episode.size,
                episode.sumOf { it.meanDb } / episode.size, episode.maxOf { it.peakDb },
                episode.sumOf { it.conf } / episode.size,
            )
            eventCount++
            lastEventEnd = e.end
            gaspArmed = !keepConfirmed
            onEvent(e)
        }
        episode.clear()
        confirmed = false
    }

    /** 측정 종료 시 진행 중이던 구간을 마무리 */
    fun finish() {
        if (runStart >= 0) closeRun()
        closeEpisode()
    }
}
