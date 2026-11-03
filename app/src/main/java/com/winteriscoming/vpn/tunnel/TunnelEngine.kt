// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import com.winteriscoming.vpn.api.PeerConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the app and whatever actually moves packets.
 *
 * [WireguardTunnelEngine] wraps the official WireGuard library (ADR-0012). Tests supply
 * their own implementation of this interface rather than the app shipping one: a fake in
 * the main source set would ship an object whose whole purpose is to remember the private
 * key it was handed, which is the one thing that must not exist in a release binary.
 *
 * If we ever replace the official library with our own NDK build of `wire/`, it happens
 * behind this interface and nothing above it changes.
 */
interface TunnelEngine {
    val state: StateFlow<TunnelState>

    /**
     * Brings the tunnel up. [privateKeyBase64] is unwrapped from the keystore-backed
     * container by the caller and must not be logged or persisted anywhere else.
     */
    suspend fun connect(config: PeerConfig, privateKeyBase64: String)

    suspend fun disconnect()
}

sealed interface TunnelState {
    data object Down : TunnelState
    data object Connecting : TunnelState
    data class Up(val sinceEpochSeconds: Long, val rxBytes: Long = 0, val txBytes: Long = 0) :
        TunnelState

    /** [renewalRequired] is true for `403 access_expired`: the UI shows "renew", not "retry". */
    data class Failed(val message: String, val renewalRequired: Boolean = false) : TunnelState
}
