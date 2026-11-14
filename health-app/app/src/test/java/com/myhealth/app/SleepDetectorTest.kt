package com.myhealth.app

import com.myhealth.app.core.*
import org.junit.Assert.*
import org.junit.Test

class SleepDetectorTest {
    private val H = 3_600_000L
    private val M = 60_000L
    // 탐색 구간: 0시점 = 전날 20:00, 끝 = +18h (당일 14:00)
    private val ws = 100 * H
    private val we = ws + 18 * H
    private fun use(startMin: Long, endMin: Long) = listOf(
        UsageEvent(ws + startMin * M, EvType.UNLOCK),
        UsageEvent(ws + endMin * M, EvType.SCREEN_OFF),
    )

    @Test fun basicNight() {
        // 20:30~00:40 사용, 07:10 잠금 해제
        val ev = use(30, 280) + use(670, 700)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(ws + 280 * M, s.bedtime)
        assertEquals(ws + 670 * M, s.wake)
        assertEquals(390, s.minutes)
    }

    @Test fun shortCheckAtDawnIsMerged() {
        // 새벽 03:00에 1분 시계 확인 → 수면 유지
        val ev = use(30, 280) + use(420, 421) + use(670, 700)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(390, s.minutes)
    }

    @Test fun longUseAtDawnSplitsSleep() {
        // 새벽 10분 사용 → 둘로 쪼개지고 긴 쪽만 수면
        val ev = use(30, 280) + use(420, 430) + use(670, 700)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(ws + 430 * M, s.bedtime)
        assertEquals(240, s.minutes)
    }

    @Test fun shortUseNotMergedWhenNeighbourGapShort() {
        // 1분 사용이지만 앞 공백이 20분뿐 → 합치지 않음
        val ev = use(30, 280) + use(300, 301) + use(670, 700)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(ws + 301 * M, s.bedtime)
    }

    @Test fun tooShortGapGivesNoRecord() {
        val ev = use(0, 300) + use(400, 900) + use(1000, 1080)
        assertNull(SleepDetector.detect(ev, ws, we))
    }

    @Test fun noEventsGivesNoRecord() {
        assertNull(SleepDetector.detect(emptyList(), ws, we))
    }

    @Test fun notificationScreenOnWithoutUnlockIsIgnored() {
        // 잠금 해제 없이 화면만 꺼짐 이벤트가 여러 번 와도 사용으로 보지 않음
        val ev = use(30, 280) +
            listOf(UsageEvent(ws + 400 * M, EvType.SCREEN_OFF), UsageEvent(ws + 500 * M, EvType.SCREEN_OFF)) +
            use(670, 700)
        assertEquals(390, SleepDetector.detect(ev, ws, we)!!.minutes)
    }

    @Test fun usageCrossingWindowStartIsClipped() {
        // 19:00부터 쓰다 23:00에 내려놓음
        val ev = use(-60, 180) + use(660, 700)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(ws + 180 * M, s.bedtime)
        assertEquals(480, s.minutes)
    }

    @Test fun phoneDownBeforeWindowStartsAtWindowStart() {
        // 19:00에 내려놓고 06:00에 처음 집음 → 취침은 탐색 시작 시각으로 잘림
        val ev = use(-120, -60) + use(600, 640)
        val s = SleepDetector.detect(ev, ws, we)!!
        assertEquals(ws, s.bedtime)
        assertEquals(ws + 600 * M, s.wake)
    }

    @Test fun noWakeYetGivesNoRecord() {
        // 내려놓은 뒤 아직 잠금 해제가 없음
        assertNull(SleepDetector.detect(use(30, 280), ws, we))
    }

    @Test fun latencyOffsetShiftsBedtime() {
        val ev = use(30, 280) + use(670, 700)
        val s = SleepDetector.detect(ev, ws, we, DetectParams(latencyMin = 15))!!
        assertEquals(ws + 295 * M, s.bedtime)
        assertEquals(375, s.minutes)
    }

    @Test fun stillInUseAtWindowEnd() {
        val ev = use(30, 280) + listOf(UsageEvent(ws + 670 * M, EvType.UNLOCK))
        assertEquals(390, SleepDetector.detect(ev, ws, we)!!.minutes)
    }
}
