package org.omashare.app.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import org.omashare.app.R
import androidx.core.content.ContextCompat
import org.omashare.app.service.ShareService
import org.omashare.app.service.ShareTileService
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
                        onAddQuickSettingsTile = { requestAddQuickSettingsTile() },
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
                            val st = ShareStore.state.value
                            ShareService.cancel(
                                this,
                                st.transfer?.sessionId ?: st.incoming?.sessionId,
                            )
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
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ShareStore.setUiForeground(true)
        handler.post(pollPeers)
    }

    override fun onPause() {
        ShareStore.setUiForeground(false)
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
        if (intent != null &&
            (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)
        ) {
            val uris = collectShareUris(intent)
            if (uris.isNotEmpty()) {
                for (uri in uris) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                ShareStore.setShareSheet(true)
                ShareStore.setStatus("Copying files into the app…")
                io.execute { FileStager.stage(this, uris) }
            }
        }
        val metered = intent?.getStringExtra("metered_session")
        if (metered != null) {
            io.execute { ShareService.node()?.consentMetered(metered, true) }
        }
    }

    private fun collectShareUris(intent: Intent): List<Uri> {
        val fromStream = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamUri(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamUris(intent)
            else -> emptyList()
        }
        if (fromStream.isNotEmpty()) return fromStream
        val clip = intent.clipData ?: return emptyList()
        val out = mutableListOf<Uri>()
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i)?.uri ?: continue
            out.add(uri)
        }
        return out
    }

    private fun streamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun streamUris(intent: Intent): List<Uri> {
        val uris = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return uris?.filterNotNull().orEmpty()
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

    private fun requestAddQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestAddQuickSettingsTileApi33()
        }
    }

    @RequiresApi(33)
    private fun requestAddQuickSettingsTileApi33() {
        val manager = getSystemService(StatusBarManager::class.java) ?: run {
            Toast.makeText(this, R.string.qs_tile_add_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val component = ComponentName(this, ShareTileService::class.java)
        val icon = Icon.createWithResource(this, R.drawable.ic_qs_share)
        runCatching {
            manager.requestAddTileService(
                component,
                getString(R.string.qs_tile_label),
                icon,
                mainExecutor,
            ) { result ->
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        R.string.qs_tile_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        R.string.qs_tile_already_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        R.string.qs_tile_not_added
                    else -> R.string.qs_tile_add_failed
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, R.string.qs_tile_add_failed, Toast.LENGTH_SHORT).show()
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
