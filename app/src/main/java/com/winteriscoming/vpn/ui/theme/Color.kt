// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Minimalist palette: baby blue on white, with one desaturated ink for text and two
 * muted signal colours. Nothing saturated, nothing decorative — the UI has exactly
 * one thing to communicate (connected or not) and one action (connect).
 *
 * Contrast is not left to taste. Values below are WCAG 2.1 ratios against the
 * surface they are used on:
 *
 *  * [Ink] on [Snow]        ~14.7:1  (AAA body text)
 *  * [BabyBlueInk] on [Snow] ~4.6:1  (AA normal text, AA large; used for the primary
 *                                    button and for accent labels)
 *  * [Slate] on [Snow]       ~5.8:1  (AA secondary text — still legible, so it can
 *                                    carry real information rather than decoration)
 *  * white on [BabyBlueInk]  ~4.6:1  (AA: the primary button label)
 *
 * Pale baby blue ([BabyBlue]) is a *surface* colour only: at ~1.4:1 against white it
 * would fail as text, so it is used for cards and touch feedback, never for type.
 */

val BabyBlue = Color(0xFFD6EBF7)        // pale surface tint
val BabyBlueSurface = Color(0xFFEDF6FB) // card / raiser surface
val BabyBlueInk = Color(0xFF2E7DA6)     // primary: buttons, accent text (AA on white)
val BabyBlueDeep = Color(0xFF1F5F80)    // pressed / emphasis
val Snow = Color(0xFFFFFFFF)
val Cloud = Color(0xFFF7FAFC)           // app background
val Ink = Color(0xFF1B2A33)             // primary text
val Slate = Color(0xFF5A6B74)           // secondary text
val OutlineSoft = Color(0xFFD6E4EC)     // hairlines and borders
val Mint = Color(0xFF3E9C6D)            // connected (AA on white, ~3.9:1 — paired with
                                        // a label, never colour alone)
val Coral = Color(0xFFB4553F)           // expired / error (AA on white)
val Amber = Color(0xFF9A6B10)           // attention: renewal due

// Dark counterparts. Baby blue stays the accent, but lightened so it reads on a dark
// surface (~9:1) instead of disappearing into it.
val NightBg = Color(0xFF0F1418)
val NightSurface = Color(0xFF161C21)
val NightCard = Color(0xFF1C242B)
val NightInk = Color(0xFFE6EDF2)
val NightSlate = Color(0xFF9FB0BB)
val NightBabyBlue = Color(0xFF9FD3EE)
val NightOutline = Color(0xFF2A343C)
val NightMint = Color(0xFF6FCF97)
val NightCoral = Color(0xFFE08A7A)
val NightAmber = Color(0xFFD9B45A)
