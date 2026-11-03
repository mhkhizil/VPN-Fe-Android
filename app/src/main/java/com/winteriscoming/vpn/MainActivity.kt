// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import com.winteriscoming.vpn.api.VpnApi
import com.winteriscoming.vpn.data.KeystoreSecretStore
import com.winteriscoming.vpn.data.PrefsSessionStore
import com.winteriscoming.vpn.data.WgKeyPairFactory
import com.winteriscoming.vpn.onboarding.OnboardingController
import com.winteriscoming.vpn.onboarding.OnboardingRepository
import kotlinx.coroutines.launch

/**
 * Onboarding + account status.
 *
 * The Activity owns nothing but wiring: the pipeline lives in OnboardingController so
 * it can be tested on the JVM, and the tunnel seam is TunnelEngine so the connect
 * path (M3) can be swapped without touching this file.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = OnboardingController(
            OnboardingRepository(
                api = VpnApi(BuildConfig.API_BASE_URL),
                secrets = KeystoreSecretStore(applicationContext),
                session = PrefsSessionStore(applicationContext),
                keys = WgKeyPairFactory(),
            ),
        )
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    OnboardingScreen(controller, Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(controller: OnboardingController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
            onClick = { scope.launch { controller.signIn(email.trim(), password) } },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
        ) {
            Text(if (state.busy) "Working..." else "Sign in and set up")
        }

        if (state.message.isNotEmpty()) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        state.config?.let { config ->
            Text(
                "Endpoint " + config.serverEndpoint + "  |  MTU " + config.mtu,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Addresses " + config.addresses.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.renewalRequired) {
            Text("Contact your provider to renew your plan.")
        }
    }
}