package com.me.fonttoggle

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlin.math.abs

/** 글자 크기 토글 로직을 한 곳에 모아둔 객체. */
object FontScaler {
    private const val PREFS = "font_toggle"
    private const val KEY_LARGE = "large"
    private const val KEY_SMALL = "small"

    // 기본값: 앱 설정 화면에서 '지금 크기 저장'으로 실제 기기 값에 맞추는 걸 권장
    const val DEFAULT_LARGE = 1.3f
    const val DEFAULT_SMALL = 1.0f

    fun current(ctx: Context): Float =
        Settings.System.getFloat(ctx.contentResolver, Settings.System.FONT_SCALE, 1.0f)

    fun large(ctx: Context): Float =
        ctx.getSharedPreferences(PREFS, 0).getFloat(KEY_LARGE, DEFAULT_LARGE)

    fun small(ctx: Context): Float =
        ctx.getSharedPreferences(PREFS, 0).getFloat(KEY_SMALL, DEFAULT_SMALL)

    fun saveLarge(ctx: Context, v: Float) =
        ctx.getSharedPreferences(PREFS, 0).edit().putFloat(KEY_LARGE, v).apply()

    fun saveSmall(ctx: Context, v: Float) =
        ctx.getSharedPreferences(PREFS, 0).edit().putFloat(KEY_SMALL, v).apply()

    fun isLarge(ctx: Context): Boolean = abs(current(ctx) - large(ctx)) < 0.01f

    fun canWrite(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 지금이 큰 값이면 작은 값으로, 그 외에는 큰 값으로.
     * @return 성공 여부 (권한 없으면 false)
     */
    fun toggle(ctx: Context): Boolean {
        if (!canWrite(ctx)) return false
        val target = if (isLarge(ctx)) small(ctx) else large(ctx)
        return set(ctx, target)
    }

    fun set(ctx: Context, value: Float): Boolean {
        return try {
            val ok = Settings.System.putFloat(ctx.contentResolver, Settings.System.FONT_SCALE, value)
            if (ok) {
                val label = if (abs(value - large(ctx)) < 0.01f) "글자 크게" else "글자 작게"
                Toast.makeText(ctx.applicationContext, "$label (${fmt(value)})", Toast.LENGTH_SHORT).show()
                refreshUi(ctx)
            }
            ok
        } catch (e: SecurityException) {
            Toast.makeText(
                ctx.applicationContext,
                "이 기기에서 변경이 막혔어요. 설정 화면의 안내(ADB)를 참고하세요.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    fun refreshUi(ctx: Context) {
        TileService.requestListeningState(ctx, ComponentName(ctx, ToggleTileService::class.java))
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ToggleWidget::class.java))
        if (ids.isNotEmpty()) ToggleWidget.update(ctx, mgr, ids)
    }

    fun fmt(v: Float): String = String.format("%.2f", v).trimEnd('0').trimEnd('.')
}
