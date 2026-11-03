// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Base64/hex conversion, checked against `java.util.Base64` as an independent oracle
 * rather than against my own arithmetic. The server accepts only 64 lowercase hex
 * characters for a public key, while WireGuard speaks base64: a silent mismatch here
 * looks like "invalid public key" and gives no hint about the cause.
 */
class Base64HexTest {

    /** Real key the server returned in a captured peer payload. */
    private val realServerKey = "A4uR6Gx3e1rXgaEcbAo1rNurUIlxfbSgP5UhRU7NpCw="

    @Test
    fun matchesJavaUtilForEveryPaddingCase() {
        val cases = listOf(
            byteArrayOf(),
            byteArrayOf(0x01),
            byteArrayOf(0x01, 0x02),
            byteArrayOf(0x01, 0x02, 0x03),
            byteArrayOf(-1, -1, -1, -1),
            ByteArray(32) { it.toByte() },
        )
        for (bytes in cases) {
            assertEquals(
                "encode ${bytes.size} bytes",
                Base64.getEncoder().encodeToString(bytes),
                Base64Hex.encodeBase64(bytes),
            )
        }
    }

    @Test
    fun decodesWhatJavaUtilEncodes() {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        assertTrue("decode", Base64Hex.decodeBase64(encoded).contentEquals(bytes))
    }

    @Test
    fun producesSixtyFourLowercaseHexForARealKey() {
        val hex = Base64Hex.hexFromBase64(realServerKey)
        assertEquals(64, hex.length)
        assertEquals(hex.lowercase(), hex)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun roundTripsTheRealKeyExactly() {
        // Padding must survive: '=' is the character most often lost to "helpful"
        // normalisation, and WireGuard keys depend on it.
        assertEquals(realServerKey, Base64Hex.base64FromHex(Base64Hex.hexFromBase64(realServerKey)))
    }

    @Test
    fun hexMatchesAnIndependentHandComputation() {
        // 0x00,0xff,0x10 -> "AP8Q"
        assertEquals("00ff10", Base64Hex.hexFromBase64("AP8Q"))
        assertEquals("AP8Q", Base64Hex.base64FromHex("00ff10"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnOddLengthHexString() {
        Base64Hex.base64FromHex("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonBase64Input() {
        Base64Hex.decodeBase64("!!!!")
    }
}
