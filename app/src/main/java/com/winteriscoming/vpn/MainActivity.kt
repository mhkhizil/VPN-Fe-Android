// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

/**
 * Onboarding, account status and the connect switch.
 *
 * The Activity only wires things together: the pipeline lives in OnboardingController
 * and the tunnel in TunnelController/TunnelEngine, so both can be exercised on the JVM
 * (the pipeline fully, the tunnel through its interface).
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
            MaterialTheme {
                Scaffold { padding ->
                    VpnScreen(onboarding, tunnel, Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }
}

@Composable
private fun VpnScreen(
    onboarding: OnboardingController,
    tunnel: TunnelController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by onboarding.state.collectAsState()
    val tunnelState by tunnel.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pendingConfig by remember { mutableStateOf<PeerConfig?>(null) }

    // Start the foreground service, then hand the config to the engine.
    fun startTunnel(config: PeerConfig) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WgTunnelService::class.java),
        )
        scope.launch { tunnel.connect(config) }
    }

    // The VPN consent dialog is a separate Activity; its result decides whether the
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

    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Winter VPN", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            enabled = !state.busy,
        )
        Button(
            onClick = { scope.launch { onboarding.signIn(email.trim(), password) } },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
        ) {
            Text(if (state.busy) "Working..." else "Sign in and set up")
        }

        if (state.message.isNotEmpty()) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }

        val config = state.config
        if (config != null) {
            Text(
                "Endpoint " + config.serverEndpoint + "  |  MTU " + config.mtu,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Addresses " + config.addresses.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )

            val connected = tunnelState is TunnelState.Up
            Button(onClick = {
                if (connected) {
                    scope.launch { tunnel.disconnect() }
                } else {
                    val intent = VpnConsent.prepare(context)
                    if (intent != null) {
                        pendingConfig = config
                        consent.launch(intent)
                    } else {
                        startTunnel(config)
                    }
                }
            }) {
                Text(if (connected) "Disconnect" else "Connect")
            }
        }

        when (val current = tunnelState) {
            is TunnelState.Up -> Text("Tunnel is up.")
            is TunnelState.Connecting -> Text("Connecting...")
            is TunnelState.Failed -> Text(current.message)
            TunnelState.Down -> Unit
        }

        if (state.renewalRequired) {
            Text("Contact your provider to renew your plan.")
        }
    }
}