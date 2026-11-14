package com.myhealth.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.app.AppViewModel
import com.myhealth.app.collect.Collector
import com.myhealth.app.core.SleepStats
import com.myhealth.app.core.WRow
import com.myhealth.app.core.WeightModel
import com.myhealth.app.data.SRC_MANUAL
import com.myhealth.app.data.SleepRec
import com.myhealth.app.data.WeightRec
import com.myhealth.app.toNight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

fun List<WeightRec>.points() = WeightModel.daily(map { WRow(it.day, it.kg, it.device, it.savedAt) })

fun openUsageAccess(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (sub != null) Muted(sub)
    }
}

@Composable
fun PermissionCard() {
    val ctx = LocalContext.current
    SectionCard("수면 자동 기록을 켜려면") {
        Text("휴대폰 잠금 해제·화면 꺼짐 기록으로 취침·기상 시각을 계산합니다. 설정에서 ‘나의 건강’의 사용 정보 접근을 한 번만 허용해 주세요.",
            style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { openUsageAccess(ctx) }) { Text("사용 정보 접근 허용하러 가기") }
    }
}

// ───────────────────────── 오늘 ─────────────────────────
@Composable
fun TodayScreen(vm: AppViewModel, onTab: (Int) -> Unit) {
    val sleeps by vm.sleeps.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    val perm by vm.hasPermission.collectAsStateWithLifecycle()
    val today = today()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("오늘", dayShort(today))
        if (!perm) PermissionCard()

        val byDay = sleeps.filter { it.valid }.associateBy { it.day }
        val last = byDay[today]
        val week = (today - 6..today).mapNotNull { byDay[it]?.toNight() }
        SectionCard("어젯밤 수면", trailing = { if (last?.source == SRC_MANUAL) Muted("수동") }) {
            if (last != null) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("취침", clock(last.bed!!), Modifier.weight(1f))
                    Stat("기상", clock(last.wake!!), Modifier.weight(1f))
                    Stat("수면시간", dur(last.minutes), Modifier.weight(1.4f))
                }
            } else {
                Text("아직 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
                Muted(if (perm) "기상 후 폰 잠금을 해제한 뒤에 계산됩니다. 최소 수면 길이(${dur(st.minSleepMin)})보다 짧으면 기록하지 않습니다."
                else "사용 정보 접근을 허용하면 자동으로 기록됩니다.")
            }
            if (week.isNotEmpty()) {
                val avg = week.sumOf { it.minutes }.toDouble() / week.size
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Muted("7일 평균 (기록 ${week.size}일)")
                        Text(dur(avg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (last != null) Column(Modifier.weight(1f)) {
                        Muted("평균 대비")
                        Text(signedMin(last.minutes - avg.roundToInt()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                SleepBarChart(today - 6, (today - 6..today).map { byDay[it]?.minutes }, st.targetSleepMin,
                    Modifier.fillMaxWidth().height(110.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTab(1) }) { Text("기록 보기") }
                if (perm) TextButton(onClick = { vm.collectNow() }) { Text("지금 다시 수집") }
            }
        }

        val pts = weights.points()
        SectionCard("체중") {
            val lp = pts.lastOrNull()
            if (lp == null) {
                Text("아직 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
            } else {
                val sc = WeightModel.scenarios(pts, st.goal1, st.goal2).first()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(kg(lp.kg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(" kg", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.width(12.dp))
                    Muted("${dayShort(lp.day)} · ${deviceName(lp.device)}", Modifier.padding(bottom = 8.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat("${kg(st.goal1)}kg까지", if (lp.kg > st.goal1) "${kg(lp.kg - st.goal1)}kg" else "달성", Modifier.weight(1f))
                    Stat("전체 평균 속도", sc.rate?.let { "주 %.2fkg".format(it) } ?: "—", Modifier.weight(1f))
                    Stat("예상 도달일", sc.goal1Day?.let { dayIso(it).substring(2).replace('-', '.') } ?: "—", Modifier.weight(1f))
                }
            }
            FilledTonalButton(onClick = { onTab(3) }) { Text("체중 기록·추이 보기") }
        }
    }
}

// ───────────────────────── 수면 기록 ─────────────────────────
@Composable
fun SleepLogScreen(vm: AppViewModel) {
    val sleeps by vm.sleeps.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    val perm by vm.hasPermission.collectAsStateWithLifecycle()
    var count by rememberSaveable { mutableIntStateOf(7) }
    var editing by remember { mutableStateOf<Long?>(null) }
    val today = today()
    val byDay = sleeps.associateBy { it.day }
    val zone = zone()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { ScreenTitle("수면 기록", "날짜는 기상일 기준 · 탭하면 수정") }
        if (!perm) item { Spacer(Modifier.height(8.dp)); PermissionCard() }
        item {
            Spacer(Modifier.height(8.dp))
            val week = (today - 6..today).map { d -> byDay[d]?.takeIf { it.valid }?.minutes }
            SectionCard("최근 7일 수면시간") {
                SleepBarChart(today - 6, week, st.targetSleepMin, Modifier.fillMaxWidth().height(190.dp))
                val rec = week.filterNotNull()
                Muted(if (rec.isEmpty()) "최근 7일 기록이 없습니다"
                else "평균 ${dur(rec.sum() / rec.size)} (기록 ${rec.size}일) · 점선 = 목표 ${dur(st.targetSleepMin)}")
            }
            Spacer(Modifier.height(12.dp))
            TimelineAxis(st.winStartMin, st.winLenMin, Modifier.fillMaxWidth().height(16.dp))
        }
        items((0 until count).map { today - it }, key = { it }) { day ->
            val rec = byDay[day]
            val (ws, _) = Collector.window(day, st, zone)
            val len = st.winLenMin * 60_000f
            Column(Modifier.fillMaxWidth().clickable { editing = day }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayShort(day), Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (rec?.valid == true) {
                        Text("${clock(rec.bed!!)} – ${clock(rec.wake!!)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (rec.source == SRC_MANUAL) Muted("수동  ")
                        Text(dur(rec.minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    } else {
                        Muted(if (rec?.deleted == true) "제외됨" else "기록 없음", Modifier.weight(1f))
                    }
                }
                SleepTimelineBar(
                    if (rec?.valid == true) (rec.bed!! - ws) / len else null,
                    if (rec?.valid == true) (rec.wake!! - ws) / len else null,
                    st.winLenMin, rec?.source == SRC_MANUAL, Modifier.fillMaxWidth().height(12.dp),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (count < 365) TextButton(onClick = { count = if (count == 7) 30 else count + 30 }) {
                    Text(if (count == 7) "이전 기록 상세보기" else "이전 30일 더 보기")
                }
                if (count > 7) TextButton(onClick = { count = 7 }) { Text("최근 7일만 보기") }
            }
        }
    }

    editing?.let { day -> SleepEditDialog(day, byDay[day], vm) { editing = null } }
}

@Composable
private fun SleepEditDialog(day: Long, rec: SleepRec?, vm: AppViewModel, onClose: () -> Unit) {
    val zone = zone()
    fun lt(ms: Long?, def: LocalTime) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: def
    val b0 = lt(rec?.bed ?: rec?.autoBed, LocalTime.of(23, 30))
    val w0 = lt(rec?.wake ?: rec?.autoWake, LocalTime.of(7, 0))
    val bed = rememberTimePickerState(b0.hour, b0.minute, true)
    val wake = rememberTimePickerState(w0.hour, w0.minute, true)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${dayShort(day)} 기상") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("취침 시각", style = MaterialTheme.typography.labelLarge)
                TimeInput(bed)
                Text("기상 시각", style = MaterialTheme.typography.labelLarge)
                TimeInput(wake)
                if (rec?.autoBed != null && rec.autoWake != null)
                    Muted("자동 감지 원본: ${clock(rec.autoBed)} – ${clock(rec.autoWake)}")
                if (rec != null) Row {
                    if (rec.valid) TextButton(onClick = { vm.deleteSleep(day); onClose() }) { Text("이 날 제외") }
                    if (rec.source == SRC_MANUAL) TextButton(onClick = { vm.restoreAutoSleep(day); onClose() }) { Text("자동값으로 되돌리기") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = LocalDate.ofEpochDay(day)
                val bm = bed.hour * 60 + bed.minute; val wm = wake.hour * 60 + wake.minute
                val wakeMs = d.atTime(wake.hour, wake.minute).atZone(zone).toInstant().toEpochMilli()
                val bedMs = (if (bm < wm) d else d.minusDays(1)).atTime(bed.hour, bed.minute).atZone(zone).toInstant().toEpochMilli()
                vm.saveSleepManual(day, bedMs, wakeMs); onClose()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
    )
}

// ───────────────────────── 분석 ─────────────────────────
@Composable
fun AnalysisScreen(vm: AppViewModel) {
    val sleeps by vm.sleeps.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val ranges = listOf(7, 30, 90)
    val n = ranges[sel]
    val today = today()
    val first = today - n + 1
    val zone = zone()
    val byDay = sleeps.filter { it.valid }.associateBy { it.day }
    val nights = (first..today).mapNotNull { byDay[it]?.toNight(zone) }
    val sum = SleepStats.summary(nights)
    val week = (today - 6..today).mapNotNull { byDay[it]?.toNight(zone) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("수면 분석")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ranges.forEachIndexed { i, r ->
                SegmentedButton(sel == i, { sel = i }, SegmentedButtonDefaults.itemShape(i, ranges.size)) { Text("${r}일") }
            }
        }
        if (sum == null) {
            SectionCard { Text("이 기간에 수면 기록이 없습니다"); Muted("기록이 쌓이면 평균·규칙성·추이가 표시됩니다.") }
        } else {
            SectionCard("요약") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("평균 수면시간", dur(sum.avgMinutes), Modifier.weight(1f), "기록 ${sum.count}일 / ${n}일")
                    Stat("수면 중간점", minClock(sum.avgMid), Modifier.weight(1f), "생활 리듬 위치")
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat("평균 취침", minClock(sum.avgBed), Modifier.weight(1f))
                    Stat("평균 기상", minClock(sum.avgWake), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat("취침 규칙성", "±${sum.bedSd.roundToInt()}분", Modifier.weight(1f), "표준편차 · 작을수록 규칙적")
                    Stat("평일·주말 차이", sum.holidayShift?.let { signedMin(it.roundToInt()) } ?: "—", Modifier.weight(1f),
                        if (sum.holidayShift != null) "휴일 ${minClock(sum.holidayMid!!)} · 출근일 ${minClock(sum.workMid!!)}" else "토·일과 평일 기록이 모두 필요")
                }
            }
        }
        SectionCard("수면 부족 누적 · 최근 7일") {
            if (week.isEmpty()) Muted("최근 7일 기록이 없습니다") else {
                val debt = SleepStats.debt(week, st.targetSleepMin)
                Text(if (debt > 0) "${dur(debt)} 부족" else if (debt < 0) "${dur(-debt)} 여유" else "목표와 같음",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Muted("목표 ${dur(st.targetSleepMin)} × 기록 ${week.size}일 기준")
            }
        }
        if (sum != null) {
            SectionCard("수면시간 추이") {
                SleepBarChart(first, (first..today).map { byDay[it]?.minutes }, st.targetSleepMin, Modifier.fillMaxWidth().height(170.dp))
                Muted("점선 = 목표 ${dur(st.targetSleepMin)} · 옅은 막대 = 목표 미달")
            }
            SectionCard("취침·기상 시각 추이") {
                val spans = (first..today).map { d ->
                    byDay[d]?.let { r ->
                        val ws = Collector.window(d, st, zone).first
                        ((r.bed!! - ws) / 60_000L).toInt() to ((r.wake!! - ws) / 60_000L).toInt()
                    }
                }
                SleepSpanChart(first, spans, st.winStartMin, st.winLenMin, Modifier.fillMaxWidth().height(210.dp))
                Muted("막대 위 끝 = 취침, 아래 끝 = 기상")
            }
        }
    }
}
