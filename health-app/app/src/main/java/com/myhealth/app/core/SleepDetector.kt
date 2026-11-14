package com.myhealth.app.core

/** 순수 Kotlin 수면 감지 로직. Android 의존성 없음. 시각은 모두 epoch millis. */

enum class EvType { UNLOCK, SCREEN_OFF }

data class UsageEvent(val time: Long, val type: EvType)

data class DetectParams(
    val minSleepMin: Int = 180,
    val shortUseMin: Int = 2,
    val mergeGapMin: Int = 30,
    val latencyMin: Int = 0,
)

data class Interval(val start: Long, val end: Long) {
    val length: Long get() = end - start
}

data class SleepSpan(val bedtime: Long, val wake: Long) {
    val minutes: Int get() = ((wake - bedtime) / 60_000L).toInt()
}

object SleepDetector {
    private const val MIN = 60_000L

    /**
     * 사용 구간 U_i = [잠금 해제, 다음 화면 꺼짐]. 탐색 구간으로 잘라서 돌려준다.
     * 탐색 구간 이전 이벤트는 구간 시작 시점의 상태(사용 중/내려놓음)를 알기 위해서만 쓴다.
     * 구간 시작 때 폰을 이미 내려놓은 상태였다면 길이 0인 경계 구간을 맨 앞에 둔다.
     */
    fun usageIntervals(events: List<UsageEvent>, windowStart: Long, windowEnd: Long): List<Interval> {
        val sorted = events.filter { it.time <= windowEnd }.sortedBy { it.time }
        val raw = ArrayList<Interval>()
        var start: Long? = null
        for (e in sorted) {
            when (e.type) {
                EvType.UNLOCK -> if (start == null) start = e.time
                EvType.SCREEN_OFF -> start?.let { raw.add(Interval(it, e.time)); start = null }
            }
        }
        start?.let { raw.add(Interval(it, windowEnd)) }

        val clipped = raw
            .filter { it.end > windowStart && it.start < windowEnd }
            .map { Interval(maxOf(it.start, windowStart), minOf(it.end, windowEnd)) }
            .toMutableList()

        val knownBefore = sorted.any { it.time < windowStart }
        val inUseAtStart = clipped.firstOrNull()?.start == windowStart
        if (knownBefore && !inUseAtStart) clipped.add(0, Interval(windowStart, windowStart))
        return clipped
    }

    /** 짧은 사용(≤ shortUse)이고 앞뒤 공백이 각각 mergeGap 이상이면 그 사용을 없애 공백을 합친다. */
    fun mergeShortUses(uses: List<Interval>, p: DetectParams): List<Interval> {
        val list = uses.toMutableList()
        var i = 1
        while (i < list.size - 1) {
            val u = list[i]
            val before = u.start - list[i - 1].end
            val after = list[i + 1].start - u.end
            if (u.length <= p.shortUseMin * MIN && before >= p.mergeGapMin * MIN && after >= p.mergeGapMin * MIN) {
                list.removeAt(i)
            } else i++
        }
        return list
    }

    fun gaps(uses: List<Interval>): List<Interval> =
        (0 until uses.size - 1).map { Interval(uses[it].end, uses[it + 1].start) }

    /** 가장 긴 공백이 최소 수면 길이 이상이면 수면으로 판정. 아니면 null(추정값 만들지 않음). */
    fun detect(events: List<UsageEvent>, windowStart: Long, windowEnd: Long, p: DetectParams = DetectParams()): SleepSpan? {
        val uses = mergeShortUses(usageIntervals(events, windowStart, windowEnd), p)
        val longest = gaps(uses).maxByOrNull { it.length } ?: return null
        if (longest.length < p.minSleepMin * MIN) return null
        val bed = longest.start + p.latencyMin * MIN
        if (bed >= longest.end) return null
        return SleepSpan(bed, longest.end)
    }
}
