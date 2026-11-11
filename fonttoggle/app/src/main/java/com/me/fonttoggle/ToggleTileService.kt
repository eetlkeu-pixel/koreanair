package com.me.fonttoggle

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.app.PendingIntent

/** 빠른 설정 패널 타일. 켜짐 = 큰 글자. */
class ToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (FontScaler.canWrite(this)) {
            FontScaler.toggle(this)
            render()
        } else {
            openSettings()
        }
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val large = FontScaler.isLarge(this)
        tile.state = if (large) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (large) "크게 ${FontScaler.fmt(FontScaler.current(this))}"
                            else "작게 ${FontScaler.fmt(FontScaler.current(this))}"
        }
        tile.updateTile()
    }
}
