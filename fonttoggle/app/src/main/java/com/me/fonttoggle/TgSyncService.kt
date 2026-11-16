package com.me.fonttoggle

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 텔레그램 글자 크기 동기화.
 * 텔레그램은 시스템 글자 크기를 따르지 않으므로, tg://settings/themes(채팅 설정)를 열고
 * 맨 위 '메시지 글자 크기' 슬라이더(12~30)를 접근성 기능으로 옮긴 뒤 원래 화면으로 돌아간다.
 */
class TgSyncService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSize: Int? = null
    private var pendingUntil = 0L
    private var returnTo: String? = null
    private var setProgressTried = false
    private var lastPkg: String? = null
    private var lastActionAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pendingSize == null) {
            // 원래 있던 앱 기억 (상단바·이 앱의 투명 토글 화면은 제외)
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                pkg != "com.android.systemui" &&
                !(event.className?.toString()?.endsWith("ToggleActivity") ?: false)
            ) lastPkg = pkg
            return
        }
        if (pkg in TG_PACKAGES) tryApply()
    }

    private fun start(size: Int, tgPkg: String) {
        pendingSize = size
        pendingUntil = SystemClock.elapsedRealtime() + 10_000
        setProgressTried = false
        lastActionAt = 0L
        returnTo = lastPkg
        // 빠른 설정 타일에서 눌렀을 때 상단바를 닫아야 텔레그램 화면을 읽을 수 있음
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://settings/themes"))
            .setPackage(tgPkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            pendingSize = null
            Toast.makeText(this, "텔레그램을 열 수 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        poll()
    }

    /** 이벤트가 안 와도 시도하도록 0.3초마다 확인 */
    private fun poll() {
        handler.postDelayed({
            if (pendingSize == null) return@postDelayed
            if (SystemClock.elapsedRealtime() > pendingUntil) {
                pendingSize = null
                Toast.makeText(this, "텔레그램 글자 크기를 못 바꿨어요", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            tryApply()
            poll()
        }, 300)
    }

    private fun tryApply() {
        val target = pendingSize ?: return
        // 조작 직후엔 값이 반영될 시간을 줌
        if (SystemClock.elapsedRealtime() - lastActionAt < 500) return
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg !in TG_PACKAGES) return
        val bar = findSizeBar(root) ?: return
        bar.refresh()
        val now = bar.text?.toString()?.trim()?.toIntOrNull() ?: return
        if (now == target) {
            done()
            return
        }
        if (!setProgressTried) {
            // 1차: 슬라이더 값을 바로 지정
            setProgressTried = true
            val args = Bundle().apply {
                putFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    (target - MIN) / (MAX - MIN).toFloat()
                )
            }
            bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
            lastActionAt = SystemClock.elapsedRealtime()
            return // 다음 확인에서 값 검증
        }
        // 2차: 한 칸씩 이동 (한 번에 1pt)
        val action = if (target > now) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        repeat(minOf(kotlin.math.abs(target - now), MAX - MIN)) { bar.performAction(action) }
        lastActionAt = SystemClock.elapsedRealtime()
    }

    /** 화면 맨 위쪽의, 값이 12~30인 SeekBar = 메시지 글자 크기 */
    private fun findSizeBar(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40) return
            if (n.className?.toString() == "android.widget.SeekBar") {
                val v = n.text?.toString()?.trim()?.toIntOrNull()
                if (v != null && v in MIN..MAX) found += n
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val r = android.graphics.Rect()
        return found.minByOrNull { it.getBoundsInScreen(r); r.top }
    }

    private fun done() {
        pendingSize = null
        handler.postDelayed({ goBack() }, 350)
    }

    private fun goBack() {
        val back = returnTo
        when {
            back != null && back in TG_PACKAGES -> performGlobalAction(GLOBAL_ACTION_BACK)
            back == packageName -> startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            back == null || back == launcherPackage() -> performGlobalAction(GLOBAL_ACTION_HOME)
            else -> {
                val i = packageManager.getLaunchIntentForPackage(back)
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
    }

    companion object {
        const val MIN = 12
        const val MAX = 30
        val TG_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
        )

        @Volatile var instance: TgSyncService? = null

        fun isRunning(): Boolean = instance != null

        fun installedTelegram(ctx: Context): String? = TG_PACKAGES.firstOrNull {
            try { ctx.packageManager.getPackageInfo(it, 0); true } catch (e: Exception) { false }
        }

        /** 큰 글자로 바꿨으면 large=true */
        fun sync(ctx: Context, large: Boolean) {
            if (!BuildConfig.TG || !FontScaler.tgEnabled(ctx)) return
            val svc = instance
            if (svc == null) {
                Toast.makeText(ctx.applicationContext,
                    "텔레그램 동기화: 접근성에서 글자토글을 켜주세요", Toast.LENGTH_LONG).show()
                return
            }
            val pkg = installedTelegram(ctx) ?: return
            val size = if (large) FontScaler.tgLarge(ctx) else FontScaler.tgSmall(ctx)
            svc.handler.post { svc.start(size, pkg) }
        }
    }
}
