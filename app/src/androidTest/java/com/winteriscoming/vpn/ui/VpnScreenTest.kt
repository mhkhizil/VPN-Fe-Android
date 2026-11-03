// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.winteriscoming.vpn.tunnel.TunnelState
import com.winteriscoming.vpn.ui.theme.WinterVpnTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the one screen.
 *
 * They live here rather than in `test/` because Compose UI testing needs a real
 * Android runtime. They are the only tests in the project that can catch layout
 * regressions ("the connect button disappeared"), and they exist so that "the design
 * works" is checkable by a machine rather than by squinting at a screenshot.
 *
 * Run with: gradle :app:connectedDebugAndroidTest   (needs a device or emulator)
 */
@RunWith(AndroidJUnit4::class)
class VpnScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(
        signedIn: Boolean = false,
        busy: Boolean = false,
        message: String = "",
        renewalRequired: Boolean = false,
        tunnel: TunnelState = TunnelState.Down,
        onSignIn: () -> Unit = {},
        onToggleTunnel: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        compose.setContent {
            WinterVpnTheme {
                VpnScreen(
                    signedIn = signedIn,
                    busy = busy,
                    message = message,
                    planLabel = if (signedIn) "6m plan, 180 days left" else null,
                    endpointLabel = if (signedIn) "Endpoint vpn.example.com:51820   MTU 1420" else null,
                    addressesLabel = if (signedIn) "10.66.0.2/32" else null,
                    renewalRequired = renewalRequired,
                    tunnel = tunnel,
                    email = "",
                    onEmailChange = {},
                    password = "",
                    onPasswordChange = {},
                    onSignIn = onSignIn,
                    onToggleTunnel = onToggleTunnel,
                    onSignOut = onSignOut,
                )
            }
        }
    }

    @Test
    fun signedOutShowsEmailPasswordAndTheSetupButton() {
        setScreen(signedIn = false)
        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Sign in and set up").assertExists()
    }

    @Test
    fun idleStateSaysNotConnectedAndOffersConnect() {
        setScreen(signedIn = true, tunnel = TunnelState.Down)
        compose.onNodeWithText("Not connected").assertExists()
        compose.onNodeWithText("Connect").assertExists()
    }

    @Test
    fun connectedStateSaysConnectedAndOffersDisconnect() {
        setScreen(signedIn = true, tunnel = TunnelState.Up(sinceEpochSeconds = 0))
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("Disconnect").assertExists()
        compose.onNodeWithText("Not connected").assertDoesNotExist()
    }

    @Test
    fun statusIsAWordNotJustAColour() {
        // The dot is decoration; the word is the accessible signal.
        setScreen(signedIn = true, tunnel = TunnelState.Failed("handshake failed"))
        compose.onNodeWithText("Not connected").assertExists()
        compose.onNodeWithText("handshake failed").assertExists()
    }

    @Test
    fun renewalIsAnnouncedInWords() {
        setScreen(signedIn = true, renewalRequired = true)
        compose.onNodeWithText("Your plan has ended. Contact your provider to renew.")
            .assertExists()
    }

    @Test
    fun busyDisablesThePrimaryAction() {
        setScreen(signedIn = false, busy = true)
        compose.onNodeWithText("Working").assertIsNotEnabled()
    }

    @Test
    fun tappingConnectCallsTheCallback() {
        var tapped = false
        setScreen(signedIn = true, tunnel = TunnelState.Down, onToggleTunnel = { tapped = true })
        compose.onNodeWithText("Connect").performClick()
        assertTrue(tapped)
    }

    @Test
    fun thePrimaryActionIsEnabledWhenIdle() {
        setScreen(signedIn = false, busy = false)
        compose.onNodeWithText("Sign in and set up").assertIsEnabled()
    }

    @Test
    fun signOutIsOfferedOnlyWhenSignedIn() {
        setScreen(signedIn = false)
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun tappingSignOutCallsTheCallback() {
        var signedOut = false
        setScreen(signedIn = true, onSignOut = { signedOut = true })
        compose.onNodeWithText("Sign out").performClick()
        assertTrue(signedOut)
    }
}
