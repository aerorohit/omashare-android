package org.omashare.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.omashare.app.service.ShareService
import org.omashare.app.service.ShareStore

class IncomingOfferActivity : ComponentActivity() {
    private var responded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOverLockScreen()
        if (!publishOffer(intent)) {
            finish()
            return
        }
        setContent {
            val state by ShareStore.state.collectAsState()
            val offer = state.incoming
            // Set before OfferSheet is removed so its dismiss path cannot decline.
            if (offer == null) responded = true
            val theme = remember { ThemePreferences.load(this@IncomingOfferActivity) }
            LaunchedEffect(offer) {
                if (offer == null) finish()
            }
            OmashareTheme(theme = theme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MochaBackground),
                ) {
                    if (offer != null) {
                        OfferSheet(
                            offer = offer,
                            onAccept = { respond(offer.sessionId, accept = true) },
                            onDecline = { respond(offer.sessionId, accept = false) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishOffer(intent)
    }

    private fun respond(sessionId: String, accept: Boolean) {
        if (responded) return
        responded = true
        if (accept) {
            ShareService.accept(this, sessionId)
        } else {
            ShareService.decline(this, sessionId)
        }
        finish()
    }

    private fun publishOffer(source: Intent?): Boolean {
        val offer = readOffer(source) ?: return false
        ShareStore.setIncoming(offer)
        return ShareStore.state.value.incoming?.sessionId == offer.sessionId
    }

    private fun readOffer(source: Intent?): ShareStore.IncomingOffer? {
        val session = source?.getStringExtra(ShareService.EXTRA_SESSION)?.takeIf { it.isNotEmpty() }
            ?: return null
        val alias = source.getStringExtra(ShareService.EXTRA_ALIAS).orEmpty()
        val names = source.getStringArrayListExtra(ShareService.EXTRA_FILE_NAMES).orEmpty()
        val sizes = source.getLongArrayExtra(ShareService.EXTRA_FILE_SIZES) ?: LongArray(0)
        val files = names.mapIndexed { index, name ->
            ShareStore.OfferedFile(name = name, size = sizes.getOrNull(index) ?: 0L)
        }
        return ShareStore.IncomingOffer(session, alias, files)
    }

    private fun applyOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            return
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }
}
