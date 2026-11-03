# VPN-Fe-Android — Android client

Kotlin client for the `Winter-Is-Coming-Vpn` server
(**[mhkhizil/Winter-Is-Coming-Vpn](https://github.com/mhkhizil/Winter-Is-Coming-Vpn)**).
Server and client live in separate repositories on purpose: they ship on different
cadences, have different toolchains, and the client must be able to pin a server
contract version independently.

**Status: skeleton. Never compiled.** This repository has been built only by its CI
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
