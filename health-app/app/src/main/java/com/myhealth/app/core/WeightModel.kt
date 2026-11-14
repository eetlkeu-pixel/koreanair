package com.myhealth.app.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** 원장 한 행 (계산용). day = epoch day */
data class WRow(val day: Long, val kg: Double, val device: String, val savedAt: Long)

data class WPoint(val day: Long, val kg: Double, val device: String)

data class Scenario(
    val key: String,
    /** kg/주. 계산 불가면 null */
    val rate: Double?,
    val goal1Day: Long?,
    val goal2Day: Long?,
    val after8w: Double?,
)

data class WeekRow(val monday: Long, val days: Int, val avg: Double, val min: Double, val last: WPoint)

object WeightModel {
    const val OVERALL = "overall"
    const val RECENT = "recent"
    const val SLOW = "slow"

    /** 같은 날 기기가 다르면 마지막으로 저장된 행이 그날의 대표값. */
    fun daily(rows: List<WRow>): List<WPoint> =
        rows.groupBy { it.day }
            .map { (_, v) -> v.maxBy { it.savedAt }.let { WPoint(it.day, it.kg, it.device) } }
            .sortedBy { it.day }

    /** 전체 평균 감량속도 kg/주 = (첫 실측 − 최신) ÷ 일수 × 7 */
    fun overallRate(p: List<WPoint>): Double? {
        if (p.size < 2) return null
        val span = p.last().day - p.first().day
        if (span <= 0) return null
        return (p.first().kg - p.last().kg) / span * 7
    }

    /** 최근 속도의 기준점: 최신일 35일 전과 가장 가까운 측정. 간격 14일 이상을 우선. */
    fun recentRef(p: List<WPoint>): WPoint? {
        if (p.size < 2) return null
        val latest = p.last()
        val cands = p.dropLast(1)
        val pool = cands.filter { latest.day - it.day >= 14 }.ifEmpty { cands }
        val target = latest.day - 35
        return pool.minWith(compareBy<WPoint> { abs(it.day - target) }.thenByDescending { it.day })
    }

    fun recentRate(p: List<WPoint>): Double? {
        val ref = recentRef(p) ?: return null
        val latest = p.last()
        return (ref.kg - latest.kg) / (latest.day - ref.day) * 7
    }

    /** 목표 도달일 = 최신 실측일 + (남은 kg ÷ 주당 속도 × 7일), 가까운 날짜로 반올림 */
    fun targetDay(latest: WPoint, target: Double, rate: Double?): Long? {
        if (rate == null || rate <= 0 || latest.kg <= target) return null
        return latest.day + ((latest.kg - target) / rate * 7).roundToLong()
    }

    fun scenarios(p: List<WPoint>, goal1: Double, goal2: Double): List<Scenario> {
        val latest = p.lastOrNull() ?: return emptyList()
        val recent = recentRate(p)
        fun mk(key: String, rate: Double?) = Scenario(
            key, rate,
            targetDay(latest, goal1, rate), targetDay(latest, goal2, rate),
            rate?.let { latest.kg - it * 8 },
        )
        return listOf(mk(OVERALL, overallRate(p)), mk(RECENT, recent), mk(SLOW, recent?.let { it * 0.7 }))
    }

    /** 월~일 주별 요약. 측정한 주만. */
    fun weekly(p: List<WPoint>): List<WeekRow> =
        p.groupBy { it.day - Math.floorMod(it.day + 3, 7L) }
            .map { (mon, v) -> WeekRow(mon, v.size, v.sumOf { it.kg } / v.size, v.minOf { it.kg }, v.maxBy { it.day }) }
            .sortedBy { it.monday }
}
