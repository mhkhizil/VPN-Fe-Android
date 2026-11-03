// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import com.winteriscoming.vpn.api.PeerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tunnel configuration is the one part of the connect path that can be verified
 * without a device, so it gets the attention: this text is parsed by the official
 * library, and a malformed line fails at connection time with a message that rarely
 * names the offending field.
 */
class WireguardConfigBuilderTest {

    private val privateKey = "yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk="
    private val serverKey = "A4uR6Gx3e1rXgaEcbAo1rNurUIlxfbSgP5UhRU7NpCw="

    private fun config(
        addresses: List<String> = listOf("10.66.0.2/32", "fd00:66::2/128"),
        dns: List<String> = listOf("10.66.0.1"),
        allowed: List<String> = listOf("0.0.0.0/0", "::/0"),
        endpoint: String = "vpn.example.com:51820",
        mtu: Int = 1420,
        keepalive: Int = 25,
    ) = PeerConfig(
        peerId = "pe_test",
        deviceId = "dev_test",
        status = "active",
        addresses = addresses,
        dnsServers = dns,
        mtu = mtu,
        persistentKeepaliveSeconds = keepalive,
        serverEndpoint = endpoint,
        serverPublicKey = serverKey,
        allowedIps = allowed,
    )

    @Test
    fun rendersTheExactConfigTheLibraryParses() {
        val expected = """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.66.0.2/32, fd00:66::2/128
            DNS = 10.66.0.1
            MTU = 1420

            [Peer]
            PublicKey = A4uR6Gx3e1rXgaEcbAo1rNurUIlxfbSgP5UhRU7NpCw=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = vpn.example.com:51820
            PersistentKeepalive = 25

        """.trimIndent()

        assertEquals(expected.trim(), WireguardConfigBuilder.build(config(), privateKey).trim())
    }

    @Test
    fun bracketsAnIpv6Endpoint() {
        // Without brackets the library reads "fd00:66::1:51820" and fails on the host.
        val text = WireguardConfigBuilder.build(config(endpoint = "[fd00:66::1]:51820"), privateKey)
        assertTrue(text.contains("Endpoint = [fd00:66::1]:51820"))
    }

    @Test
    fun joinsMultipleDnsServersAndAddresses() {
        val text = WireguardConfigBuilder.build(
            config(
                addresses = listOf("10.66.0.2/32", "fd00:66::2/128", "192.168.9.9/32"),
                dns = listOf("10.66.0.1", "fd00:66::1"),
            ),
            privateKey,
        )
        assertTrue(text.contains("Address = 10.66.0.2/32, fd00:66::2/128, 192.168.9.9/32"))
        assertTrue(text.contains("DNS = 10.66.0.1, fd00:66::1"))
    }

    @Test
    fun omitsKeepaliveWhenItIsZero() {
        val text = WireguardConfigBuilder.build(config(keepalive = 0), privateKey)
        assertFalse("keepalive 0 must not be written", text.contains("PersistentKeepalive"))
    }

    @Test
    fun omitsDnsWhenTheServerSendsNone() {
        val text = WireguardConfigBuilder.build(config(dns = emptyList()), privateKey)
        assertFalse(text.contains("DNS ="))
    }

    @Test
    fun honoursIpv4OnlyDeployment() {
        // The IPv6 go/no-go switch lives on the server (VPN_ALLOWED_IPS); the client
        // must simply not invent routes of its own.
        val text = WireguardConfigBuilder.build(config(allowed = listOf("0.0.0.0/0")), privateKey)
        assertTrue(text.contains("AllowedIPs = 0.0.0.0/0"))
        assertFalse(text.contains("::/0"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnEmptyAllowedIps() {
        WireguardConfigBuilder.build(config(allowed = emptyList()), privateKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAShortPrivateKey() {
        WireguardConfigBuilder.build(config(), "not-a-key")
    }

    @Test
    fun namesTheOffendingFieldInItsErrors() {
        val error = runCatching {
            WireguardConfigBuilder.build(config(allowed = emptyList()), privateKey)
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("AllowedIPs"))
    }
}
