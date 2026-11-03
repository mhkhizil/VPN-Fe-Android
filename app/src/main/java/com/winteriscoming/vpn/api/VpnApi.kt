// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * Thin client for the vpnd REST API. Endpoints are the ones documented in
 * docs/API.md and verified against a live server; the shapes here are the ones the
 * server actually returns today, not the aspirational ones.
 *
 * The session token is not held only in memory: it is loaded from [loadToken] on first
 * use and written back through [saveToken] whenever it changes, so a process death does
 * not leave the user "not logged in" with a perfectly valid token still on disk. A 401
 * on a request that carried a token triggers exactly one rotation and one retry, because
 * the server invalidates the old value the moment it issues a new one.
 */
class VpnApi(
    baseUrl: String,
    private val http: OkHttpClient = defaultClient(),
    private val loadToken: () -> String? = { null },
    private val saveToken: (String?) -> Unit = {},
) : PeerApi {

    private val base = baseUrl.trimEnd('/')

    @Volatile
    private var token: String? = null

    /** The bearer token in force, hydrated from the session store on first use. */
    val accessToken: String?
        get() = token ?: runCatching { loadToken() }.getOrNull()?.also { token = it }

    private fun setToken(value: String?) {
        token = value
        runCatching { saveToken(value) }
    }

    data class Session(val userId: String, val role: String, val accessToken: String)

    override fun login(email: String, password: String): Session {
        val body = JSONObject().put("email", email).put("password", password)
        val json = post("/api/v1/auth/login", body, token = null)
        val session = Session(
            userId = json.optString("user_id"),
            role = json.optString("role", "user"),
            accessToken = json.getString("access_token"),
        )
        setToken(session.accessToken)
        return session
    }

    /** Rotates the token: the old value dies server-side, so a leak has a short life. */
    override fun rotateToken(): Session {
        val json = post("/api/v1/auth/rotate", JSONObject(), token = requireToken(), rotateOn401 = false)
        val session = Session(
            userId = json.optString("user_id"),
            role = json.optString("role", "user"),
            accessToken = json.getString("access_token"),
        )
        setToken(session.accessToken)
        return session
    }

    /** `/me` reports plan and days_left so the UI can show "expires in N days". */
    override fun me(): JSONObject = get("/api/v1/me", requireToken())

    override fun createDevice(name: String, platform: String): String {
        val body = JSONObject().put("name", name).put("platform", platform)
        return post("/api/v1/devices", body, requireToken()).getString("id")
    }

    /**
     * Uploads the device public key. Mandatory before peer creation: the server
     * rejects a peer for a device with no key, so onboarding has a fixed order.
     */
    override fun uploadDeviceKey(deviceId: String, publicKeyHex: String) {
        put("/api/v1/devices/$deviceId/key", JSONObject().put("public_key", publicKeyHex), requireToken())
    }

    /** Creates (or returns) the peer and its full tunnel configuration. */
    override fun createPeer(deviceId: String): PeerConfig =
        PeerConfigParser.parse(post("/api/v1/peers", JSONObject().put("device_id", deviceId), requireToken()).toString())

    override fun listServers(): JSONObject = get("/api/v1/servers", requireToken())

    private fun requireToken(): String =
        accessToken ?: throw IllegalStateException("not logged in")

    private fun get(path: String, token: String?, rotateOn401: Boolean = true): JSONObject =
        execute(Request.Builder().url(base + path).get(), token, rotateOn401)

    private fun post(
        path: String,
        body: JSONObject,
        token: String?,
        rotateOn401: Boolean = true,
    ): JSONObject = execute(
        Request.Builder().url(base + path).post(body.toString().toRequestBody(JSON)),
        token,
        rotateOn401,
    )

    private fun put(path: String, body: JSONObject, token: String?): JSONObject =
        execute(
            Request.Builder().url(base + path).put(body.toString().toRequestBody(JSON)),
            token,
        )

    private fun execute(
        builder: Request.Builder,
        token: String?,
        rotateOn401: Boolean = true,
    ): JSONObject {
        if (token != null) builder.header("Authorization", "Bearer $token")
        builder.header("Accept", "application/json")
        val request = builder.build()
        http.newCall(request).execute().use { response ->
            // Bounded read. peekBody(n) reads at most n bytes, so a hostile or
            // misconfigured endpoint cannot make this process allocate megabytes, and
            // the error path reuses the same text instead of reading the body twice.
            val text = response.peekBody(MAX_BODY_BYTES).string()
            if (response.code == 401 && token != null && rotateOn401) {
                // The token expired or was rotated elsewhere. Rotate once and retry once:
                // any more than that is a loop, and `rotateOn401 = false` keeps the
                // rotate call itself out of it.
                val renewed = runCatching { rotateToken() }.getOrNull()
                if (renewed != null) {
                    return execute(request.newBuilder(), renewed.accessToken, rotateOn401 = false)
                }
            }
            if (!response.isSuccessful) {
                throw ApiException(response.code, problemDetail(text))
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun problemDetail(text: String): String =
        runCatching { JSONObject(text).optString("detail", text) }.getOrDefault(text)

    class ApiException(val status: Int, override val message: String) : Exception(message) {
        /**
         * True when the plan has lapsed. The server answers `403 access_expired`, and
         * the UI must offer renewal rather than a retry button (ADR-0010).
         */
        val renewalRequired: Boolean get() = status == 403
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Far more than any endpoint returns; a bound, not a target. */
        private const val MAX_BODY_BYTES = 64L * 1024

        fun defaultClient(socketFactory: SocketFactory? = null): OkHttpClient =
            OkHttpClient.Builder()
                // The raw socket factory is where the tunnel exemption has to happen; TLS
                // is still layered on top by the default SSLSocketFactory.
                .apply { if (socketFactory != null) socketFactory(socketFactory) }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
