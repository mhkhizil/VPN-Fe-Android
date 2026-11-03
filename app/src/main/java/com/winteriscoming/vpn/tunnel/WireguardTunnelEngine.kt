// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import android.content.Context
import com.winteriscoming.vpn.api.PeerConfig
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The real tunnel, backed by the official WireGuard library (ADR-0012).
 *
 * This class is deliberately thin. Everything that can be reasoned about — the
 * configuration text — lives in [WireguardConfigBuilder] and is unit-tested on the
 * JVM. What is left here is the part that cannot be tested without a device: one
 * `setState` call and the state mapping around it.
 *
 * Errors are mapped to [TunnelState.Failed] with `renewalRequired = false`: by the
 * time we are connecting, entitlement has already been checked by the server, so a
 * failure here is a network or configuration problem, not a billing one.
 */
class WireguardTunnelEngine(private val context: Context) : TunnelEngine {

    private val backend: Backend by lazy { GoBackend(context) }
    private val _state = MutableStateFlow<TunnelState>(TunnelState.Down)
    override val state: StateFlow<TunnelState> = _state

    override suspend fun connect(config: PeerConfig, privateKeyBase64: String) {
        _state.value = TunnelState.Connecting
        val service = WgTunnelService.instance
        if (service == null) {
            _state.value = TunnelState.Failed(
                "The VPN service is not running. Grant the VPN permission and try again.",
            )
            return
        }
        try {
            val ini = WireguardConfigBuilder.build(config, privateKeyBase64)
            // Config.parse is the library's own wg-quick parser: the same text a user
            // would paste into the official app.
            val wgConfig = Config.parse(ini.byteInputStream())
            backend.setState(service, Tunnel.State.UP, wgConfig)
            _state.value = TunnelState.Up(sinceEpochSeconds = System.currentTimeMillis() / 1000)
        } catch (e: Exception) {
            // Never surface the config text: it contains the private key.
            _state.value = TunnelState.Failed(
                "Could not bring the tunnel up: ${e.javaClass.simpleName}",
            )
            runCatching { backend.setState(service, Tunnel.State.DOWN, null) }
        }
    }

    override suspend fun disconnect() {
        val service = WgTunnelService.instance
        if (service != null) {
            runCatching { backend.setState(service, Tunnel.State.DOWN, null) }
        }
        _state.value = TunnelState.Down
    }
}
