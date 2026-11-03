// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.api

/**
 * Base64 <-> hex conversion.
 *
 * The server stores and accepts public keys as **64 lowercase hex characters**
 * (`decode_key` in the server rejects anything else), while WireGuard itself — and
 * the tunnel library — speak **base64**. Both directions are needed:
 * base64→hex when uploading the device key, hex/base64 when building the tunnel
 * configuration. Getting this wrong produces a rejected key upload with a message
 * that does not mention base64 at all, so it is worth a test.
 */
object Base64Hex {

    fun hexFromBase64(base64: String): String {
        val bytes = decodeBase64(base64)
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4])
            out.append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    fun base64FromHex(hex: String): String {
        require(hex.length % 2 == 0) { "hex string must have an even length" }
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "not a hex string: $hex" }
            bytes[i] = (((hi shl 4) or lo) and 0xFF).toByte()
        }
        return encodeBase64(bytes)
    }

    /** RFC 4648 base64, no line breaks. Padding is preserved: WireGuard keys end in '='. */
    fun encodeBase64(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < bytes.size) {
            val block = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(B64[(block ushr 18) and 0x3F])
            sb.append(B64[(block ushr 12) and 0x3F])
            sb.append(B64[(block ushr 6) and 0x3F])
            sb.append(B64[block and 0x3F])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val block = (bytes[i].toInt() and 0xFF) shl 16
                sb.append(B64[(block ushr 18) and 0x3F])
                sb.append(B64[(block ushr 12) and 0x3F])
                sb.append("==")
            }
            2 -> {
                val block = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8)
                sb.append(B64[(block ushr 18) and 0x3F])
                sb.append(B64[(block ushr 12) and 0x3F])
                sb.append(B64[(block ushr 6) and 0x3F])
                sb.append('=')
            }
        }
        return sb.toString()
    }

    fun decodeBase64(base64: String): ByteArray {
        val clean = base64.trim()
        require(clean.length % 4 == 0) { "base64 length must be a multiple of 4" }
        val out = java.io.ByteArrayOutputStream(clean.length / 4 * 3)
        var i = 0
        while (i < clean.length) {
            val chunk = IntArray(4) { idx ->
                val c = clean[i + idx]
                when {
                    c == '=' -> 0
                    else -> {
                        val v = B64.indexOf(c)
                        require(v >= 0) { "not base64: '$c'" }
                        v
                    }
                }
            }
            val block = (chunk[0] shl 18) or (chunk[1] shl 12) or (chunk[2] shl 6) or chunk[3]
            out.write((block ushr 16) and 0xFF)
            if (clean[i + 2] != '=') out.write((block ushr 8) and 0xFF)
            if (clean[i + 3] != '=') out.write(block and 0xFF)
            i += 4
        }
        return out.toByteArray()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val HEX = "0123456789abcdef".toCharArray()
}
