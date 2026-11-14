package com.myhealth.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealth.app.data.ArchiveRec
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** align: 0 = 왼쪽 기준, 0.5 = 가운데, 1 = 오른쪽 기준. 화면 밖으로 나가지 않게 가로 위치를 보정한다. */
private fun DrawScope.label(
    tm: TextMeasurer, text: String, x: Float, y: Float, color: Color,
    align: Float = 0f, bold: Boolean = false, sp: Int = 10, bg: Color? = null,
) {
    val l = tm.measure(text, TextStyle(color = color, fontSize = sp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    val left = (x - l.size.width * align).coerceIn(0f, maxOf(0f, size.width - l.size.width))
    // 선 위에 겹치는 글자는 배경을 깔아 읽기 쉽게
    if (bg != null) drawRect(bg, Offset(left - 2f, y), Size(l.size.width + 4f, l.size.height.toFloat()))
    drawText(l, topLeft = Offset(left, y))
}

private val DASH = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

private fun niceStep(range: Double): Double {
    val raw = range / 4
    return listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0).firstOrNull { it >= raw } ?: 50.0
}

data class ChartPoint(val day: Long, val v: Double, val hollow: Boolean = false)
data class RefLine(val v: Double, val label: String, val color: Color)
/** 최신 실측 이후의 예상 지점 (날짜, 값, 표시 문구) */
data class Forecast(val day: Long, val v: Double, val label: String)

/**
 * 날짜 간격대로 배치하는 선 그래프. 기준선(목표 등)은 점선, 예상 구간은 최신점에서 이어지는 점선으로 그린다.
 * yInclude 의 값은 세로축 범위에 항상 포함한다.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>, refs: List<RefLine>, modifier: Modifier = Modifier,
    selected: Int? = null, onSelect: ((Int) -> Unit)? = null,
    forecast: List<Forecast> = emptyList(), yInclude: List<Double> = emptyList(),
) {
    val tm = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val lastDay = maxOf(points.lastOrNull()?.day ?: 0, forecast.lastOrNull()?.day ?: 0)
    Canvas(modifier.pointerInput(points, lastDay) {
        detectTapGestures { off ->
            if (points.isEmpty() || onSelect == null) return@detectTapGestures
            val l = 36.dp.toPx(); val w = size.width - l - 14.dp.toPx()
            val d0 = points.first().day; val span = maxOf(1L, lastDay - d0)
            onSelect(points.indices.minBy { abs(l + (points[it].day - d0).toFloat() / span * w - off.x) })
        }
    }) {
        if (points.isEmpty()) return@Canvas
        val l = 36.dp.toPx(); val r = 14.dp.toPx(); val t = 16.dp.toPx(); val b = 20.dp.toPx()
        val w = size.width - l - r; val h = size.height - t - b
        val vals = points.map { it.v } + forecast.map { it.v } + yInclude
        val lo = floor(vals.min() - 1); val hi = ceil(vals.max() + 1)
        val d0 = points.first().day; val span = maxOf(1L, lastDay - d0)
        fun x(day: Long) = l + (day - d0).toFloat() / span * w
        fun y(v: Double) = t + ((hi - v) / (hi - lo)).toFloat() * h

        val step = niceStep(hi - lo)
        var g = ceil(lo / step) * step
        while (g <= hi) {
            drawLine(cs.outlineVariant, Offset(l, y(g)), Offset(l + w, y(g)), 1f)
            label(tm, if (step < 1) kg(g) else g.toInt().toString(), l - 6.dp.toPx(), y(g) - 6.dp.toPx(), cs.onSurfaceVariant, 1f)
            g += step
        }
        // 월 경계 (기간이 길면 한 달 걸러 표시)
        val every = if (span > 240) 2 else 1
        var m = LocalDate.ofEpochDay(d0).withDayOfMonth(1).plusMonths(1)
        var k = 0
        while (m.toEpochDay() <= lastDay) {
            val mx = x(m.toEpochDay())
            drawLine(cs.outlineVariant, Offset(mx, t + h), Offset(mx, t + h + 4.dp.toPx()), 2f)
            if (k % every == 0) label(tm, if (m.monthValue == 1) "${m.year % 100}년 1월" else "${m.monthValue}월", mx, t + h + 5.dp.toPx(), cs.onSurfaceVariant, 0.5f)
            m = m.plusMonths(1); k++
        }
        for (ref in refs) {
            if (ref.v < lo || ref.v > hi) continue
            drawLine(ref.color, Offset(l, y(ref.v)), Offset(l + w, y(ref.v)), 2f, pathEffect = DASH)
            label(tm, ref.label, l + 4.dp.toPx(), y(ref.v) - 14.dp.toPx(), ref.color, 0f, bold = true)
        }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(x(points[0].day), y(points[0].v))
                points.drop(1).forEach { lineTo(x(it.day), y(it.v)) }
            }
            drawPath(path, cs.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val lp = points.last()
        // 예상 구간
        if (forecast.isNotEmpty()) {
            val path = Path().apply {
                moveTo(x(lp.day), y(lp.v))
                forecast.forEach { lineTo(x(it.day), y(it.v)) }
            }
            drawPath(path, cs.secondary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            forecast.forEachIndexed { i, f ->
                val c = Offset(x(f.day), y(f.v))
                drawCircle(cs.surfaceContainerLow, 7.dp.toPx(), c)
                drawCircle(cs.secondary, 5.dp.toPx(), c)
                // 내려가는 점선과 겹치지 않게: 중간 지점은 오른쪽 위, 마지막 지점은 왼쪽 아래
                if (i < forecast.lastIndex) label(tm, f.label, c.x - 2.dp.toPx(), c.y - 26.dp.toPx(), cs.secondary, 0f, bold = true, sp = 12)
                else label(tm, f.label, c.x + 6.dp.toPx(), c.y + 9.dp.toPx(), cs.secondary, 1f, bold = true, sp = 12)
            }
        }
        points.forEachIndexed { i, p ->
            val c = Offset(x(p.day), y(p.v)); val last = i == points.lastIndex
            if (i == selected) drawLine(cs.outline, Offset(c.x, t), Offset(c.x, t + h), 1.5f, pathEffect = DASH)
            drawCircle(cs.surfaceContainerLow, (if (last) 7 else 5).dp.toPx(), c)
            if (!p.hollow) drawCircle(cs.primary, (if (last) 5.5f else 3.5f).dp.toPx(), c)
            else drawCircle(cs.primary, (if (last) 5f else 3.2f).dp.toPx(), c, style = Stroke(2.dp.toPx()))
            if (i == selected) drawCircle(cs.primary, 9.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
        // 최신값 글자는 선과 겹치지 않는 쪽(내려오는 추세면 점 아래)에 둔다
        val falling = points.size < 2 || points[points.size - 2].v >= lp.v
        val below = falling && y(lp.v) + 26.dp.toPx() < t + h
        val right = forecast.isEmpty()
        label(tm, kg(lp.v), x(lp.day) + (if (right) 8 else -4).dp.toPx(), y(lp.v) + (if (below) 8 else -24).dp.toPx(),
            cs.primary, if (right) 1f else 1f, bold = true, sp = 13)
    }
}

/** 삼성 헬스 장기 캡처: 기간별 최저~최고 범위 막대와 평균 점, 현재 실측을 점선으로 겹쳐 표시 */
@Composable
fun ArchiveChart(items: List<ArchiveRec>, latestKg: Double?, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    Canvas(modifier) {
        if (items.isEmpty()) return@Canvas
        val l = 36.dp.toPx(); val r = 8.dp.toPx(); val t = 14.dp.toPx(); val b = 20.dp.toPx()
        val w = size.width - l - r; val h = size.height - t - b
        val all = items.flatMap { listOfNotNull(it.avg, it.min, it.max) } + listOfNotNull(latestKg)
        val lo = floor(all.min() - 2); val hi = ceil(all.max() + 2)
        fun y(kg: Double) = t + ((hi - kg) / (hi - lo)).toFloat() * h
        val step = niceStep(hi - lo)
        var g = ceil(lo / step) * step
        while (g <= hi) {
            drawLine(cs.outlineVariant, Offset(l, y(g)), Offset(l + w, y(g)), 1f)
            label(tm, g.toInt().toString(), l - 6.dp.toPx(), y(g) - 6.dp.toPx(), cs.onSurfaceVariant, 1f)
            g += step
        }
        val slot = w / items.size
        items.forEachIndexed { i, a ->
            val cx = l + slot * (i + 0.5f)
            if (a.min != null && a.max != null)
                drawLine(cs.primary.copy(alpha = 0.35f), Offset(cx, y(a.max)), Offset(cx, y(a.min)), minOf(slot * 0.45f, 16.dp.toPx()), StrokeCap.Round)
            drawCircle(cs.surfaceContainerLow, 5.dp.toPx(), Offset(cx, y(a.avg)))
            drawCircle(cs.primary, 3.5.dp.toPx(), Offset(cx, y(a.avg)))
            label(tm, a.short, cx, t + h + 5.dp.toPx(), cs.onSurfaceVariant, 0.5f)
        }
        if (latestKg != null) {
            drawLine(cs.secondary, Offset(l, y(latestKg)), Offset(l + w, y(latestKg)), 2f, pathEffect = DASH)
            label(tm, "현재 ${kg(latestKg)}", l + 4.dp.toPx(), y(latestKg) - 14.dp.toPx(), cs.secondary, 0f, bold = true)
        }
    }
}

/** 날짜별 수면시간 막대. minutes[i] == null 이면 기록 없음(빈칸). */
@Composable
fun SleepBarChart(firstDay: Long, minutes: List<Int?>, targetMin: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    Canvas(modifier) {
        // 막대 위 숫자: 7일 이하 = "5시간 / 39분" 두 줄, 14일 이하 = "5:39", 그보다 많으면 생략
        val twoLine = minutes.size <= 7; val oneLine = !twoLine && minutes.size <= 14
        val l = 28.dp.toPx(); val r = 6.dp.toPx(); val b = 18.dp.toPx()
        val t = (if (twoLine) 34 else if (oneLine) 22 else 12).dp.toPx()
        val w = size.width - l - r; val h = size.height - t - b
        val hiH = ceil((maxOf(targetMin, minutes.maxOfOrNull { it ?: 0 } ?: 0) + 1) / 120.0).toInt() * 2
        fun y(min: Int) = t + (1 - min / (hiH * 60f)) * h
        for (hr in 0..hiH step 2) {
            drawLine(cs.outlineVariant, Offset(l, y(hr * 60)), Offset(l + w, y(hr * 60)), 1f)
            label(tm, "${hr}h", l - 5.dp.toPx(), y(hr * 60) - 6.dp.toPx(), cs.onSurfaceVariant, 1f)
        }
        val slot = w / minutes.size
        val bw = (slot * 0.68f).coerceAtMost(22.dp.toPx())
        minutes.forEachIndexed { i, m ->
            if (m == null) return@forEachIndexed
            val cx = l + slot * (i + 0.5f)
            val rad = CornerRadius(minOf(bw / 2, 4.dp.toPx()))
            drawRoundRect(if (m >= targetMin) cs.primary else cs.primary.copy(alpha = 0.5f),
                Offset(cx - bw / 2, y(m)), Size(bw, t + h - y(m)), rad)
        }
        drawLine(cs.tertiary, Offset(l, y(targetMin)), Offset(l + w, y(targetMin)), 2f, pathEffect = DASH)
        minutes.forEachIndexed { i, m ->
            if (m == null) return@forEachIndexed
            val cx = l + slot * (i + 0.5f)
            if (twoLine) {
                label(tm, "${m / 60}시간", cx, y(m) - 28.dp.toPx(), cs.onSurface, 0.5f, bold = true, bg = cs.surfaceContainerLow)
                label(tm, "${m % 60}분", cx, y(m) - 15.dp.toPx(), cs.onSurface, 0.5f, bold = true, bg = cs.surfaceContainerLow)
            } else if (oneLine) label(tm, "%d:%02d".format(m / 60, m % 60), cx, y(m) - 15.dp.toPx(), cs.onSurface, 0.5f, bold = true, sp = 9, bg = cs.surfaceContainerLow)
        }
        label(tm, dayMd(firstDay).replace('-', '/'), l, t + h + 4.dp.toPx(), cs.onSurfaceVariant)
        label(tm, dayMd(firstDay + minutes.size - 1).replace('-', '/'), l + w, t + h + 4.dp.toPx(), cs.onSurfaceVariant, 1f)
    }
}

/**
 * 날짜별 취침~기상 구간을 세로 막대로 그린다. 세로축은 탐색 구간(위 = 시작).
 * spans[i] = (취침, 기상)을 탐색 구간 시작 기준 분으로 환산한 값, 없으면 null.
 */
@Composable
fun SleepSpanChart(firstDay: Long, spans: List<Pair<Int, Int>?>, winStartMin: Int, winLenMin: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    Canvas(modifier) {
        val l = 36.dp.toPx(); val r = 6.dp.toPx(); val t = 10.dp.toPx(); val b = 18.dp.toPx()
        val w = size.width - l - r; val h = size.height - t - b
        fun y(off: Int) = t + off.coerceIn(0, winLenMin) / winLenMin.toFloat() * h
        var off = 0
        while (off <= winLenMin) {
            drawLine(cs.outlineVariant, Offset(l, y(off)), Offset(l + w, y(off)), 1f)
            label(tm, minClock(winStartMin + off), l - 5.dp.toPx(), y(off) - 6.dp.toPx(), cs.onSurfaceVariant, 1f)
            off += 180
        }
        val slot = w / spans.size
        val bw = (slot * 0.68f).coerceAtMost(22.dp.toPx())
        spans.forEachIndexed { i, s ->
            if (s == null) return@forEachIndexed
            val cx = l + slot * (i + 0.5f)
            drawRoundRect(cs.secondary, Offset(cx - bw / 2, y(s.first)), Size(bw, maxOf(2f, y(s.second) - y(s.first))),
                CornerRadius(minOf(bw / 2, 4.dp.toPx())))
        }
        label(tm, dayMd(firstDay).replace('-', '/'), l, t + h + 4.dp.toPx(), cs.onSurfaceVariant)
        label(tm, dayMd(firstDay + spans.size - 1).replace('-', '/'), l + w, t + h + 4.dp.toPx(), cs.onSurfaceVariant, 1f)
    }
}

/** 기록 탭의 하루 가로 막대. start/end = 탐색 구간 대비 0..1 비율, 없으면 빈 트랙만. */
@Composable
fun SleepTimelineBar(start: Float?, end: Float?, winLenMin: Int, manual: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier) {
        val rad = CornerRadius(size.height / 2)
        drawRoundRect(cs.surfaceVariant, size = size, cornerRadius = rad)
        var off = 180
        while (off < winLenMin) {
            val x = off / winLenMin.toFloat() * size.width
            drawLine(cs.outlineVariant, Offset(x, 0f), Offset(x, size.height), 1f)
            off += 180
        }
        if (start != null && end != null) {
            val s = start.coerceIn(0f, 1f) * size.width; val e = end.coerceIn(0f, 1f) * size.width
            drawRoundRect(if (manual) cs.tertiary else cs.primary, Offset(s, 0f), Size(maxOf(4f, e - s), size.height), rad)
        }
    }
}

/** 기록 탭 상단의 시각 눈금 (3시간 간격) */
@Composable
fun TimelineAxis(winStartMin: Int, winLenMin: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        var off = 0
        while (off <= winLenMin) {
            label(tm, "%02d".format(Math.floorMod(winStartMin + off, 1440) / 60), off / winLenMin.toFloat() * size.width, 0f, c, 0.5f)
            off += 180
        }
    }
}
