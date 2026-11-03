// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of [ProtectingSocketFactory] is *when* it calls protect: `VpnService.protect`
 * only works on a socket that has not been connected yet. A factory that returned an
 * already-connected socket would look right and silently fail to keep traffic out of the
 * tunnel, so the ordering is asserted, not assumed.
 */
class ProtectingSocketFactoryTest {

    @Test
    fun exemptsTheSocketBeforeConnectingIt() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            var calls = 0
            var connectedAtCallTime: Boolean? = null
            val factory = ProtectingSocketFactory { socket ->
                calls++
                connectedAtCallTime = socket.isConnected
                true
            }

            factory.createSocket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
                assertEquals("protect must be called exactly once", 1, calls)
                assertEquals(
                    "protect must run before connect(), or the exemption is useless",
                    false,
                    connectedAtCallTime,
                )
                assertTrue("the socket must still end up connected", socket.isConnected)
            }
        }
    }

    @Test
    fun theNoArgSocketIsExemptedAndLeftUnconnected() {
        var calls = 0
        val factory = ProtectingSocketFactory { calls++; true }
        factory.createSocket().use { socket ->
            assertEquals(1, calls)
            assertFalse(
                "OkHttp connects a no-arg socket itself; it must arrive unconnected",
                socket.isConnected,
            )
        }
    }

    @Test
    fun aProtectFailureDoesNotBlockTheConnection() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val factory = ProtectingSocketFactory { error("no VPN service") }
            factory.createSocket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
                assertTrue("a missing exemption must not become a failed sign-in", socket.isConnected)
            }
        }
    }
}
