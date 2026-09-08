package org.omashare.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class OfferActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val session = intent.getStringExtra(ShareService.EXTRA_SESSION) ?: return
        val service = Intent(context, ShareService::class.java).apply {
            this.action = when (action) {
                ACTION_ACCEPT -> ShareService.ACTION_ACCEPT
                ACTION_DECLINE -> ShareService.ACTION_DECLINE
                else -> return
            }
            putExtra(ShareService.EXTRA_SESSION, session)
        }
        ContextCompat.startForegroundService(context, service)
    }

    companion object {
        const val ACTION_ACCEPT = "org.omashare.app.ACCEPT_OFFER"
        const val ACTION_DECLINE = "org.omashare.app.DECLINE_OFFER"
    }
}
