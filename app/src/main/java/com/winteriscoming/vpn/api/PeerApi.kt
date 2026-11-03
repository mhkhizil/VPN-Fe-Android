// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.api

import org.json.JSONObject

/**
 * The subset of the server API the app depends on, as an interface so onboarding can
 * be tested on the JVM without sockets, OkHttp or a server.
 *
 * Keeping this list short is deliberate: every method here is a contract the client
 * repo promises the server it uses, and `PeerConfigParserTest` plus the fakes below
 * are what notice when the server changes it.
 */
interface PeerApi {
    fun login(email: String, password: String): VpnApi.Session
    fun rotateToken(): VpnApi.Session

    /**
     * Revokes the presented token server-side. Signing out locally is not enough: the
     * token stays valid until its TTL expires, so a device that signs out after a
     * suspected compromise leaves a usable credential behind.
     */
    fun logout()
    fun me(): JSONObject
    fun createDevice(name: String, platform: String = "android"): String
    fun uploadDeviceKey(deviceId: String, publicKeyHex: String)
    fun createPeer(deviceId: String): PeerConfig
    fun listServers(): JSONObject
}
