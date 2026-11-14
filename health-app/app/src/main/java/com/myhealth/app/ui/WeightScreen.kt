package com.myhealth.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.app.AppViewModel
import com.myhealth.app.core.WeightModel
import com.myhealth.app.data.DEV_HOME
import com.myhealth.app.data.DEV_INBODY
import com.myhealth.app.data.WeightRec

private fun shortDate(day: Long?) = day?.let { dayIso(it).substring(2).replace('-', '.') } ?: "—"

@Composable
fun WeightScreen(vm: AppViewModel) {
    val weights by vm.weights.collectAsStateWithLifecycle()
    val archive by vm.archive.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(1) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WeightRec?>(null) }

    val pts = weights.points()
    val latest = pts.lastOrNull()
    val today = today()
    val since = when (range) { 0 -> today - 8 * 7; 1 -> today - 16 * 7; else -> Long.MIN_VALUE }
    val shown = pts.filter { it.day >= since }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle("체중 추이")

            // ── 현재 위치 ──
            SectionCard {
                if (latest == null) {
                    Text("아직 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
                    Muted("오른쪽 아래 ‘기록 추가’로 첫 체중을 입력하세요.")
                } else {
                    Muted("최신 실측 · ${dayShort(latest.day)} · ${deviceName(latest.device)}")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(kg(latest.kg), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(" kg", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val first = pts.first()
                    if (first.day != latest.day) CompareRow("첫 실측 대비",
                        "${kg(first.kg)} → ${kg(latest.kg)}", "${signedKg(latest.kg - first.kg)} · ${latest.day - first.day}일")
                    archive.filter { it.max != null }.maxByOrNull { it.max!! }?.let { a ->
                        CompareRow("장기 기록 최고 대비", "${a.label} ${kg(a.max!!)} → ${kg(latest.kg)}", signedKg(latest.kg - a.max))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth()) {
                        for (g in listOf(st.goal1, st.goal2))
                            Stat("${kg(g)}kg까지", if (latest.kg > g) "${kg(latest.kg - g)}kg" else "달성", Modifier.weight(1f))
                    }
                    weights.firstOrNull { it.device == DEV_INBODY && (it.smm != null || it.pbf != null) }?.let { r ->
                        Muted("최근 인바디(${dayMd(r.day)}): " + bodyComp(r))
                    }
                }
            }

            if (pts.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("최근 8주", "최근 16주", "전체").forEachIndexed { i, s ->
                        SegmentedButton(range == i, { range = i; selectedDay = null }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(s) }
                    }
                }

                // ── 추이 그래프 ──
                SectionCard("실측 추이") {
                    if (shown.isEmpty()) Muted("이 기간에 측정이 없습니다") else {
                        val selIdx = shown.indexOfFirst { it.day == selectedDay }.takeIf { it >= 0 }
                        WeightChart(shown, st.goal1, st.goal2, selIdx, { selectedDay = shown[it].day }, Modifier.fillMaxWidth().height(230.dp))
                        val sp = selIdx?.let { shown[it] }
                        Muted(if (sp != null) "${dayShort(sp.day)} · ${kg(sp.kg)}kg · ${deviceName(sp.device)}"
                        else "● 인바디  ○ 집 체중계 · 점을 누르면 날짜와 값이 표시됩니다")
                    }
                }

                // ── 목표 시뮬레이션 ──
                SectionCard("목표 시뮬레이션") {
                    val names = mapOf(WeightModel.OVERALL to "전체 평균", WeightModel.RECENT to "최근 속도", WeightModel.SLOW to "30% 감속")
                    TableRow(listOf("시나리오", "kg/주", "${kg(st.goal1)}kg", "${kg(st.goal2)}kg", "8주 후"), header = true)
                    WeightModel.scenarios(pts, st.goal1, st.goal2).forEach { s ->
                        HorizontalDivider()
                        TableRow(listOf(names[s.key]!!, s.rate?.let { "%.3f".format(it) } ?: "—",
                            shortDate(s.goal1Day), shortDate(s.goal2Day), s.after8w?.let { kg(it) } ?: "—"),
                            bold = s.key == WeightModel.OVERALL)
                    }
                    val ref = WeightModel.recentRef(pts)
                    Muted("전체 평균 = (첫 실측 − 최신) ÷ 일수 × 7." +
                        (if (ref != null && latest != null) " 최근 속도 = ${dayMd(ref.day)} ${kg(ref.kg)} → ${dayMd(latest.day)} ${kg(latest.kg)} (${latest.day - ref.day}일)." else "") +
                        " 30% 감속은 최근 속도의 70%. 고정 속도 가정이라 실제 도달일과 다를 수 있습니다.")
                }

                // ── 주별 요약 ──
                SectionCard("주별 요약 (월~일, 측정한 주만)") {
                    val weeks = WeightModel.weekly(shown).reversed()
                    if (weeks.isEmpty()) Muted("이 기간에 측정이 없습니다") else {
                        TableRow(listOf("주", "측정일", "평균", "최저", "마지막 측정"), header = true, weights = WEEK_COLS)
                        weeks.forEach { w ->
                            HorizontalDivider()
                            TableRow(listOf("${dayMd(w.monday)}~${dayMd(w.monday + 6)}", "${w.days}", kg(w.avg), kg(w.min),
                                "${dayMd(w.last.day)} · ${kg(w.last.kg)}"), weights = WEEK_COLS)
                        }
                    }
                }
            }

            // ── 장기 기록 ──
            if (archive.isNotEmpty()) SectionCard("장기 기록 (삼성 헬스 캡처)") {
                ArchiveChart(archive, latest?.kg, Modifier.fillMaxWidth().height(200.dp))
                Muted("막대 = 기간 최저~최고, 점 = 기간 평균. 캡처 요약값이며 목표 계산에는 쓰지 않습니다.")
                TableRow(listOf("기간", "평균", "최저", "최고"), header = true, weights = ARCH_COLS)
                archive.forEach { a ->
                    HorizontalDivider()
                    TableRow(listOf(a.label, kg(a.avg), a.min?.let { kg(it) } ?: "—", a.max?.let { kg(it) } ?: "—"), weights = ARCH_COLS)
                }
            }

            // ── 실측 원장 ──
            if (weights.isNotEmpty()) SectionCard("날짜별 실측 원장 (${weights.size}회)") {
                Muted("행을 누르면 수정·삭제할 수 있습니다.")
                weights.forEach { r ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { editing = r }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${dayIso(r.day)} · ${deviceName(r.device)}", style = MaterialTheme.typography.bodyMedium)
                            if (r.smm != null || r.pbf != null || r.fat != null) Muted(bodyComp(r))
                        }
                        Text("${kg(r.kg)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }

        ExtendedFloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Text("＋ 기록 추가")
        }
    }

    if (adding) WeightDialog(null, vm) { adding = false }
    editing?.let { WeightDialog(it, vm) { editing = null } }
}

private val WEEK_COLS = listOf(1.5f, 0.7f, 0.8f, 0.8f, 1.6f)
private val ARCH_COLS = listOf(2f, 1f, 1f, 1f)

private fun signedKg(d: Double) = (if (d > 0) "+" else if (d < 0) "−" else "") + kg(kotlin.math.abs(d)) + "kg"

private fun bodyComp(r: WeightRec): String = listOfNotNull(
    r.smm?.let { "골격근 ${kg(it)}kg" },
    r.pbf?.let { "체지방률 ${kg(it)}%" },
    r.fat?.let { "체지방 ${kg(it)}kg" } ?: r.pbf?.let { "체지방 ≈${kg(r.kg * it / 100)}kg" },
).joinToString(" · ")

@Composable
private fun CompareRow(label: String, detail: String, delta: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Muted(label)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        Text(delta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, bold: Boolean = false, weights: List<Float> = listOf(1.4f, 0.9f, 1.2f, 1.2f, 0.9f)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            Text(c, Modifier.weight(weights[i]),
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
        }
    }
}

@Composable
private fun WeightDialog(rec: WeightRec?, vm: AppViewModel, onClose: () -> Unit) {
    var day by remember { mutableLongStateOf(rec?.day ?: today()) }
    var device by remember { mutableStateOf(rec?.device ?: DEV_HOME) }
    var kgText by remember { mutableStateOf(rec?.kg?.let { kg(it) } ?: "") }
    var smm by remember { mutableStateOf(rec?.smm?.let { kg(it) } ?: "") }
    var pbf by remember { mutableStateOf(rec?.pbf?.let { kg(it) } ?: "") }
    var fat by remember { mutableStateOf(rec?.fat?.let { kg(it) } ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    val w = num(kgText)
    val valid = w != null && w in 20.0..300.0 && day <= today()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (rec == null) "체중 기록 추가" else "기록 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("측정일  ${dayIso(day)}") }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(DEV_HOME, DEV_INBODY).forEachIndexed { i, d ->
                        SegmentedButton(device == d, { device = d }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(deviceName(d)) }
                    }
                }
                NumField("체중 (kg)", kgText) { kgText = it }
                if (device == DEV_INBODY) {
                    Muted("체성분은 선택 항목입니다. 검사지에 보이는 값만 입력하세요.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("골격근량", smm, Modifier.weight(1f)) { smm = it }
                        NumField("체지방률 %", pbf, Modifier.weight(1f)) { pbf = it }
                    }
                    NumField("체지방량 (kg)", fat) { fat = it }
                }
                Muted("같은 날짜·기기의 기록이 이미 있으면 새 값으로 정정됩니다.")
                if (rec != null) TextButton(onClick = { vm.deleteWeight(rec.id); onClose() }) {
                    Text("이 기록 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val body = device == DEV_INBODY
                vm.saveWeight(rec?.id, WeightRec(0, day, device, w!!,
                    if (body) num(smm) else null, if (body) num(pbf) else null, if (body) num(fat) else null))
                onClose()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { day = it / 86_400_000L }; pickDate = false }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun NumField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}
