// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.onboarding

import com.winteriscoming.vpn.api.PeerApi
import com.winteriscoming.vpn.api.PeerConfig
import com.winteriscoming.vpn.api.VpnApi
import com.winteriscoming.vpn.data.GeneratedKeyPair
import com.winteriscoming.vpn.data.KeyPairFactory
import com.winteriscoming.vpn.data.SecretStore
import com.winteriscoming.vpn.data.SessionStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Onboarding pipeline on the JVM, against fakes. No emulator, no VPN service, no
 * server - which is the point of the interfaces.
 *
 * The invariant these tests exist for: **the private key must never reach the API**.
 * If a future refactor "helpfully" uploads it, or logs a request body containing it,
 * one of these fails.
 */
private class FakeApi : PeerApi {
    var devicesCreated = 0
    val keysUploaded = mutableListOf<String>()
    var peersCreated = 0
    var failPeerWith: VpnApi.ApiException? = null
    var plan = "6m"
    var daysLeft = 180

    override fun login(email: String, password: String): VpnApi.Session =
        VpnApi.Session("usr_test", "user", "tok_1")

    override fun rotateToken(): VpnApi.Session = VpnApi.Session("usr_test", "user", "tok_rot")

    override fun me(): JSONObject = JSONObject(
        """{"user_id":"usr_test","role":"user",
            "access":{"plan":"$plan","status":"active","days_left":$daysLeft}}""",
    )

    override fun createDevice(name: String, platform: String): String {
        devicesCreated++
        return "dev_test"
    }

    override fun uploadDeviceKey(deviceId: String, publicKeyHex: String) {
        keysUploaded.add(publicKeyHex)
    }

    override fun createPeer(deviceId: String): PeerConfig {
        failPeerWith?.let { throw it }
        peersCreated++
        return testConfig()
    }

    override fun listServers(): JSONObject = JSONObject("""{"data":[]}""")
}

private class FakeSecrets : SecretStore {
    var stored: String? = null
    override fun storePrivateKey(base64: String) { stored = base64 }
    override fun loadPrivateKey(): String? = stored
    override fun clear() { stored = null }
}

private class FakeSession : SessionStore {
    override var accessToken: String? = null
    override var deviceId: String? = null
    override var peerId: String? = null
    override var publicKeyHex: String? = null
    override fun clear() {
        accessToken = null
        deviceId = null
        peerId = null
        publicKeyHex = null
    }
}

private class FakeKeys(private val publicHex: String = "ab".repeat(32)) : KeyPairFactory {
    var generated = 0
    override fun generate(): GeneratedKeyPair {
        generated++
        return GeneratedKeyPair(
            privateKeyBase64 = "cHJpdmF0ZS1rZXktYmFzZTY0",
            publicKeyHex = publicHex,
            publicKeyBase64 = "cHVibGljLWtleS1iYXNlNjQ=",
        )
    }
}

private fun testConfig() = PeerConfig(
    peerId = "pe_test",
    deviceId = "dev_test",
    status = "active",

    addresses = listOf("10.66.0.2/32"),
    dnsServers = listOf("10.66.0.1"),
    mtu = 1420,
    persistentKeepaliveSeconds = 25,
    serverEndpoint = "vpn.example.com:51820",
    serverPublicKey = "A4uR6Gx3e1rXgaEcbAo1rNurUIlxfbSgP5UhRU7NpCw=",
    allowedIps = listOf("0.0.0.0/0", "::/0"),
)


class OnboardingTest {

    @Test
    fun runsThePipelineInTheOrderTheServerRequires() = runTest {
        val api = FakeApi()
        val secrets = FakeSecrets()
        val session = FakeSession()
        val keys = FakeKeys()

        val outcome = OnboardingRepository(api, secrets, session, keys)
            .signInAndProvision("a@b.c", "pw", "android")

        assertTrue(outcome is OnboardingOutcome.Ready)
        val ready = outcome as OnboardingOutcome.Ready
        assertEquals("6m", ready.account.plan)
        assertEquals(180, ready.account.daysLeft)
        assertEquals(1, api.devicesCreated)
        assertEquals(1, api.peersCreated)
        assertEquals(listOf("ab".repeat(32)), api.keysUploaded)
        assertEquals("pe_test", session.peerId)
        assertNotNull("the private key must be sealed", secrets.stored)
    }

    @Test
    fun neverSendsThePrivateKeyToTheApi() = runTest {
        val api = FakeApi()
        val secrets = FakeSecrets()

        OnboardingRepository(api, secrets, FakeSession(), FakeKeys())
            .signInAndProvision("a@b.c", "pw", "android")

        val privateKey = secrets.stored!!
        assertFalse(
            "the private key must never appear in a key upload",
            api.keysUploaded.any { it.contains(privateKey) },
        )
        assertTrue("uploads are 64 lowercase hex", api.keysUploaded.all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test
    fun reusesTheDeviceAndKeyPairOnTheSecondRun() = runTest {
        val api = FakeApi()
        val secrets = FakeSecrets()
        val session = FakeSession()
        val keys = FakeKeys()
        val repository = OnboardingRepository(api, secrets, session, keys)

        repository.signInAndProvision("a@b.c", "pw", "android")
        repository.signInAndProvision("a@b.c", "pw", "android")

        assertEquals("one device per install", 1, api.devicesCreated)
        assertEquals("key pair generated once", 1, keys.generated)
        // Re-uploaded every run: idempotent, and it repairs a device row whose key
        // was cleared server-side instead of dead-ending the user.
        assertEquals(2, api.keysUploaded.size)
    }

    @Test
    fun reportsRenewalInsteadOfFailureWhenThePlanHasEnded() = runTest {
        val api = FakeApi().apply {
            failPeerWith = VpnApi.ApiException(403, "access expired or revoked")
        }

        val outcome = OnboardingRepository(api, FakeSecrets(), FakeSession(), FakeKeys())
            .signInAndProvision("a@b.c", "pw", "android")

        assertTrue(outcome is OnboardingOutcome.RenewalRequired)
    }

    @Test
    fun surfacesTheRenewalStateToTheUi() = runTest {
        val api = FakeApi().apply { failPeerWith = VpnApi.ApiException(403, "access expired") }
        val controller = OnboardingController(
            OnboardingRepository(api, FakeSecrets(), FakeSession(), FakeKeys()),
        )

        controller.signIn("a@b.c", "pw")

        val state = controller.state.value
        assertFalse(state.busy)
        assertTrue(state.renewalRequired)
        assertTrue(state.message.contains("plan has ended"))
        assertEquals(null, state.config)
    }

    @Test
    fun signOutDropsTheKeyAndTheSession() = runTest {
        val secrets = FakeSecrets()
        val session = FakeSession()
        val repository = OnboardingRepository(FakeApi(), secrets, session, FakeKeys())

        repository.signInAndProvision("a@b.c", "pw", "android")
        repository.signOut()

        assertEquals(null, secrets.loadPrivateKey())
        assertEquals(null, session.deviceId)
        assertEquals(null, session.accessToken)
    }
}

