// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winteriscoming.vpn.tunnel.TunnelState
import com.winteriscoming.vpn.ui.theme.status

/**
 * The whole app in one screen.
 *
 * Stateless on purpose: every input is a parameter and every action is a callback, so
 * it can be previewed in Android Studio and driven by a UI test without a server, a
 * keystore or a VPN service. The stateful wiring lives in MainActivity.
 *
 * Three tiers only: identity, state, one action. A VPN app has one question ("am I
 * protected?") and one button; minimalist means not inventing a second reason to
 * look at the screen.
 */
@Composable
fun VpnScreen(
    signedIn: Boolean,
    busy: Boolean,
    message: String,
    planLabel: String?,
    endpointLabel: String?,
    addressesLabel: String?,
    renewalRequired: Boolean,
    tunnel: TunnelState,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onToggleTunnel: () -> Unit,
    /** Signing out forgets the device: sealed key, ids and token. */
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(planLabel, signedIn = signedIn, onSignOut = onSignOut)
            StatusCard(tunnel = tunnel, endpointLabel = endpointLabel)

            if (!signedIn) {
                SignInFields(
                    email = email,
                    onEmailChange = onEmailChange,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    enabled = !busy,
                )
            }

            PrimaryAction(
                signedIn = signedIn,
                busy = busy,
                tunnel = tunnel,
                onClick = if (signedIn) onToggleTunnel else onSignIn,
            )

            if (addressesLabel != null) {
                Text(
                    text = addressesLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (renewalRequired) {
                Text(
                    text = "Your plan has ended. Contact your provider to renew.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.status.attention,
                )
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(planLabel: String?, signedIn: Boolean, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Winter VPN",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (planLabel != null) {
                Text(
                    text = planLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Quiet by design: a plain text button, in the header, only once there is
        // something to sign out of.
        if (signedIn) {
            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}


@Composable
private fun StatusCard(tunnel: TunnelState, endpointLabel: String?) {
    val label: String
    val tint: androidx.compose.ui.graphics.Color
    when (tunnel) {
        is TunnelState.Up -> {
            label = "Connected"
            tint = MaterialTheme.status.connected
        }
        is TunnelState.Connecting -> {
            label = "Connecting"
            tint = MaterialTheme.status.idle
        }
        is TunnelState.Failed -> {
            label = "Not connected"
            tint = MaterialTheme.status.danger
        }
        TunnelState.Down -> {
            label = "Not connected"
            tint = MaterialTheme.status.idle
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dot plus word: colour is never the only signal.
                Box(
                    Modifier
                        .size(10.dp)
                        .background(tint, CircleShape),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tunnel is TunnelState.Connecting) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (endpointLabel != null) {
                Text(
                    text = endpointLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tunnel is TunnelState.Failed) {
                Text(
                    text = tunnel.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.status.danger,
                )
            }
        }
    }
}

@Composable
private fun SignInFields(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = enabled,
            // Hoisted: `PasswordVisualTransformation()` allocates, and the alternative is
            // a fresh instance on every keystroke's recomposition.
            visualTransformation = remember { PasswordVisualTransformation() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrimaryAction(
    signedIn: Boolean,
    busy: Boolean,
    tunnel: TunnelState,
    onClick: () -> Unit,
) {
    val connected = tunnel is TunnelState.Up
    val label = when {
        !signedIn -> "Sign in and set up"
        busy -> "Working"
        connected -> "Disconnect"
        else -> "Connect"
    }
    // One filled button, full width, 56 dp. When connected it steps back to a quiet
    // surface tint: the interesting state is already shown above it.
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (connected) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
