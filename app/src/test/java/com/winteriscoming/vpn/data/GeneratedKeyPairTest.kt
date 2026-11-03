// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The private key must not be printable. A `data class` generates a `toString()` that
 * happily includes every field, which is exactly how secrets reach logcat, crash reports
 * and "just add a println" debugging.
 */
class GeneratedKeyPairTest {

    private val pair = GeneratedKeyPair(
        privateKeyBase64 = "SUPER-SECRET-PRIVATE-KEY-MATERIAL=",
        publicKeyHex = "ab".repeat(32),
        publicKeyBase64 = "cHVibGljLWtleQ==",
    )

    @Test
    fun toStringRedactsThePrivateKey() {
        val text = pair.toString()
        assertFalse("toString() must not contain the private key: $text", text.contains("SUPER-SECRET"))
        assertTrue("the public half stays visible for support", text.contains("ab".repeat(32)))
    }

    @Test
    fun equalityStillWorksBecauseItIsADataClass() {
        assertTrue(pair == pair.copy())
    }
}
