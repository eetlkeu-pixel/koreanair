package com.myhealth.app

import com.myhealth.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** 기획안 PART 6 계산 예시(가상 7일, 목표 7시간)와 일치하는지 확인 */
class SleepStatsTest {
    private fun n(date: String, bed: String, wake: String): Night {
        fun m(s: String) = s.substringBefore(':').toInt() * 60 + s.substringAfter(':').toInt()
        val dur = Math.floorMod(m(wake) - m(bed), 1440)
        return Night(LocalDate.parse(date).toEpochDay(), m(bed), m(wake), dur)
    }

    // 2026-11-09(월) ~ 11-15(일)
    private val week = listOf(
        n("2026-11-09", "00:40", "07:10"), n("2026-11-10", "01:10", "07:05"),
        n("2026-11-11", "00:20", "07:15"), n("2026-11-12", "01:30", "07:00"),
        n("2026-11-13", "01:20", "07:05"), n("2026-11-14", "02:30", "10:30"),
        n("2026-11-15", "01:00", "09:00"),
    )

    @Test fun planExample() {
        val s = SleepStats.summary(week)!!
        assertEquals(2795, week.sumOf { it.minutes })
        assertEquals(399.3, s.avgMinutes, 0.1)          // 6시간 39분
        assertEquals(73.0, s.avgBed, 0.5)               // 01:13
        assertEquals(472.0, s.avgWake, 0.5)             // 07:52
        assertEquals(38.0, s.bedSd, 0.6)                // 38분
        assertEquals(345.0, s.holidayMid!!, 0.5)        // 05:45
        assertEquals(244.0, s.workMid!!, 0.5)           // 04:04
        assertEquals(101.5, s.holidayShift!!, 1.0)      // 약 1시간 42분
        assertEquals(145, SleepStats.debt(week, 420))   // 2시간 25분
    }

    @Test fun midpoints() {
        assertEquals(listOf(235, 248, 228, 255, 253, 390, 300), week.map { it.midMin })
    }

    @Test fun weekendDetection() {
        assertEquals(listOf(false, false, false, false, false, true, true), week.map { it.isHoliday })
    }

    @Test fun circularMeanAcrossMidnight() {
        assertEquals(0.0, SleepStats.wrap(SleepStats.circularMean(listOf(23 * 60 + 30, 30))!!), 0.01)
        assertEquals(1425.0, SleepStats.circularMean(listOf(23 * 60 + 30, 0))!!, 0.01)
        assertEquals(30.0, SleepStats.circularSd(listOf(23 * 60 + 30, 30))!!, 0.01)
    }

    @Test fun emptyIsNull() {
        assertNull(SleepStats.summary(emptyList()))
    }
}
