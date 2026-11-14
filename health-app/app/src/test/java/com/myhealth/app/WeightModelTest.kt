package com.myhealth.app

import com.myhealth.app.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class WeightModelTest {
    private fun d(s: String) = LocalDate.parse(s).toEpochDay()
    private fun iso(day: Long?) = day?.let { LocalDate.ofEpochDay(it).toString() }

    @Test fun syntheticRatesAndDates() {
        val rows = listOf(
            WRow(d("2026-01-01"), 90.0, "INBODY", 1), WRow(d("2026-01-15"), 88.0, "HOME", 2),
            WRow(d("2026-02-05"), 86.5, "HOME", 3), WRow(d("2026-02-19"), 86.0, "HOME", 4),
        )
        val p = WeightModel.daily(rows)
        assertEquals(4.0 / 49 * 7, WeightModel.overallRate(p)!!, 1e-9)
        // 35일 전 = 01-15 → 정확히 일치
        assertEquals(d("2026-01-15"), WeightModel.recentRef(p)!!.day)
        assertEquals(2.0 / 35 * 7, WeightModel.recentRate(p)!!, 1e-9)
        val sc = WeightModel.scenarios(p, 84.0, 80.0)
        // 2kg ÷ (4/49 kg/일) = 24.5일 → 반올림 25일
        assertEquals("2026-03-16", iso(sc[0].goal1Day))
        assertEquals(86.0 - 2.0 / 35 * 7 * 0.7 * 8, sc[2].after8w!!, 1e-9)
    }

    @Test fun sameDayUsesLastSavedRow() {
        val p = WeightModel.daily(listOf(
            WRow(10, 90.0, "INBODY", 5), WRow(10, 89.5, "HOME", 9), WRow(11, 89.0, "HOME", 1)))
        assertEquals(listOf(89.5, 89.0), p.map { it.kg })
    }

    @Test fun recentRefPrefersGapOf14Days() {
        val p = listOf(WPoint(0, 90.0, ""), WPoint(95, 88.0, ""), WPoint(100, 87.5, ""))
        assertEquals(0L, WeightModel.recentRef(p)!!.day)
    }

    @Test fun gainingOrReachedGivesNoDate() {
        val p = listOf(WPoint(0, 90.0, ""), WPoint(30, 91.0, ""))
        val sc = WeightModel.scenarios(p, 89.0, 82.0)
        assertNull(sc[0].goal1Day)
        assertNull(WeightModel.targetDay(WPoint(0, 88.0, ""), 89.0, 0.5))
        assertTrue(WeightModel.scenarios(listOf(WPoint(0, 90.0, "")), 89.0, 82.0).all { it.rate == null })
    }

    /** 개인 시드 데이터(seed.json, 저장소 미포함)가 있으면 기존 대시보드 수치와 대조 */
    @Test fun matchesExistingDashboard() {
        val f = File("src/main/assets/seed.json")
        assumeTrue(f.exists())
        val arr = JSONObject(f.readText()).getJSONArray("weight")
        val rows = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            WRow(d(o.getString("date")), o.getDouble("kg"), o.getString("device"), it.toLong())
        }
        val p = WeightModel.daily(rows)
        assertEquals(11, p.size)
        val sc = WeightModel.scenarios(p, 89.0, 82.0)
        assertEquals(0.798, sc[0].rate!!, 0.0005)
        assertEquals("2027-01-30", iso(sc[0].goal1Day)); assertEquals("2027-04-01", iso(sc[0].goal2Day))
        assertEquals(91.6, sc[0].after8w!!, 0.05)
        assertEquals(0.260, sc[1].rate!!, 0.0005)
        assertEquals("2027-07-12", iso(sc[1].goal1Day)); assertEquals("2028-01-17", iso(sc[1].goal2Day))
        assertEquals(95.9, sc[1].after8w!!, 0.05)
        assertEquals(0.182, sc[2].rate!!, 0.0005)
        assertEquals("2027-10-24", iso(sc[2].goal1Day)); assertEquals("2028-07-19", iso(sc[2].goal2Day))
        assertEquals(96.5, sc[2].after8w!!, 0.05)
        val w = WeightModel.weekly(p)
        assertEquals(10, w.size)
        val oct5 = w.first { it.monday == d("2026-10-05") }
        assertEquals(2, oct5.days); assertEquals(99.8, oct5.avg, 0.001); assertEquals(99.3, oct5.min, 0.001)
    }
}
