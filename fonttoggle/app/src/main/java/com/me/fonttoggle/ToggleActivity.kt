package com.me.fonttoggle

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** 화면 없이 토글만 하고 바로 종료. 권한이 없으면 설정 화면을 연다. */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FontScaler.canWrite(this)) {
            FontScaler.toggle(this)
        } else {
            Toast.makeText(this, "먼저 '시스템 설정 변경' 권한을 허용해 주세요", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
