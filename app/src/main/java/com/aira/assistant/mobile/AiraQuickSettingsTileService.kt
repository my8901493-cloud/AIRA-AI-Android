package com.aira.assistant.mobile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aira.assistant.MainActivity
import com.aira.assistant.core.AiraLogger

/**
 * Android Quick Settings Shade Tile (`AiraQuickSettingsTileService`).
 * Allows the user to pull down the notification shade from any screen and launch AIRA Live Operator in 1 tap.
 */
class AiraQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (AiraFloatingOverlayService.isOverlayVisible.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "AIRA Operator"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
        AiraLogger.i("QuickSettingsTile", "User launched AIRA Operator via Quick Settings Tile.")
    }
}
