package org.omashare.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import org.omashare.app.R

class ShareTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        if (ShareStore.state.value.sharingOn) {
            showState(sharing = false)
            runCatching { ShareService.stop(this) }
                .onFailure {
                    Toast.makeText(
                        applicationContext,
                        R.string.qs_tile_stop_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshTile()
                }
            return
        }
        val start = Runnable { startSharing() }
        if (isLocked) {
            unlockAndRun(start)
        } else {
            start.run()
        }
    }

    private fun startSharing() {
        runCatching { ShareService.start(this) }
            .onFailure {
                Toast.makeText(
                    applicationContext,
                    R.string.qs_tile_start_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
    }

    private fun refreshTile() {
        showState(ShareStore.state.value.sharingOn)
    }

    private fun showState(sharing: Boolean) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_tile_label)
        tile.state = if (sharing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_share)
        val subtitle = getString(
            if (sharing) R.string.qs_tile_subtitle_on else R.string.qs_tile_subtitle_off,
        )
        tile.contentDescription = getString(R.string.qs_tile_content_description, subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }
}
