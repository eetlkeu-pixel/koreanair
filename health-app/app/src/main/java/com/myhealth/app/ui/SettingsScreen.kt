package com.myhealth.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.app.AppViewModel
import com.myhealth.app.data.Backup
import com.myhealth.app.data.Settings
import com.myhealth.app.data.round1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Edit {
    data class Num(val title: String, val unit: String, val value: Double, val range: ClosedFloatingPointRange<Double>,
                   val integer: Boolean, val apply: (Double) -> Settings) : Edit
    data class Time(val title: String, val minutes: Int, val apply: (Int) -> Settings) : Edit
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val perm by vm.hasPermission.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<Edit?>(null) }
    var pending by remember { mutableStateOf<(suspend () -> String)?>(null) }

    fun write(uri: Uri?, make: suspend () -> String) {
        if (uri == null) return
        scope.launch {
            vm.message.value = runCatching {
                val text = make()
                withContext(Dispatchers.IO) {
                    // 엑셀에서 한글이 깨지지 않도록 CSV에는 BOM을 붙인다
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                }
                "저장했습니다"
            }.getOrElse { "저장 실패: ${it.message}" }
        }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> pending?.let { write(uri, it) } }
    val jsonSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> pending?.let { write(uri, it) } }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } } }
                .onSuccess { vm.restore(it) }.onFailure { vm.message.value = "파일을 읽지 못했습니다" }
        }
    }
    fun int(title: String, unit: String, v: Int, range: IntRange, apply: (Int) -> Settings) =
        Edit.Num(title, unit, v.toDouble(), range.first.toDouble()..range.last.toDouble(), true) { apply(it.toInt()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("설정")

        SectionCard("수면 자동 기록") {
            Item("사용 정보 접근 권한", if (perm) "허용됨" else "필요 · 눌러서 허용") { openUsageAccess(ctx) }
            Item("지금 다시 수집", "최근 7일을 다시 계산합니다. 수동 수정한 날은 덮어쓰지 않습니다.") { vm.collectNow() }
            Item("삼성 절전 예외 등록", "배터리 → 백그라운드 사용 제한에서 ‘나의 건강’을 ‘제한 없음’으로 두면 매일 오전 11시 자동 수집이 밀리지 않습니다. 밀려도 앱을 열면 7일치까지 다시 채웁니다.") { openBattery(ctx) }
        }

        SectionCard("수면 감지 기준") {
            Item("탐색 구간 시작", minClock(st.winStartMin)) { edit = Edit.Time("탐색 구간 시작", st.winStartMin) { st.copy(winStartMin = it) } }
            Item("탐색 구간 끝", minClock(st.winEndMin) + if (st.winStartMin >= st.winEndMin) " (다음 날)" else "") {
                edit = Edit.Time("탐색 구간 끝", st.winEndMin) { st.copy(winEndMin = it) }
            }
            Item("최소 수면 길이", dur(st.minSleepMin)) { edit = int("최소 수면 길이", "분", st.minSleepMin, 30..720) { st.copy(minSleepMin = it) } }
            Item("짧은 사용 기준", "${st.shortUseMin}분 이하는 수면 유지") { edit = int("짧은 사용 기준", "분", st.shortUseMin, 0..30) { st.copy(shortUseMin = it) } }
            Item("짧은 사용 앞뒤 공백", "각각 ${st.mergeGapMin}분 이상일 때만 합침") { edit = int("짧은 사용 앞뒤 공백", "분", st.mergeGapMin, 0..180) { st.copy(mergeGapMin = it) } }
            Item("잠드는 시간 보정", "폰을 내려놓고 +${st.latencyMin}분 뒤 취침으로 기록") { edit = int("잠드는 시간 보정", "분", st.latencyMin, 0..120) { st.copy(latencyMin = it) } }
            Muted("기준을 바꾸면 최근 7일의 자동 기록이 새 기준으로 다시 계산됩니다.")
        }

        SectionCard("목표") {
            Item("목표 수면시간", dur(st.targetSleepMin)) { edit = int("목표 수면시간", "분", st.targetSleepMin, 180..720) { st.copy(targetSleepMin = it) } }
            Item("1차 목표 체중", "${kg(st.goal1)} kg") { edit = Edit.Num("1차 목표 체중", "kg", st.goal1, 30.0..200.0, false) { st.copy(goal1 = it.round1()) } }
            Item("2차 목표 체중", "${kg(st.goal2)} kg") { edit = Edit.Num("2차 목표 체중", "kg", st.goal2, 30.0..200.0, false) { st.copy(goal2 = it.round1()) } }
            Item("키", "${kg(st.heightCm)} cm · BMI 계산에 사용") { edit = Edit.Num("키", "cm", st.heightCm, 100.0..230.0, false) { st.copy(heightCm = it.round1()) } }
        }

        SectionCard("내보내기·백업") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pending = { "﻿" + Backup.sleepCsv(ctx) }; csvSaver.launch("sleep.csv") }, modifier = Modifier.weight(1f)) { Text("수면 CSV") }
                OutlinedButton(onClick = { pending = { "﻿" + Backup.weightCsv(ctx) }; csvSaver.launch("weight.csv") }, modifier = Modifier.weight(1f)) { Text("체중 CSV") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pending = { Backup.exportJson(ctx) }; jsonSaver.launch("my-health-backup.json") }, modifier = Modifier.weight(1f)) { Text("전체 백업") }
                OutlinedButton(onClick = { opener.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("백업 복원") }
            }
            Muted("모든 데이터는 이 휴대폰 안에만 저장됩니다(인터넷 권한 없음). 앱을 지우거나 폰을 바꾸기 전에 ‘전체 백업’ 파일을 만들어 두세요.")
        }
    }

    when (val e = edit) {
        is Edit.Num -> NumDialog(e, { vm.saveSettings(it); vm.onResume() }) { edit = null }
        is Edit.Time -> TimeDialog(e, { vm.saveSettings(it); vm.onResume() }) { edit = null }
        null -> {}
    }
}

@Composable
private fun Item(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Muted(value)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NumDialog(e: Edit.Num, save: (Settings) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(if (e.integer) e.value.toInt().toString() else kg(e.value)) }
    val v = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in e.range && (!e.integer || it % 1.0 == 0.0) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(e.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("${e.title} (${e.unit})", text) { text = it }
                Muted("허용 범위 ${fmt(e.range.start, e.integer)} ~ ${fmt(e.range.endInclusive, e.integer)} ${e.unit}")
            }
        },
        confirmButton = { TextButton(enabled = v != null, onClick = { save(e.apply(v!!)); onClose() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
    )
}

private fun fmt(v: Double, integer: Boolean) = if (integer) v.toInt().toString() else kg(v)

@Composable
private fun TimeDialog(e: Edit.Time, save: (Settings) -> Unit, onClose: () -> Unit) {
    val state = rememberTimePickerState(e.minutes / 60, e.minutes % 60, true)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(e.title) },
        text = { TimeInput(state) },
        confirmButton = { TextButton(onClick = { save(e.apply(state.hour * 60 + state.minute)); onClose() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
    )
}

private fun openBattery(ctx: Context) {
    val pkg = Uri.parse("package:${ctx.packageName}")
    for (i in listOf(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )) {
        if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
