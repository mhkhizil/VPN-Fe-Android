// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import com.winteriscoming.vpn.api.PeerConfig

/**
 * Renders the `wg-quick` configuration that the tunnel library parses.
 *
 * Kept as pure string logic on purpose: it is the one part of the connect path that
 * can be verified on the JVM, and it is where the boring mistakes live — a missing
 * `Address`, a keepalive of `0` written as `PersistentKeepalive = 0`, an IPv6
 * endpoint without brackets. The Android side of this (`GoBackend.setState`) is
 * three lines and cannot be tested here at all.
 *
 * The client does **not** decide routes: `AllowedIPs` comes from the server
 * (`VPN_ALLOWED_IPS`), so an operator can ship IPv4-only without touching the app.
 */
object WireguardConfigBuilder {

    /**
     * @param privateKeyBase64 the device private key, unwrapped from the keystore.
     * @throws IllegalArgumentException when a field would produce a config the
     *   library rejects, naming the field rather than letting WireGuard say "bad".
     */
    fun build(config: PeerConfig, privateKeyBase64: String): String {
        require(validKey(privateKeyBase64)) { "private key must be 44 base64 characters" }
        require(validKey(config.serverPublicKey)) { "server public key must be 44 base64 characters" }
        require(config.addresses.isNotEmpty()) { "at least one interface address is required" }
        require(config.allowedIps.isNotEmpty()) {
            "AllowedIPs must not be empty: a tunnel with no routes silently blackholes traffic"
        }
        require(config.serverHost.isNotBlank()) { "server endpoint has no host" }
        require(config.serverPort in 1..65535) { "server endpoint has no valid port" }

        val out = StringBuilder(512)
        out.append("[Interface]\n")
        out.append("PrivateKey = ").append(privateKeyBase64).append('\n')
        out.append("Address = ").append(config.addresses.joinToString(", ")).append('\n')
        if (config.dnsServers.isNotEmpty()) {
            out.append("DNS = ").append(config.dnsServers.joinToString(", ")).append('\n')
        }
        out.append("MTU = ").append(config.mtu).append('\n')

        out.append("\n[Peer]\n")
        out.append("PublicKey = ").append(config.serverPublicKey).append('\n')
        out.append("AllowedIPs = ").append(config.allowedIps.joinToString(", ")).append('\n')
        out.append("Endpoint = ").append(endpoint(config)).append('\n')
        // 0 means "off" in wg-quick; writing the line anyway is legal but noise.
        if (config.persistentKeepaliveSeconds > 0) {
            out.append("PersistentKeepalive = ").append(config.persistentKeepaliveSeconds).append('\n')
        }
        return out.toString()
    }

    /** IPv6 literals must be bracketed or the library parses the port as part of the host. */
    private fun endpoint(config: PeerConfig): String {
        val host = config.serverHost
        return if (host.contains(':')) "[$host]:${config.serverPort}" else "$host:${config.serverPort}"
    }

    /** 32 bytes base64-encoded is always 44 characters ending in '='. */
    private fun validKey(value: String): Boolean =
        value.length == 44 && value.endsWith("=") && value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
}
