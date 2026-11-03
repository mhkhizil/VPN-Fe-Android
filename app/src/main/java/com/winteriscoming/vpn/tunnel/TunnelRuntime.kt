// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.winteriscoming.vpn.MainActivity
import com.winteriscoming.vpn.api.PeerConfig
import com.winteriscoming.vpn.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-side tunnel plumbing: the consent dialog, the foreground notification and
 * the controller the UI talks to.
 *
 * Pulled into one file because none of it can be tested off-device; keeping it
 * adjacent makes it obvious which code is "trust me, it compiles" and which code has
 * unit tests.
 */

/** Returns the consent intent, or null when the user has already granted VPN access. */
object VpnConsent {
    fun prepare(context: Context): Intent? = VpnService.prepare(context)
}

/** The ongoing notification a foreground VpnService is required to show. */
object TunnelNotification {
    const val CHANNEL_ID = "vpn-tunnel"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN tunnel",
            NotificationManager.IMPORTANCE_LOW, // no sound: this is a status indicator
        ).apply { description = "Shown while the VPN tunnel is connected" }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Winter VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Connects the onboarding result to the tunnel.
 *
 * The private key is fetched from the repository at connect time rather than carried
 * around in UI state, so it never sits in a composable's memory or a saved bundle.
 */
class TunnelController(
    private val repository: OnboardingRepository,
    private val engine: TunnelEngine,
) {
    val state: StateFlow<TunnelState> = engine.state

    /** Returns null on success, or a user-facing message on failure. */
    suspend fun connect(config: PeerConfig): String? {
        val privateKey = repository.storedPrivateKey()
            ?: return "Set up the VPN first: no key is stored on this device."
        engine.connect(config, privateKey)
        return when (val current = engine.state.value) {
            is TunnelState.Failed -> current.message
            else -> null
        }
    }

    suspend fun disconnect() = engine.disconnect()
}
