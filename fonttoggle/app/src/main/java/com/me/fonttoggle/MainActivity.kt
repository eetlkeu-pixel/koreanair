package com.me.fonttoggle

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** 설정 화면: 권한 허용, 큰/작은 크기 맞추기. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var permBtn: Button
    private lateinit var tgCheck: android.widget.CheckBox
    private lateinit var tgStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        permBtn = findViewById(R.id.btn_perm)

        permBtn.setOnClickListener { startActivity(FontScaler.permissionIntent(this)) }

        findViewById<Button>(R.id.btn_save_large).setOnClickListener {
            FontScaler.saveLarge(this, FontScaler.current(this)); refresh()
        }
        findViewById<Button>(R.id.btn_save_small).setOnClickListener {
            FontScaler.saveSmall(this, FontScaler.current(this)); refresh()
        }
        findViewById<Button>(R.id.btn_large_minus).setOnClickListener { nudgeLarge(-0.1f) }
        findViewById<Button>(R.id.btn_large_plus).setOnClickListener { nudgeLarge(+0.1f) }
        findViewById<Button>(R.id.btn_small_minus).setOnClickListener { nudgeSmall(-0.1f) }
        findViewById<Button>(R.id.btn_small_plus).setOnClickListener { nudgeSmall(+0.1f) }
        tgCheck = findViewById(R.id.tg_check)
        tgStatus = findViewById(R.id.tg_status)
        tgCheck.setOnCheckedChangeListener { _, on -> FontScaler.setTgEnabled(this, on); refresh() }
        findViewById<Button>(R.id.btn_a11y).setOnClickListener {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_appinfo).setOnClickListener {
            startActivity(android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_tg_large_minus).setOnClickListener { FontScaler.saveTgLarge(this, FontScaler.tgLarge(this) - 1); refresh() }
        findViewById<Button>(R.id.btn_tg_large_plus).setOnClickListener { FontScaler.saveTgLarge(this, FontScaler.tgLarge(this) + 1); refresh() }
        findViewById<Button>(R.id.btn_tg_small_minus).setOnClickListener { FontScaler.saveTgSmall(this, FontScaler.tgSmall(this) - 1); refresh() }
        findViewById<Button>(R.id.btn_tg_small_plus).setOnClickListener { FontScaler.saveTgSmall(this, FontScaler.tgSmall(this) + 1); refresh() }

        findViewById<Button>(R.id.btn_toggle).setOnClickListener {
            FontScaler.toggle(this); refresh()
        }
    }

    private fun nudgeLarge(d: Float) {
        FontScaler.saveLarge(this, (FontScaler.large(this) + d).coerceIn(0.5f, 2.0f)); refresh()
    }

    private fun nudgeSmall(d: Float) {
        FontScaler.saveSmall(this, (FontScaler.small(this) + d).coerceIn(0.5f, 2.0f)); refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ok = FontScaler.canWrite(this)
        permBtn.text = if (ok) "✅ 권한 허용됨" else "① 시스템 설정 변경 권한 허용하기"
        permBtn.isEnabled = !ok
        status.text = buildString {
            append("지금 글자 크기: ${FontScaler.fmt(FontScaler.current(this@MainActivity))}\n")
            append("큰 크기: ${FontScaler.fmt(FontScaler.large(this@MainActivity))}   ")
            append("작은 크기: ${FontScaler.fmt(FontScaler.small(this@MainActivity))}\n")
            append("마지막 적용: ${FontScaler.lastResult}")
        }
        tgCheck.isChecked = FontScaler.tgEnabled(this)
        val tg = TgSyncService.installedTelegram(this)
        tgStatus.text = buildString {
            append("텔레그램: ${if (tg != null) "설치됨" else "못 찾음"}\n")
            append("접근성(글자토글): ${if (TgSyncService.isRunning()) "✅ 켜짐" else "❌ 꺼짐"}\n")
            append("텔레그램 큰 크기: ${FontScaler.tgLarge(this@MainActivity)}   작은 크기: ${FontScaler.tgSmall(this@MainActivity)}  (12~30)")
        }
        FontScaler.refreshUi(this)
    }
}
