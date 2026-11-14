package com.myhealth.app

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myhealth.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 입력 대화상자가 열리고 닫히는지 확인.
 * Robolectric 기본 화면 크기를 쓴다: 화면 qualifier를 키우면 앱 코드와 무관하게
 * "AlertDialog + 텍스트 입력칸" 조합이 idle 상태가 되지 않는 테스트 환경 현상이 있다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DialogsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun settingsDialogs() {
        rule.onNodeWithText("설정").performClick()
        rule.onNodeWithText("탐색 구간 시작").performScrollTo().performClick()
        rule.onNodeWithText("저장").assertExists()
        rule.onNodeWithText("취소").performClick()
        rule.onNodeWithText("최소 수면 길이").performScrollTo().performClick()
        rule.onNodeWithText("저장").assertExists()
        rule.onNodeWithText("취소").performClick()
    }

    @Test fun weightDialog() {
        rule.onAllNodesWithText("체중").onLast().performClick()
        rule.onNodeWithText("＋ 기록 추가").performClick()
        rule.onNodeWithText("체중 기록 추가").assertExists()
        rule.onNodeWithText("인바디").performClick()
        rule.onNodeWithText("체지방량 (kg)").assertExists()
        rule.onNodeWithText("취소").performClick()
    }

    @Test fun sleepEditDialog() {
        rule.onNodeWithText("수면").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("기록 없음"))
        rule.onAllNodesWithText("기록 없음").onFirst().performClick()
        rule.onNodeWithText("취침 시각").assertExists()
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("수동", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("7시간 30분").onFirst().assertExists()
    }
}
