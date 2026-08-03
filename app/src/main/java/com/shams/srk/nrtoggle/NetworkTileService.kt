package com.shams.srk.nrtoggle

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.shams.srk.nrtoggle.network.NetworkMode
import com.shams.srk.nrtoggle.network.NetworkModeController
import com.shams.srk.nrtoggle.shizuku.ShizukuShell
import java.util.concurrent.Executors

class NetworkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val mode = if (!ShizukuShell.isReady()) {
            null
        } else {
            NetworkModeController.readMode(this, allowShellFallback = false)
                ?: NetworkModeController.lastKnownMode()
        }
        applyTile(mode, switching = false)
    }

    override fun onClick() {
        super.onClick()
        if (!ShizukuShell.isReady()) {
            toast(R.string.shizuku_required)
            startActivityAndCollapseCompat(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (NetworkModeController.isBusy()) {
            toast(R.string.toggle_busy)
            return
        }

        val current = NetworkModeController.readMode(this, allowShellFallback = false)
            ?: NetworkModeController.lastKnownMode()
            ?: NetworkMode.NR
        val target = if (current == NetworkMode.NR) NetworkMode.LTE else NetworkMode.NR

        applyTile(target, switching = true)

        AppExecutors.io.execute {
            // setMode skips the extra current-mode shell/settings dance inside toggle().
            val result = NetworkModeController.setMode(applicationContext, target)
            AppExecutors.main.post {
                when (result) {
                    is NetworkModeController.ToggleResult.Ok -> {
                        applyTile(result.verified ?: result.target, switching = false)
                        toast(
                            getString(
                                R.string.toggle_done,
                                modeLabel(result.verified ?: result.target)
                            )
                        )
                    }
                    NetworkModeController.ToggleResult.Busy -> {
                        applyTile(current, switching = false)
                        toast(R.string.toggle_busy)
                    }
                    NetworkModeController.ToggleResult.NeedShizuku -> {
                        applyTile(current, switching = false)
                        toast(R.string.shizuku_required)
                    }
                    is NetworkModeController.ToggleResult.Failed -> {
                        applyTile(
                            NetworkModeController.readMode(this, allowShellFallback = false)
                                ?: current,
                            switching = false
                        )
                        toast(getString(R.string.toggle_failed, result.reason), long = true)
                    }
                }
            }
        }
    }

    private fun applyTile(mode: NetworkMode?, switching: Boolean) {
        val tile = qsTile ?: return
        try {
            tile.icon = TileIcons.forMode(this, mode)
            when {
                !ShizukuShell.isReady() -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.tile_label)
                    tile.subtitle = getString(R.string.tile_need_shizuku)
                }
                switching -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = modeLabel(mode ?: NetworkMode.NR)
                    tile.subtitle = getString(R.string.tile_switching)
                }
                mode == NetworkMode.NR -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = getString(R.string.mode_5g)
                    tile.subtitle = getString(R.string.tile_tap_4g)
                }
                mode == NetworkMode.LTE -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.mode_4g)
                    tile.subtitle = getString(R.string.tile_tap_5g)
                }
                else -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.tile_label)
                    tile.subtitle = getString(R.string.tile_unknown)
                    tile.icon = TileIcons.fiveG(this)
                }
            }
            tile.updateTile()
        } catch (_: Throwable) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = getString(R.string.tile_error)
            tile.updateTile()
        }
    }

    private fun modeLabel(mode: NetworkMode): String =
        if (mode == NetworkMode.NR) getString(R.string.mode_5g) else getString(R.string.mode_4g)

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    private fun toast(msg: String, long: Boolean = false) {
        Toast.makeText(
            this,
            msg,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = android.app.PendingIntent.getActivity(
                this,
                REQUEST_MAIN,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val REQUEST_MAIN = 5201

        fun component(context: android.content.Context): ComponentName =
            ComponentName(context, NetworkTileService::class.java)
    }
}

internal object AppExecutors {
    val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "nrtoggle-io").apply { isDaemon = true }
    }
    val main = Handler(Looper.getMainLooper())
}
