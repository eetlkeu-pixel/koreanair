package com.myhealth.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.app.AppViewModel
import com.myhealth.app.core.SnoreMath
import com.myhealth.app.data.SnoreEvent
import com.myhealth.app.data.SnoreSession
import com.myhealth.app.snore.SnoreService
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

const val SNORE_DISCLAIMER = "이 앱은 의료기기나 진단 도구가 아닙니다. 수치는 내 기록끼리의 추세 비교용입니다."
val SNORE_TAGS = listOf("음주", "늦은 식사", "운동", "옆으로 자기", "코막힘", "피로")
/** 태그 비교에 필요한 최소 밤 수 */
const val MIN_NIGHTS = 5

/** 한 밤의 요약. "아님"으로 표시한 구간은 계산에서 뺀다 */
data class SnoreStats(val score: Double, val snoreSec: Double, val pct: Double, val peak: SnoreEvent?, val count: Int)

fun snoreStats(s: SnoreSession, events: List<SnoreEvent>): SnoreStats {
    val ev = events.filter { it.sessionId == s.id && it.label != 0 }
    val sec = ev.sumOf { it.durSec }
    return SnoreStats(
        SnoreMath.score(ev.map { it.durSec to it.intensity }, s.measuredSec), sec,
        if (s.measuredSec > 0) sec / s.measuredSec * 100 else 0.0, ev.maxByOrNull { it.peakDb }, ev.size,
    )
}

private fun durSec(sec: Double): String = if (sec < 60) "${sec.roundToInt()}초" else dur((sec / 60).roundToInt())
private fun intensityName(i: Int) = when (i) { 3 -> "큼"; 2 -> "보통"; else -> "조용" }
@Composable private fun intensityColor(i: Int): Color = when (i) {
    3 -> MaterialTheme.colorScheme.error; 2 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary
}

@Composable
fun SnoreScreen(vm: AppViewModel) {
    val sessions by vm.snoreSessions.collectAsStateWithLifecycle()
    val events by vm.snoreEvents.collectAsStateWithLifecycle()
    val runningId by vm.snoreRunning.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val done = sessions.filter { !it.running }
    val report = done.firstOrNull { it.id == selected } ?: done.firstOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("코골이 측정", "밤새 마이크로 듣고 코골이 구간만 기록합니다")
        val run = sessions.firstOrNull { it.id == runningId }
        if (run != null) RunningCard(run, events.count { it.sessionId == run.id }) else StartCard(vm)

        if (report != null) {
            ReportCard(report, events, vm)
            SamplesCard(report, events, vm)
        } else if (run == null) {
            SectionCard { Text("아직 측정 기록이 없습니다"); Muted("오늘 밤 ‘측정 시작’을 누르고 자면 아침에 리포트가 나옵니다.") }
        }
        if (done.size >= 2) CompareCard(done, events)
        if (done.isNotEmpty()) SectionCard("지난 기록") {
            done.take(30).forEach { s ->
                val st = snoreStats(s, events)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { selected = s.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayShort(s.day), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (s.id == report?.id) FontWeight.Bold else FontWeight.Normal)
                        Muted("코골이 ${durSec(st.snoreSec)} / 측정 ${durSec(s.measuredSec)}" + if (s.tagList.isNotEmpty()) " · ${s.tagList.joinToString(", ")}" else "")
                    }
                    Text("%.1f".format(st.score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Muted(SNORE_DISCLAIMER)
    }
}

@Composable
private fun StartCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    var delayIdx by rememberSaveable { mutableIntStateOf(2) }
    val delays = listOf(0, 15, 30)
    val charging = remember { SnoreService.isCharging(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) SnoreService.start(ctx, delays[delayIdx])
        else vm.message.value = "마이크 권한이 있어야 측정할 수 있습니다"
    }
    SectionCard("오늘 밤 측정") {
        Muted("잠들기 전 소리를 빼려면 시작을 늦추세요")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            delays.forEachIndexed { i, d ->
                SegmentedButton(delayIdx == i, { delayIdx = i }, SegmentedButtonDefaults.itemShape(i, delays.size)) {
                    Text(if (d == 0) "바로 시작" else "${d}분 뒤")
                }
            }
        }
        if (!charging) Text("충전기가 연결되어 있지 않습니다. 밤새 측정하려면 충전을 권합니다.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            launcher.launch(buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }, modifier = Modifier.fillMaxWidth()) { Text("측정 시작") }
        Muted("휴대폰은 머리맡 50cm 이내에, 마이크가 가려지지 않게 두세요. 화면은 꺼도 됩니다. " +
            "아침에 잠금을 풀면(측정 3시간 이후) 자동으로 끝나고, 알림의 ‘측정 종료’로도 끝낼 수 있습니다. " +
            "전체 녹음은 저장하지 않고 코골이 샘플 10초짜리만 최대 20개 남깁니다.")
    }
}

@Composable
private fun RunningCard(s: SnoreSession, found: Int) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.id) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    SectionCard("측정 중") {
        Row(Modifier.fillMaxWidth()) {
            Stat("시작", clock(s.startAt), Modifier.weight(1f))
            Stat("경과", dur(((now - s.startAt) / 60_000).toInt()), Modifier.weight(1f))
            Stat("감지된 구간", "${found}개", Modifier.weight(1f))
        }
        if (now < s.measureFrom) Muted("${clock(s.measureFrom)}부터 감지를 시작합니다")
        OutlinedButton(onClick = { SnoreService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("측정 종료") }
    }
}

