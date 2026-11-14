package com.myhealth.app.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** 하룻밤 기록. day = 기상일(epoch day), bedMin·wakeMin = 자정 기준 분(0..1439). */
data class Night(val day: Long, val bedMin: Int, val wakeMin: Int, val minutes: Int) {
    val midMin: Int get() = Math.floorMod(bedMin + (minutes + 1) / 2, 1440)

    /** 기상일이 토·일이면 휴일. (1970-01-01은 목요일) */
    val isHoliday: Boolean get() = Math.floorMod(day + 3, 7L) >= 5
}

data class SleepSummary(
    val count: Int,
    val avgMinutes: Double,
    val avgBed: Double,
    val avgWake: Double,
    val bedSd: Double,
    val avgMid: Double,
    val workMid: Double?,
    val holidayMid: Double?,
    /** 휴일 중간점 − 출근일 중간점 (분). 둘 중 하나라도 없으면 null */
    val holidayShift: Double?,
)

object SleepStats {
    /** -720..720 범위로 감싼 분 차이 */
    fun wrap(d: Double): Double = ((d + 720.0) % 1440.0 + 1440.0) % 1440.0 - 720.0

    /** 자정 넘김을 고려한 원형 평균 (23:30과 00:30의 평균 = 00:00). */
    fun circularMean(mins: List<Int>): Double? {
        if (mins.isEmpty()) return null
        val k = 2 * PI / 1440.0
        val s = mins.sumOf { sin(it * k) }
        val c = mins.sumOf { cos(it * k) }
        val ref = if (hypot(s, c) < 1e-9) mins[0].toDouble() else atan2(s, c) / k
        val mean = ref + mins.sumOf { wrap(it - ref) } / mins.size
        return (mean % 1440.0 + 1440.0) % 1440.0
    }

    /** 원형 평균 기준 표준편차(모집단). */
    fun circularSd(mins: List<Int>): Double? {
        val m = circularMean(mins) ?: return null
        return sqrt(mins.sumOf { val d = wrap(it - m); d * d } / mins.size)
    }

    fun summary(nights: List<Night>): SleepSummary? {
        if (nights.isEmpty()) return null
        val work = circularMean(nights.filter { !it.isHoliday }.map { it.midMin })
        val holi = circularMean(nights.filter { it.isHoliday }.map { it.midMin })
        return SleepSummary(
            count = nights.size,
            avgMinutes = nights.sumOf { it.minutes }.toDouble() / nights.size,
            avgBed = circularMean(nights.map { it.bedMin })!!,
            avgWake = circularMean(nights.map { it.wakeMin })!!,
            bedSd = circularSd(nights.map { it.bedMin })!!,
            avgMid = circularMean(nights.map { it.midMin })!!,
            workMid = work,
            holidayMid = holi,
            holidayShift = if (work != null && holi != null) wrap(holi - work) else null,
        )
    }

    /** 수면 부족 누적 = Σ(목표 − 실제). 양수면 부족. 기록 있는 날만 합산. */
    fun debt(nights: List<Night>, targetMin: Int): Int = nights.sumOf { targetMin - it.minutes }
}
