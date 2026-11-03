// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.tunnel

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Keeps the app's own control-plane traffic out of the tunnel it is configuring.
 *
 * The server hands out `AllowedIPs = 0.0.0.0/0, ::/0`, so without this the request that
 * creates the tunnel is itself routed into the tunnel: sign-in and renewal need the tunnel
 * up, the tunnel needs sign-in to finish, and on a device whose policy blocks the control
 * plane until renewal, the app can lock itself out.
 *
 * `VpnService.protect(socket)` is the supported exemption, and it must be applied *before*
 * `connect()`. That is why every overload here builds an unconnected [Socket], protects it,
 * then connects: wrapping a delegate factory that returns already-connected sockets would
 * be too late to work.
 *
 * The tunnel library exposes no `addDisallowedApplication`, so this is the alternative to
 * forking it.
 */
class ProtectingSocketFactory(
    private val protect: (Socket) -> Boolean = { socket ->
        WgTunnelService.instance?.protect(socket) ?: false
    },
) : SocketFactory() {

    override fun createSocket(): Socket = exempt(Socket())

    override fun createSocket(host: String, port: Int): Socket =
        exempt(Socket()).apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = exempt(Socket()).apply {
        bind(InetSocketAddress(localHost, localPort))
        connect(InetSocketAddress(host, port))
    }

    override fun createSocket(address: InetAddress, port: Int): Socket =
        exempt(Socket()).apply { connect(InetSocketAddress(address, port)) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = exempt(Socket()).apply {
        bind(InetSocketAddress(localAddress, localPort))
        connect(InetSocketAddress(address, port))
    }

    /**
     * `false` means there is no VPN to be exempted from, or the socket could not be
     * exempted. Either way the caller's request should still be attempted: refusing to
     * connect would turn "tunnel is down" into "cannot even sign in".
     */
    private fun exempt(socket: Socket): Socket {
        runCatching { protect.invoke(socket) }
        return socket
    }
}