@Composable
private fun ReportCard(s: SnoreSession, events: List<SnoreEvent>, vm: AppViewModel) {
    val st = snoreStats(s, events)
    val mine = events.filter { it.sessionId == s.id && it.label != 0 }
    val player = rememberClipPlayer()
    SectionCard("${dayShort(s.day)} 아침 리포트", trailing = { Muted("${clock(s.startAt)}–${clock(s.endAt)}") }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Muted("Snore Score")
                Text("%.1f".format(st.score), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
            Stat("코골이 시간", durSec(st.snoreSec), Modifier.weight(1.2f), "측정 ${durSec(s.measuredSec)}의 ${"%.0f".format(st.pct)}%")
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("가장 큰 소리", st.peak?.let { "+${it.peakDb.roundToInt()}dB" } ?: "—", Modifier.weight(1f),
                st.peak?.let { "${clock(it.startAt)} · 방 소음 대비" })
            Stat("감지 구간", "${st.count}개", Modifier.weight(1.2f), if (s.charging) "충전 중 측정" else "충전 없이 측정")
        }
        if (s.interrupted) Text("측정이 ${clock(s.endAt)}에 중단됐습니다. 배터리 설정에서 ‘나의 건강’을 ‘제한 없음’으로 두면 중단을 줄일 수 있습니다.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (s.apneaLike >= 5) Text("숨이 멈춘 듯한 구간이 반복됐습니다(${s.apneaLike}회). 이 앱은 진단 도구가 아니므로 반복되면 수면 검사 상담을 권합니다.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)

        HorizontalDivider()
        Text("타임라인", style = MaterialTheme.typography.labelLarge)
        if (mine.isEmpty()) Muted("코골이로 확정된 구간이 없습니다") else {
            SnoreTimeline(s, mine, player.playingId, Modifier.fillMaxWidth().height(110.dp)) { e -> player.toggle(e) }
            Muted("막대 높이 = 강도(조용·보통·큼). 막대를 누르면 그 구간 샘플을 재생합니다.")
        }

        HorizontalDivider()
        Text("전날 밤 태그", style = MaterialTheme.typography.labelLarge)
        SNORE_TAGS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag -> FilterChip(tag in s.tagList, { vm.toggleSnoreTag(s, tag) }, { Text(tag) }) }
            }
        }
        TextButton(onClick = { vm.deleteSnore(s) }) { Text("이 밤 기록 삭제", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SamplesCard(s: SnoreSession, events: List<SnoreEvent>, vm: AppViewModel) {
    val clips = events.filter { it.sessionId == s.id && it.clipPath != null }
        .sortedWith(compareByDescending<SnoreEvent> { it.intensity }.thenByDescending { it.meanDb })
    if (clips.isEmpty()) return
    val player = rememberClipPlayer()
    SectionCard("샘플 (${clips.size}개, 강한 순)") {
        Muted("들어 보고 ‘맞음/아님’을 눌러 주세요. ‘아님’은 점수에서 빠지고, 20개 이상 쌓이면 판별 기준이 자동 조정됩니다.")
        clips.forEach { e ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { player.toggle(e) }) { Text(if (player.playingId == e.id) "■" else "▶") }
                Column(Modifier.weight(1f)) {
                    Text("${clock(e.startAt)} · ${durSec(e.durSec)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${intensityName(e.intensity)} +${e.meanDb.roundToInt()}dB", style = MaterialTheme.typography.bodySmall, color = intensityColor(e.intensity))
                }
                FilterChip(e.label == 1, { vm.setSnoreLabel(e, if (e.label == 1) null else 1) }, { Text("맞음") })
                Box(Modifier.width(6.dp))
                FilterChip(e.label == 0, { vm.setSnoreLabel(e, if (e.label == 0) null else 0) }, { Text("아님") })
            }
        }
    }
}

