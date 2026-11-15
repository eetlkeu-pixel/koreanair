package com.myhealth.app

import com.myhealth.app.core.DetectedSnore
import com.myhealth.app.core.SnoreDetector
import com.myhealth.app.core.SnoreMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 합성 소리로 감지 흐름(1차 크기 필터 → 2차 주파수 판별 → 3차 주기성)을 확인 */
class SnoreDetectorTest {
    private val rnd = Random(7)
    private val HOP = 1600

    /** 100ms 한 프레임: 방 소음 + (선택) 여러 주파수의 사인파 */
    private fun hop(index: Int, amp: Double = 0.0, freqs: List<Double> = emptyList(), noise: Double = 30.0) = ShortArray(HOP) { i ->
        val t = (index.toLong() * HOP + i) / 16000.0
        var v = (rnd.nextDouble() * 2 - 1) * noise
        for (f in freqs) v += amp / freqs.size * sin(2 * PI * f * t)
        v.toInt().coerceIn(-32768, 32767).toShort()
    }

    private val SNORE = listOf(110.0, 220.0, 330.0, 440.0)
    private val SPEECH = listOf(1800.0, 2600.0, 3400.0)

    /** sound(초) = 그 시각에 낼 (진폭, 주파수들) 또는 null(조용) */
    private fun run(seconds: Int, sound: (Double) -> Pair<Double, List<Double>>?): Pair<List<DetectedSnore>, SnoreDetector> {
        val out = ArrayList<DetectedSnore>()
        val d = SnoreDetector(onEvent = { out.add(it) })
        for (i in 0 until seconds * 10) {
            val s = sound(i / 10.0)
            d.feed(if (s == null) hop(i) else hop(i, s.first, s.second), i * 100L)
        }
        d.finish()
        return out to d
    }

    @Test fun periodicSnoringIsDetected() {
        // 20초부터 1분간, 4초마다 1.2초씩 코골이
        val (ev, _) = run(120) { t -> if (t in 20.0..80.0 && (t - 20) % 4 < 1.2) 3000.0 to SNORE else null }
        assertEquals(1, ev.size)
        val e = ev[0]
        assertEquals(20_000.0, e.start.toDouble(), 300.0)
        assertEquals(77_200.0, e.end.toDouble(), 400.0)
        assertTrue(e.bursts >= 14)
        assertEquals(3, e.intensity)
        assertTrue(e.confidence > 0.8)
    }

    @Test fun quietSnoringGetsLowerIntensity() {
        val (ev, _) = run(90) { t -> if (t in 20.0..60.0 && (t - 20) % 4 < 1.2) 250.0 to SNORE else null }
        assertEquals(1, ev.size)
        assertEquals(1, ev[0].intensity)
    }

    @Test fun singleCoughIsIgnored() {
        val (ev, _) = run(60) { t -> if (t in 30.0..30.8) 4000.0 to SNORE else null }
        assertTrue(ev.isEmpty())
    }

    @Test fun twoBurstsAreNotEnough() {
        val (ev, _) = run(60) { t -> if (t in 30.0..31.0 || t in 34.0..35.0) 3000.0 to SNORE else null }
        assertTrue(ev.isEmpty())
    }

    @Test fun speechLikeSoundIsIgnored() {
        // 같은 주기·크기지만 높은 주파수(말소리·TV 대역)
        val (ev, _) = run(120) { t -> if (t in 20.0..80.0 && (t - 20) % 4 < 1.2) 3000.0 to SPEECH else null }
        assertTrue(ev.isEmpty())
    }

    @Test fun steadyFanIsAbsorbedIntoBaseline() {
        // 20초부터 계속 나는 저주파 소음(선풍기)
        val (ev, d) = run(300) { t -> if (t >= 20.0) 600.0 to listOf(120.0, 240.0) else null }
        assertTrue(ev.isEmpty())
        assertTrue("기준 소음이 선풍기 소리까지 올라가야 함", d.baselineDb > d.initialBaselineDb + 15)
    }

    @Test fun snoringOverFanIsStillDetected() {
        val (ev, _) = run(300) { t ->
            if (t in 150.0..210.0 && (t - 150) % 4 < 1.2) 6000.0 to SNORE
            else if (t >= 20.0) 300.0 to listOf(120.0, 240.0) else null
        }
        assertEquals(1, ev.size)
        assertEquals(150_000.0, ev[0].start.toDouble(), 300.0)
    }

    @Test fun irregularLongNoiseIsIgnored() {
        // 5초짜리 긴 소리 3번 (들숨 길이 3초 초과)
        val (ev, _) = run(90) { t -> if (t in 20.0..50.0 && (t - 20) % 10 < 5) 3000.0 to SNORE else null }
        assertTrue(ev.isEmpty())
    }

    @Test fun longSnoringIsSplitIntoSegments() {
        val (ev, _) = run(260) { t -> if (t in 20.0..230.0 && (t - 20) % 4 < 1.2) 3000.0 to SNORE else null }
        assertTrue(ev.size in 3..4)
        assertTrue(ev.all { it.end - it.start <= 62_000 })
        assertEquals(207.0, ev.sumOf { it.end - it.start } / 1000.0, 15.0)
    }

    @Test fun pauseThenGaspIsCounted() {
        // 코골이 → 20초 조용 → 큰 숨소리, 를 세 번 반복
        val (_, d) = run(200) { t ->
            val c = t % 60
            if (t < 20 || t > 190) null
            else if (c in 20.0..36.0 && (c - 20) % 4 < 1.2) 2500.0 to SNORE
            else if (c in 57.0..58.0) 5000.0 to SNORE else null
        }
        assertTrue(d.apneaLike >= 2)
    }

    @Test fun scoreMatchesPlanExample() {
        // 7시간 측정, 조용 30분 + 보통 15분 + 큼 3분 → 69 ÷ 7 ≈ 9.9
        val s = SnoreMath.score(listOf(1800.0 to 1, 900.0 to 2, 180.0 to 3), 7 * 3600.0)
        assertEquals(9.86, s, 0.01)
        assertEquals(0.0, SnoreMath.score(emptyList(), 3600.0), 0.0)
    }

    @Test fun intensityBands() {
        assertEquals(listOf(1, 1, 2, 2, 3), listOf(10.0, 19.9, 20.0, 29.9, 30.0).map { SnoreMath.intensity(it) })
    }

    @Test fun thresholdTuning() {
        // 라벨이 적으면 그대로
        assertEquals(0.5, SnoreMath.tuneThreshold(List(10) { 0.55 to false }), 1e-9)
        // 유사도 0.6 미만은 대부분 오탐, 이상은 대부분 진짜 → 기준이 0.6으로 올라감
        val labels = List(15) { 0.55 to false } + List(15) { 0.7 to true } + listOf(0.52 to true)
        assertEquals(0.6, SnoreMath.tuneThreshold(labels), 1e-9)
        // 전부 진짜면 그대로
        assertEquals(0.5, SnoreMath.tuneThreshold(List(30) { 0.6 to true }), 1e-9)
    }
}
