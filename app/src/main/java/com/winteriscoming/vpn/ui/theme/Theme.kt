// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BabyBlueInk,
    onPrimary = Snow,
    primaryContainer = BabyBlue,
    onPrimaryContainer = BabyBlueDeep,
    secondary = Slate,
    onSecondary = Snow,
    background = Cloud,
    onBackground = Ink,
    surface = Snow,
    onSurface = Ink,
    surfaceVariant = BabyBlueSurface,
    onSurfaceVariant = Slate,
    outline = OutlineSoft,
    outlineVariant = OutlineSoft,
    // Material's error role doubles as our "expired" colour; the label beside it is
    // what actually carries the meaning, never the colour alone.
    error = Coral,
    onError = Snow,
)

private val DarkColors = darkColorScheme(
    primary = NightBabyBlue,
    onPrimary = NightBg,
    primaryContainer = NightCard,
    onPrimaryContainer = NightBabyBlue,
    secondary = NightSlate,
    onSecondary = NightBg,
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightCard,
    onSurfaceVariant = NightSlate,
    outline = NightOutline,
    outlineVariant = NightOutline,
    error = NightCoral,
    onError = NightBg,
)

/**
 * Semantic colours Material3 has no role for.
 *
 * Kept separate from `colorScheme` so that "connected", "idle" and "attention due"
 * are named for what they mean rather than squeezed into `primary`/`error`. Every
 * one of them is paired with text in the UI: colour is never the only signal, which
 * matters for the ~8% of men with red-green colour blindness.
 */
@Immutable
data class StatusColors(
    val connected: Color,
    val idle: Color,
    val attention: Color,
    val danger: Color,
)

private val LightStatus = StatusColors(
    connected = Mint,
    idle = Slate,
    attention = Amber,
    danger = Coral,
)

private val DarkStatus = StatusColors(
    connected = NightMint,
    idle = NightSlate,
    attention = NightAmber,
    danger = NightCoral,
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** `MaterialTheme.status.connected`, alongside the standard `MaterialTheme.colorScheme`. */
val MaterialTheme.status: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

/**
 * Round, soft, unfussy. Two radii only: cards are pill-ish, controls are slightly
 * tighter. Mixing more than two looks accidental.
 */
private val WinterShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WinterVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // No dynamic colour on purpose: a VPN app that changes palette to match the
    // wallpaper stops looking like the thing the user trusts with their traffic.
    val colors = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatus else LightStatus
    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = WinterTypography,
            shapes = WinterShapes,
            content = content,
        )
    }
}
