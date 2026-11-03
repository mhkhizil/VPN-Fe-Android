// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
package com.winteriscoming.vpn.onboarding

import com.winteriscoming.vpn.api.PeerApi
import com.winteriscoming.vpn.api.PeerConfig
import com.winteriscoming.vpn.api.VpnApi
import com.winteriscoming.vpn.data.KeyPairFactory
import com.winteriscoming.vpn.data.SecretStore
import com.winteriscoming.vpn.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** What the account is entitled to right now, from `GET /api/v1/me`. */
data class AccountStatus(
    val userId: String,
    val plan: String,
    val status: String,
    val daysLeft: Int,
)

sealed interface OnboardingOutcome {
    data class Ready(val config: PeerConfig, val account: AccountStatus) : OnboardingOutcome

    /** `403 access_expired`: show renewal, not retry (ADR-0010). */
    data class RenewalRequired(val message: String) : OnboardingOutcome
    data class Failed(val message: String) : OnboardingOutcome
}

/**
 * The onboarding pipeline, in the order the server requires:
 *
 * ```
 * login -> device -> (keypair, on device) -> PUT device key -> peer -> config
 * ```
 *
 * Two properties worth calling out because both are load-bearing:
 *
 * * **The private key never reaches the API.** It is generated here, sealed by the
 *   [SecretStore] and only the public half is uploaded (server ADR-0007).
 * * **The sequence is idempotent.** The device id, the peer id and the public key are
 *   reused across launches, and the key upload is repeated even when it already
 *   happened, so a server-side reset self-heals instead of dead-ending the user in
 *   "device already has no key".
 */
class OnboardingRepository(
    private val api: PeerApi,
    private val secrets: SecretStore,
    private val session: SessionStore,
    private val keys: KeyPairFactory,
) {

    suspend fun signInAndProvision(
        email: String,
        password: String,
        deviceName: String,
    ): OnboardingOutcome {
        return try {
            val login = api.login(email, password)
            session.accessToken = login.accessToken

            val deviceId = session.deviceId ?: api.createDevice(deviceName).also {
                session.deviceId = it
            }

            var publicHex = session.publicKeyHex
            if (secrets.loadPrivateKey() == null || publicHex == null) {
                val generated = keys.generate()
                secrets.storePrivateKey(generated.privateKeyBase64)
                publicHex = generated.publicKeyHex
                session.publicKeyHex = publicHex
            }
            // Always re-upload: cheap, idempotent, and it repairs a device row whose
            // key was cleared. Skipping it is what turns a reset into a dead end.
            api.uploadDeviceKey(deviceId, publicHex)

            val config = api.createPeer(deviceId)
            session.peerId = config.peerId

            OnboardingOutcome.Ready(config, parseAccount(api.me()))
        } catch (e: VpnApi.ApiException) {
            if (e.renewalRequired) {
                OnboardingOutcome.RenewalRequired(e.message)
            } else {
                OnboardingOutcome.Failed(e.message)
            }
        } catch (e: Exception) {
            OnboardingOutcome.Failed(e.message ?: "onboarding failed")
        }
    }

    /** The stored private key, or null when the user must onboard first. */
    fun storedPrivateKey(): String? = secrets.loadPrivateKey()

    fun signOut() {
        secrets.clear()
        session.clear()
    }

    private fun parseAccount(json: JSONObject): AccountStatus {
        val access = json.optJSONObject("access")
        return AccountStatus(
            userId = json.optString("user_id", ""),
            plan = access?.optString("plan", "unknown") ?: "unknown",
            status = access?.optString("status", "unknown") ?: "unknown",
            daysLeft = access?.optInt("days_left", 0) ?: 0,
        )
    }
}

/** UI-facing state; a plain class so it is testable without Android. */
data class OnboardingState(
    val busy: Boolean = false,
    val account: AccountStatus? = null,
    val config: PeerConfig? = null,
    val message: String = "",
    val renewalRequired: Boolean = false,
)

/**
 * Holds [OnboardingState] and runs the pipeline. Kept out of the Activity so the
 * flow is testable on the JVM with fake stores.
 */
class OnboardingController(private val repository: OnboardingRepository) {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    suspend fun signIn(email: String, password: String, deviceName: String = "android") {
        _state.value = _state.value.copy(busy = true, message = "Signing in…", renewalRequired = false)
        when (val outcome = repository.signInAndProvision(email, password, deviceName)) {
            is OnboardingOutcome.Ready -> _state.value = OnboardingState(
                busy = false,
                account = outcome.account,
                config = outcome.config,
                message = "Signed in as ${outcome.account.userId} (${outcome.account.plan}, " +
                    "${outcome.account.daysLeft} days left)",
            )
            is OnboardingOutcome.RenewalRequired -> _state.value = _state.value.copy(
                busy = false,
                renewalRequired = true,
                message = "Your plan has ended: ${outcome.message}",
            )
            is OnboardingOutcome.Failed -> _state.value = _state.value.copy(
                busy = false,
                message = outcome.message,
            )
        }
    }
}
