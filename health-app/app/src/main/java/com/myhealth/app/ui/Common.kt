package com.myhealth.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myhealth.app.data.DEV_INBODY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val Light = lightColorScheme(
    primary = Color(0xFF2F5D9E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5F7), onPrimaryContainer = Color(0xFF0E2A52),
    secondary = Color(0xFF1F8A70), secondaryContainer = Color(0xFFD3F0E7), onSecondaryContainer = Color(0xFF07382C),
    tertiary = Color(0xFFB26A00),
    background = Color(0xFFF6F7FA), surface = Color(0xFFF6F7FA),
    surfaceVariant = Color(0xFFE6E9F0), surfaceContainerLow = Color.White,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9EC1F5), secondary = Color(0xFF7FE0C3), tertiary = Color(0xFFFFC56B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun SectionCard(title: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                trailing?.invoke(this)
            }
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ───── 표시 형식 ─────
private val DOW = listOf("월", "화", "수", "목", "금", "토", "일")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

fun zone(): ZoneId = ZoneId.systemDefault()
fun today(): Long = LocalDate.now(zone()).toEpochDay()
fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(HM)
fun minClock(min: Double): String = minClock(min.roundToInt())
fun minClock(min: Int): String { val m = Math.floorMod(min, 1440); return "%02d:%02d".format(m / 60, m % 60) }
fun dur(min: Int): String { val a = abs(min); return (if (min < 0) "−" else "") + if (a >= 60) "${a / 60}시간 ${a % 60}분" else "${a}분" }
fun dur(min: Double): String = dur(min.roundToInt())
fun signedMin(min: Int): String = (if (min >= 0) "+" else "−") + dur(abs(min))
fun dayShort(day: Long): String { val d = LocalDate.ofEpochDay(day); return "${d.monthValue}/${d.dayOfMonth} (${DOW[d.dayOfWeek.value - 1]})" }
fun dayMd(day: Long): String { val d = LocalDate.ofEpochDay(day); return "%02d-%02d".format(d.monthValue, d.dayOfMonth) }
fun dayIso(day: Long): String = LocalDate.ofEpochDay(day).toString()
fun kg(v: Double): String = "%.1f".format(v)
fun deviceName(d: String): String = if (d == DEV_INBODY) "인바디" else "집 체중계"
