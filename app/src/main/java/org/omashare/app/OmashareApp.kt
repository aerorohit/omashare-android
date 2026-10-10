package org.omashare.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OmashareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("omashare_ffi")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TRANSFER,
                    "Sharing",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OFFERS,
                    "Incoming files",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RESULTS,
                    "Transfer results",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    companion object {
        const val CHANNEL_TRANSFER = "omashare.transfer"
        const val CHANNEL_OFFERS = "omashare.offers"
        const val CHANNEL_RESULTS = "omashare.results"
    }
}
