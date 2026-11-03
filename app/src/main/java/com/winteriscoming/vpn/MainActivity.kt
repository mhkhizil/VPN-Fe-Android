// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.winteriscoming.vpn.api.PeerConfig
import com.winteriscoming.vpn.api.VpnApi
import com.winteriscoming.vpn.data.KeystoreSecretStore
import com.winteriscoming.vpn.data.PrefsSessionStore
import com.winteriscoming.vpn.data.WgKeyPairFactory
import com.winteriscoming.vpn.onboarding.OnboardingController
import com.winteriscoming.vpn.onboarding.OnboardingRepository
import com.winteriscoming.vpn.tunnel.ProtectingSocketFactory
import com.winteriscoming.vpn.tunnel.TunnelController
import com.winteriscoming.vpn.tunnel.TunnelState
import com.winteriscoming.vpn.tunnel.VpnConsent
import com.winteriscoming.vpn.tunnel.WgTunnelService
import com.winteriscoming.vpn.tunnel.WireguardTunnelEngine
import com.winteriscoming.vpn.ui.VpnScreen
import com.winteriscoming.vpn.ui.theme.WinterVpnTheme
import kotlinx.coroutines.launch

/**
 * Wiring only: controllers are constructed in [VpnGraph], rendering is [VpnScreen], and
 * the only Android-specific logic is the VPN consent handshake and starting the service.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = VpnGraph.of(applicationContext)
        val onboarding = graph.onboarding
        val tunnel = graph.tunnel
        setContent {
            WinterVpnTheme {
                VpnRoot(onboarding, tunnel)
            }
        }
    }
}

/**
 * One object graph per process, not per Activity.
 *
 * This used to be built inside `onCreate`. Every rotation then created a new
 * [com.wireguard.android.backend.GoBackend] - which is `AutoCloseable` and was never
 * closed, so its worker thread and VPN-service connection outlived the Activity - a new
 * OkHttpClient (thread pool and connection pool abandoned), and a fresh
 * [WireguardTunnelEngine] whose state starts at `Down`, so a rotation during an active
 * connection redrew the UI as "disconnected" while the tunnel was up.
 *
 * Holds only the application context, so nothing here can leak an Activity.
 */
internal class VpnGraph private constructor(context: Context) {

    private val session = PrefsSessionStore(context)

    private val repository = OnboardingRepository(
        api = VpnApi(
            baseUrl = BuildConfig.API_BASE_URL,
            // The tunnel carries 0.0.0.0/0, so the socket that talks to the control plane
            // has to be exempted explicitly - otherwise sign-in and renewal are routed
            // into the tunnel they are trying to establish.
            http = VpnApi.defaultClient(ProtectingSocketFactory()),
            // The token outlives the process: hydrate from the store on first use, and
            // write every rotation back so the next launch is not "not logged in".
            loadToken = { session.accessToken },
            saveToken = { session.accessToken = it },
        ),
        secrets = KeystoreSecretStore(context),
        session = session,
        keys = WgKeyPairFactory(),
    )

    val onboarding = OnboardingController(repository)
    val tunnel = TunnelController(repository, WireguardTunnelEngine(context))

    companion object {
        @Volatile
        private var instance: VpnGraph? = null

        fun of(context: Context): VpnGraph =
            instance ?: synchronized(this) {
                instance ?: VpnGraph(context.applicationContext).also { instance = it }
            }
    }
}

@Composable
private fun VpnRoot(onboarding: OnboardingController, tunnel: TunnelController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by onboarding.state.collectAsState()
    val tunnelState by tunnel.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pendingConfig by remember { mutableStateOf<PeerConfig?>(null) }

    fun startTunnel(config: PeerConfig) {
        ContextCompat.startForegroundService(
            context,
            android.content.Intent(context, WgTunnelService::class.java),
        )
        scope.launch { tunnel.connect(config) }
    }

    // The VPN consent dialog is a separate activity: its result decides whether the
    // service may be started at all.
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val config = pendingConfig
        pendingConfig = null
        if (result.resultCode == android.app.Activity.RESULT_OK && config != null) {
            startTunnel(config)
        }
    }

    val config = state.config
    // Derived values are remembered against their inputs: without this, every keystroke
    // in the email field rebuilt these three strings and the two lambdas below, so the
    // whole screen (header, status card, button) recomposed on each character typed.
    val planLabel = remember(state.account) {
        state.account?.let { account -> account.plan + " plan, " + account.daysLeft + " days left" }
    }
    val endpointLabel = remember(config) {
        config?.let { "Endpoint " + it.serverEndpoint + "   MTU " + it.mtu }
    }
    val addressesLabel = remember(config) { config?.addresses?.joinToString("   ") }
    val onSignOut: () -> Unit = remember(onboarding) {
        {
            scope.launch { onboarding.signOut() }
            // Explicit Unit: `launch` returns a Job, and a () -> Job is not a () -> Unit.
            Unit
        }
    }

    VpnScreen(
        signedIn = state.account != null,
        busy = state.busy,
        message = state.message,
        planLabel = planLabel,
        endpointLabel = endpointLabel,
        addressesLabel = addressesLabel,
        renewalRequired = state.renewalRequired,
        tunnel = tunnelState,
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        onSignIn = { scope.launch { onboarding.signIn(email.trim(), password) } },
        onToggleTunnel = {
            if (tunnelState is TunnelState.Up) {
                scope.launch { tunnel.disconnect() }
            } else if (config != null) {
                val intent = VpnConsent.prepare(context)
                if (intent != null) {
                    pendingConfig = config
                    consent.launch(intent)
                } else {
                    startTunnel(config)
                }
            }
        },
        onSignOut = onSignOut,
    )
}