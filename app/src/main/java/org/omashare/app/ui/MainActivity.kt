package org.omashare.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.omashare.app.BuildConfig
import androidx.core.content.ContextCompat
import org.omashare.app.service.ShareService
import org.omashare.app.service.ShareStore
import org.omashare.app.share.FileOpener
import org.omashare.app.share.FileStager
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "omashare-stage") }
    private val handler = Handler(Looper.getMainLooper())
    private val pollPeers = object : Runnable {
        override fun run() {
            ShareService.instance?.refreshPeers()
            handler.postDelayed(this, 2_000)
        }
    }

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        ShareStore.setStatus("Copying files into the app…")
        io.execute { FileStager.stage(this, uris) }
    }

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startSharing() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val state by ShareStore.state.collectAsState()
            var appTheme by remember { mutableStateOf(ThemePreferences.load(this)) }
            var showSettings by remember { mutableStateOf(false) }
            OmashareTheme(theme = appTheme) {
                if (showSettings) {
                    SettingsScreen(
                        currentTheme = appTheme,
                        versionName = BuildConfig.VERSION_NAME,
                        onThemeSelected = { theme ->
                            appTheme = theme
                            ThemePreferences.save(this, theme)
                        },
                        onBack = { showSettings = false },
                    )
                } else {
                    MainScreen(
                        state = state,
                        onSelectFiles = { pickFiles.launch(arrayOf("*/*")) },
                        onClearSelection = { ShareStore.clearStaged() },
                        onPeerSelected = { peer -> ShareStore.setSelectedPeer(peer.endpointId) },
                        onSend = { sendToSelected() },
                        onStopSharing = { ShareService.stop(this) },
                        onStartSharing = { startSharing() },
                        onAccept = { sid ->
                            ShareStore.setIncoming(null)
                            ShareService.accept(this, sid)
                        },
                        onDecline = { sid ->
                            ShareStore.setIncoming(null)
                            ShareService.decline(this, sid)
                        },
                        onCancel = {
                            ShareService.cancel(this, ShareStore.state.value.transfer?.sessionId)
                        },
                        onOpenHistoryEntry = { entry -> FileOpener.open(this, entry) },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
        ensurePermsThenStart()
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollPeers)
    }

    override fun onPause() {
        handler.removeCallbacks(pollPeers)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun sendToSelected() {
        val state = ShareStore.state.value
        val peer = state.peers.find { it.endpointId == state.selectedPeerId }
        if (peer == null) {
            ShareStore.setStatus("Select a device first")
            return
        }
        val staged = state.staged
        if (staged.isEmpty()) {
            ShareStore.setStatus("Select files first")
            return
        }
        if (state.transfer != null) {
            ShareStore.setStatus("Already transferring")
            return
        }
        ShareService.send(this, peer.endpointId, staged.map { it.path })
    }

    private fun handleShareIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    ShareStore.setShareSheet(true)
                    ShareStore.setStatus("Copying files into the app…")
                    io.execute { FileStager.stage(this, listOf(uri)) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    ShareStore.setShareSheet(true)
                    ShareStore.setStatus("Copying files into the app…")
                    io.execute { FileStager.stage(this, uris) }
                }
            }
        }
        val metered = intent?.getStringExtra("metered_session")
        if (metered != null) {
            io.execute { ShareService.node()?.consentMetered(metered, true) }
        }
    }

    private fun ensurePermsThenStart() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startSharing()
        } else {
            ShareStore.setStatus("Grant nearby / notification permissions")
            requestPerms.launch(missing.toTypedArray())
        }
    }

    private fun startSharing() {
        runCatching { ShareService.start(this) }
            .onFailure { ShareStore.setStatus("Service start failed: ${it.message}") }
    }

    private fun requiredPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return needed
    }
}
