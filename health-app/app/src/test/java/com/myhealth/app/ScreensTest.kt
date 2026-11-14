package com.myhealth.app

import android.app.AppOpsManager
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.SRC_AUTO
import com.myhealth.app.data.SRC_MANUAL
import com.myhealth.app.data.SleepRec
import com.myhealth.app.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** 실제 MainActivity를 띄워 탭마다 화면을 그려 보고 PNG로 남긴다. 그리는 중 예외가 나면 실패한다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = System.getProperty("shots.dir")

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/$name.png")
    }

    private fun seedSleep() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 기획안 예시와 같은 7일(가상)
        val rows = listOf("00:40" to "07:10", "01:10" to "07:05", "00:20" to "07:15", "01:30" to "07:00",
            "01:20" to "07:05", "02:30" to "10:30", "01:00" to "09:00")
        rows.forEachIndexed { i, (b, w) ->
            val d = today.minusDays((6 - i).toLong())
            fun ms(t: String) = d.atTime(t.substring(0, 2).toInt(), t.substring(3).toInt()).atZone(zone).toInstant().toEpochMilli()
            AppDb.get(ctx).sleep().put(SleepRec(d.toEpochDay(), ms(b), ms(w), if (i == 3) SRC_MANUAL else SRC_AUTO, ms(b), ms(w)))
        }
    }

    @Test fun allTabsRender() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("98.0", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("1-today-no-permission")

        shadowOf(ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName, AppOpsManager.MODE_ALLOWED)
        seedSleep()
        rule.activityRule.scenario.onActivity { }
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithText("오늘").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("7일 평균", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("2-today")

        rule.onNodeWithText("수면").performClick(); shot("3-sleep-log")
        rule.onNodeWithText("분석").performClick(); shot("4-analysis")
        rule.onNodeWithText("체중").performClick(); shot("5-weight")
        rule.onNodeWithText("장기 기록 (삼성 헬스 캡처)").performScrollTo(); shot("6-weight-archive")
        rule.onNodeWithText("설정").performClick(); shot("8-settings")
    }
}
