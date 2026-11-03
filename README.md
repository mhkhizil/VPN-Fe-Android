# VPN-Fe-Android — Android client

Kotlin client for the `Winter-Is-Coming-Vpn` server
(**[mhkhizil/Winter-Is-Coming-Vpn](https://github.com/mhkhizil/Winter-Is-Coming-Vpn)**).
Server and client live in separate repositories on purpose: they ship on different
cadences, have different toolchains, and the client must be able to pin a server
contract version independently.

**Status: Phase 2 (onboarding) implemented — still never compiled.** This repository has been built only by its CI
lane — no developer machine with a JDK/Android SDK/NDK has run it yet. Treat the
first green CI run as the moment the code exists.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/.../api/PeerConfig.kt` | Parser for `POST /api/v1/peers`. **Refuses any payload containing a `private_key`** — the server must never see or return one (server ADR-0007), and that rule is now executable rather than documented. |
| `app/src/main/java/.../api/VpnApi.kt` | Login, token rotation, device registration, public-key upload, peer creation, `/me`. `ApiException.renewalRequired` maps `403 access_expired` to a renewal prompt instead of a retry. |
| `app/src/main/java/.../tunnel/TunnelEngine.kt` | The seam between UI and packet movement: `TunnelEngine` + `FakeTunnelEngine` so onboarding and state handling are testable on the JVM with no VPN service and no emulator. |
| `app/src/main/java/.../tunnel/WgTunnelService.kt` | `GoBackend.VpnService` subclass — the documented embedding path of the official tunnel library. |
| `app/src/main/java/.../MainActivity.kt` | Compose onboarding skeleton (sign in → register device → servers). |
| `app/src/test/java/.../PeerConfigParserTest.kt` | Locks the server contract on the JVM against a **real captured server response**. |

## The tunnel is the official WireGuard library

`com.wireguard.android:tunnel:1.0.20260102` (Apache-2.0, Maven Central — see
`NOTICE`), behind our own `TunnelEngine` interface. Two reasons:

1. Client-side WireGuard (timers, rekey, cookie replies, keepalives, roaming) is
   subtle, and the reference implementation already gets it right.
2. Every session then doubles as an **official-implementation interop proof** for the
   server, which had only ever been tested against its own client.

The interface exists so that a future in-house tunnel (the server repo has a
portable C++ `wire/` layer) can replace it without touching the UI.

## What is not done

* Key generation and the keystore-backed container. **AndroidKeyStore has no X25519**:
  generate with the library's `com.wireguard.crypto.KeyPair`, seal the base64 private
  key with a KeyStore-backed AES-GCM key (StrongBox when available), persist only the
  ciphertext. Do not attempt `KeyPairGenerator`.
* `VpnService.prepare` consent dialog, foreground-service notification, Always-on VPN,
  and `addDisallowedApplication(ownPackage)` so the app's own API traffic never enters
  the tunnel it is bringing up.
* `WireguardTunnelEngine` (wiring `GoBackend.setState` to `WgTunnelService`): the exact
  member names must be confirmed against the AAR javadoc in CI first.
* Play Integrity, i18n, store listing.

## Build

```bash
gradle :app:testDebugUnitTest     # JVM contract tests, no emulator
gradle :app:assembleDebug         # debug APK
gradle :app:assembleDebug -PapiBaseUrl=https://vpn.example.com   # point at a server
```

There is no Gradle wrapper checked in (its jar is a binary); CI installs a pinned
Gradle 8.11.1. Run `gradle wrapper` locally if you prefer the wrapper.

## Contract

The client follows the server's documented API:
`docs/API.md` and `docs/CLIENT_INTEGRATION.md` in the server repository. A change to
the `POST /api/v1/peers` payload is a breaking change for this repository —
`PeerConfigParserTest` is what fails when it happens.

## Phase 2 — onboarding (implemented)

Pipeline, in the order the server requires:

```
login -> create device -> generate X25519 on device -> PUT public key (hex) -> create peer -> config
```

| Path | What it does |
|---|---|
| `data/KeyPairFactory.kt` | Key generation behind an interface. Uses the tunnel library`s own X25519 (`com.wireguard.crypto.KeyPair`) because `KeyPairGenerator` has no X25519 on Android. |
| `data/SecretStores.kt` | `SecretStore` seals the private key with a keystore-held AES-256-GCM key and stores only the ciphertext in EncryptedSharedPreferences. `SessionStore` holds the token, device/peer ids and the public key. |
| `onboarding/Onboarding.kt` | `OnboardingRepository` (the pipeline, idempotent) and `OnboardingController` (UI state). |
| `api/PeerApi.kt` | The API surface as an interface, so the pipeline is testable with fakes on the JVM. |
| `api/Base64Hex.kt` | Base64 <-> hex. The server accepts only 64 lowercase hex characters for a public key; WireGuard speaks base64. |

Design decisions that are easy to get wrong:

* **The private key never reaches the API.** It is generated on device, sealed, and only the public half is uploaded (server ADR-0007). A test asserts no upload ever contains it.
* **Onboarding is idempotent.** Device id, peer id and public key are reused across launches, and the key is re-uploaded every run: cheap, and it repairs a device row whose key was cleared server-side instead of dead-ending the user.
* **The keystore holds the right to decrypt, not the key.** AndroidKeyStore has no X25519, and `setUserAuthenticationRequired(false)` is deliberate: biometric-per-use would break reconnect-after-reboot and Always-on VPN. Any code running as this app can obtain the key — the normal model for VPN clients, stated rather than assumed.
* **`403 access_expired` is a renewal state, not an error.** `OnboardingState.renewalRequired` drives a "contact your provider" message rather than a retry button.

Tests (`app/src/test`, JVM, no emulator): pipeline order, private key never uploaded, device/keypair reuse across runs, renewal surfacing, sign-out clearing secrets, and base64/hex checked against `java.util.Base64` as an independent oracle.

Still open for M3: `WireguardTunnelEngine` (wiring `GoBackend.setState`), the consent dialog, the foreground service notification, and `addDisallowedApplication`.