/** 태그 있는 밤 vs 없는 밤의 평균 Snore Score */
@Composable
private fun CompareCard(done: List<SnoreSession>, events: List<SnoreEvent>) {
    val scores = done.associateWith { snoreStats(it, events).score }
    val max = maxOf(1.0, scores.values.max())
    SectionCard("태그별 비교") {
        Muted("태그가 있는 밤과 없는 밤의 평균 Snore Score. 양쪽 모두 ${MIN_NIGHTS}밤 이상일 때만 비교합니다.")
        SNORE_TAGS.forEach { tag ->
            val with = scores.filterKeys { tag in it.tagList }.values
            val without = scores.filterKeys { tag !in it.tagList }.values
            HorizontalDivider()
            Text(tag, style = MaterialTheme.typography.labelLarge)
            if (with.size < MIN_NIGHTS || without.size < MIN_NIGHTS) Muted("표본 부족 (있음 ${with.size}밤 · 없음 ${without.size}밤)")
            else {
                ScoreBar("있음 ${with.size}밤", with.average(), max, MaterialTheme.colorScheme.tertiary)
                ScoreBar("없음 ${without.size}밤", without.average(), max, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, v: Double, max: Double, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Muted(label, Modifier.width(84.dp))
        Canvas(Modifier.weight(1f).height(14.dp)) {
            drawRoundRect(color, size = Size(maxOf(4f, (v / max).toFloat() * size.width), size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        Text("%.1f".format(v), Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 가로축 = 측정 시간, 세로 = 강도 3단계 막대 */
@Composable
private fun SnoreTimeline(s: SnoreSession, events: List<SnoreEvent>, playingId: Long?, modifier: Modifier, onTap: (SnoreEvent) -> Unit) {
    val tm = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.error)
    val t0 = s.measureFrom
    val span = maxOf(60_000L, s.endAt - t0)
    Canvas(modifier.pointerInput(events) {
        detectTapGestures { off ->
            val t = t0 + (off.x / size.width * span).toLong()
            events.minByOrNull { abs((it.startAt + it.endAt) / 2 - t) }?.let(onTap)
        }
    }) {
        val b = 16.dp.toPx(); val h = size.height - b
        drawLine(cs.outlineVariant, Offset(0f, h), Offset(size.width, h), 2f)
        events.forEach { e ->
            val x0 = (e.startAt - t0).toFloat() / span * size.width
            val w = maxOf(3f, (e.endAt - e.startAt).toFloat() / span * size.width)
            val bh = h * e.intensity / 3f
            drawRect(colors[e.intensity - 1].copy(alpha = if (e.id == playingId) 1f else 0.75f), Offset(x0, h - bh), Size(w, bh))
        }
        val style = TextStyle(color = cs.onSurfaceVariant, fontSize = 10.sp)
        val a = tm.measure(clock(t0), style); val z = tm.measure(clock(s.endAt), style)
        drawText(a, topLeft = Offset(0f, h + 2.dp.toPx()))
        drawText(z, topLeft = Offset(size.width - z.size.width, h + 2.dp.toPx()))
    }
}

class ClipPlayer {
    var playingId by mutableStateOf<Long?>(null); private set
    private var mp: MediaPlayer? = null

    fun toggle(e: SnoreEvent) {
        val same = playingId == e.id
        stop()
        val path = e.clipPath ?: return
        if (same) return
        runCatching {
            mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { this@ClipPlayer.stop() }
                prepare(); start()
            }
            playingId = e.id
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { mp?.release() }
        mp = null; playingId = null
    }
}

@Composable
private fun rememberClipPlayer(): ClipPlayer {
    val p = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { p.stop() } }
    return p
}
