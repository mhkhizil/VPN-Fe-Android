// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.data

import com.winteriscoming.vpn.api.Base64Hex
import com.wireguard.crypto.KeyPair

/** A keypair the app generated. The private half never leaves the device. */
data class GeneratedKeyPair(
    val privateKeyBase64: String,
    /** 64 lowercase hex, which is the only format the server accepts on upload. */
    val publicKeyHex: String,
    /** Base64, for the tunnel configuration the library wants. */
    val publicKeyBase64: String,
) {
    /**
     * Redacted on purpose. The generated `data class` toString() would print the
     * private key into any log line, crash report or debugger expression that touches
     * this object - the one string in this app that must never be printed.
     */
    override fun toString(): String =
        "GeneratedKeyPair(privateKey=<redacted>, publicKeyHex=$publicKeyHex, " +
            "publicKeyBase64=$publicKeyBase64)"
}

/**
 * Key generation is behind an interface so that onboarding logic can be tested on
 * the JVM without the native library, and so a future in-house implementation can
 * replace the library here without touching the repository.
 */
interface KeyPairFactory {
    fun generate(): GeneratedKeyPair
}

/**
 * Uses the tunnel library's own X25519 implementation
 * (`com.wireguard.crypto.KeyPair`) rather than a JCA generator: `KeyPairGenerator`
 * has no X25519 on Android, and hand-rolling curve arithmetic in app code is exactly
 * the kind of thing that produces a working tunnel and a wrong public key.
 *
 * NOTE: the exact member names here come from the library's javadoc, not from a
 * successful compile — this machine has no Android SDK. If CI disagrees, only this
 * file changes.
 */
class WgKeyPairFactory : KeyPairFactory {
    override fun generate(): GeneratedKeyPair {
        val pair = KeyPair()
        val privateBase64 = pair.privateKey.toBase64()
        val publicBase64 = pair.publicKey.toBase64()
        return GeneratedKeyPair(
            privateKeyBase64 = privateBase64,
            publicKeyHex = Base64Hex.hexFromBase64(publicBase64),
            publicKeyBase64 = publicBase64,
        )
    }
}
