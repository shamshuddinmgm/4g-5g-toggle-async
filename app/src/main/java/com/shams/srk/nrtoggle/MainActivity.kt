package com.shams.srk.nrtoggle

import android.app.StatusBarManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.shams.srk.nrtoggle.network.NetworkMode
import com.shams.srk.nrtoggle.network.NetworkModeController
import com.shams.srk.nrtoggle.shizuku.ShizukuShell
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread {
                permissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                refreshUiState()
            }
        }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshUiState() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshUiState() }
    }

    private var permissionGranted by mutableStateOf(false)
    private var shizukuRunning by mutableStateOf(false)
    private var currentMode by mutableStateOf<NetworkMode?>(null)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        refreshUiState()

        setContent {
            NrToggleTheme {
                SetupScreen(
                    shizukuRunning = shizukuRunning,
                    permissionGranted = permissionGranted,
                    currentMode = currentMode,
                    busy = busy,
                    onOpenShizuku = { openShizuku() },
                    onRequestPermission = { requestShizukuPermission() },
                    onAddTile = { requestAddTile() },
                    onToggle = { runToggle() },
                    onRefresh = { refreshUiState() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun refreshUiState() {
        shizukuRunning = ShizukuShell.isRunning()
        permissionGranted = ShizukuShell.hasPermission()
        // Settings.Global only on UI path — no shell on every resume (battery).
        currentMode = NetworkModeController.readMode(this, allowShellFallback = false)
            ?: NetworkModeController.lastKnownMode()
        nudgeTile()
    }

    private fun runToggle() {
        if (busy || NetworkModeController.isBusy()) {
            Toast.makeText(this, R.string.toggle_busy, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ShizukuShell.isReady()) {
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        // Optimistic status line
        val guess = currentMode ?: NetworkMode.NR
        currentMode = if (guess == NetworkMode.NR) NetworkMode.LTE else NetworkMode.NR
        AppExecutors.io.execute {
            val result = NetworkModeController.toggle(this@MainActivity)
            runOnUiThread {
                busy = false
                when (result) {
                    is NetworkModeController.ToggleResult.Ok -> {
                        currentMode = result.verified ?: result.target
                        val label =
                            if ((result.verified ?: result.target) == NetworkMode.NR) {
                                getString(R.string.mode_5g)
                            } else {
                                getString(R.string.mode_4g)
                            }
                        Toast.makeText(this, getString(R.string.toggle_done, label), Toast.LENGTH_SHORT)
                            .show()
                    }
                    NetworkModeController.ToggleResult.Busy -> {
                        currentMode = guess
                        Toast.makeText(this, R.string.toggle_busy, Toast.LENGTH_SHORT).show()
                    }
                    NetworkModeController.ToggleResult.NeedShizuku -> {
                        currentMode = guess
                        Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_SHORT).show()
                    }
                    is NetworkModeController.ToggleResult.Failed -> {
                        currentMode = NetworkModeController.readMode(this, allowShellFallback = false)
                            ?: guess
                        Toast.makeText(
                            this,
                            getString(R.string.toggle_failed, result.reason),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                nudgeTile()
            }
        }
    }

    private fun nudgeTile() {
        try {
            TileService.requestListeningState(this, NetworkTileService.component(this))
        } catch (_: Throwable) {
        }
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.shizuku_install, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuPermission() {
        if (!ShizukuShell.isRunning()) {
            Toast.makeText(this, R.string.shizuku_start, Toast.LENGTH_SHORT).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshUiState()
            return
        }
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
            return
        }
        val statusBar = getSystemService<StatusBarManager>() ?: return
        statusBar.requestAddTileService(
            NetworkTileService.component(this),
            getString(R.string.tile_label),
            TileIcons.fiveG(this),
            mainExecutor
        ) { _ -> }
    }

    companion object {
        private const val REQUEST_CODE_SHIZUKU = 1001
    }
}

private val Signal = Color(0xFF0E6B8A)
private val Ink = Color(0xFF0C1C24)
private val Mist = Color(0xFFE6F1F5)
private val Deep = Color(0xFF083447)

@Composable
private fun NrToggleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Signal,
            onPrimary = Color.White,
            background = Mist,
            onBackground = Ink,
            surface = Color.White.copy(alpha = 0.55f),
            onSurface = Ink
        ),
        content = content
    )
}

@Composable
private fun SetupScreen(
    shizukuRunning: Boolean,
    permissionGranted: Boolean,
    currentMode: NetworkMode?,
    busy: Boolean,
    onOpenShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onAddTile: () -> Unit,
    onToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    val shizukuLine = when {
        !shizukuRunning -> stringResource(R.string.status_shizuku_off)
        !permissionGranted -> stringResource(R.string.status_shizuku_need_perm)
        else -> stringResource(R.string.status_shizuku_ready)
    }
    val modeLine = when (currentMode) {
        NetworkMode.NR -> stringResource(R.string.status_mode, stringResource(R.string.mode_5g))
        NetworkMode.LTE -> stringResource(R.string.status_mode, stringResource(R.string.mode_4g))
        null -> stringResource(R.string.status_mode_unknown)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Mist, Color(0xFFD5E8EF), Mist)
                )
            )
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = Deep,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = Ink.copy(alpha = 0.75f),
            fontSize = 15.sp
        )

        StatusCard(
            lines = listOf(shizukuLine, modeLine)
        )

        Button(
            onClick = onToggle,
            enabled = shizukuRunning && permissionGranted && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Signal)
        ) {
            Text(if (busy) stringResource(R.string.tile_switching) else stringResource(R.string.action_toggle))
        }

        OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_open_shizuku))
        }
        OutlinedButton(
            onClick = onRequestPermission,
            enabled = shizukuRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_grant_shizuku))
        }
        OutlinedButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_tile))
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_refresh))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_hint),
            color = Ink.copy(alpha = 0.65f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatusCard(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            Text(text = line, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
