// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel

/**
 * The VPN service the system binds to when the user approves the tunnel.
 *
 * It must extend `GoBackend.VpnService`: that class *is* the library's `Tunnel`
 * handle (the thing you pass to `GoBackend.setState`), and it is the documented
 * embedding path. The library then owns the packet loop, key rotation, timers and
 * `Builder.protect()` for our own sockets, which is exactly what we are buying by
 * not writing a client-side WireGuard (ADR-0012).
 *
 * Note: the exact member names below come from the AAR's javadoc; the first CI
 * build is what confirms them, since this machine has no Android SDK.
 */
class WgTunnelService : GoBackend.VpnService(), Tunnel {

    // The backend needs a Tunnel handle: the documented embedding path is to
    // implement this ourselves and hand `this` to GoBackend.setState. GoBackend.VpnService
    // is only the VpnService base (it does not implement Tunnel), which the first CI
    // compile told us in as many words.
    override fun getName(): String = "winter-vpn"

    override fun onStateChange(newState: Tunnel.State) {
        TunnelNotification.ensureChannel(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(
            TunnelNotification.NOTIFICATION_ID,
            TunnelNotification.build(
                this,
                if (newState == Tunnel.State.UP) "Tunnel active" else "Tunnel down",
            ),
        )
        if (newState == Tunnel.State.DOWN) {
            // No tunnel, no reason to keep a foreground service (and its notification).
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        /** Set while the service is alive; [WireguardTunnelEngine] needs the handle. */
        @Volatile
        var instance: WgTunnelService? = null
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        // A VpnService that stays up must be a foreground service with a visible
        // notification; on API 34+ the type comes from the manifest's
        // android:foregroundServiceType="specialUse".
        TunnelNotification.ensureChannel(this)
        startForeground(
            TunnelNotification.NOTIFICATION_ID,
            TunnelNotification.build(this, "Tunnel active"),
        )
        // START_STICKY: if the process is killed while the tunnel is up, the system
        // recreates the service and the library re-establishes from the last config.
        return START_STICKY
    }

    override fun onRevoke() {
        // The user turned the VPN off from system settings, or another VPN took over.
        instance = null
        super.onRevoke()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
