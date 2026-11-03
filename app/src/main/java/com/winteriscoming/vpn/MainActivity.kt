// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn

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
import com.winteriscoming.vpn.tunnel.TunnelController
import com.winteriscoming.vpn.tunnel.TunnelState
import com.winteriscoming.vpn.tunnel.VpnConsent
import com.winteriscoming.vpn.tunnel.WgTunnelService
import com.winteriscoming.vpn.tunnel.WireguardTunnelEngine
import com.winteriscoming.vpn.ui.VpnScreen
import com.winteriscoming.vpn.ui.theme.WinterVpnTheme
import kotlinx.coroutines.launch

/**
 * Wiring only: controllers are constructed here, rendering is [VpnScreen], and the
 * only Android-specific logic is the VPN consent handshake and starting the service.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = OnboardingRepository(
            api = VpnApi(BuildConfig.API_BASE_URL),
            secrets = KeystoreSecretStore(applicationContext),
            session = PrefsSessionStore(applicationContext),
            keys = WgKeyPairFactory(),
        )
        val onboarding = OnboardingController(repository)
        val tunnel = TunnelController(repository, WireguardTunnelEngine(applicationContext))
        setContent {
            WinterVpnTheme {
                VpnRoot(onboarding, tunnel)
            }
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
    VpnScreen(
        signedIn = state.account != null,
        busy = state.busy,
        message = state.message,
        planLabel = state.account?.let { account ->
            account.plan + " plan, " + account.daysLeft + " days left"
        },
        endpointLabel = config?.let { "Endpoint " + it.serverEndpoint + "   MTU " + it.mtu },
        addressesLabel = config?.let { it.addresses.joinToString("   ") },
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
    )
}