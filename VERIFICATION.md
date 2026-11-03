# Verification status

The point of this file is that no claim in the README is better than the evidence
behind it. Every row says *how* it was checked and, where it was not, why not.

Last updated with commit `main` at the time of writing; regenerate the commands below
rather than trusting the table.

## Client (this repository)

| Capability | How it was verified | Status |
|---|---|---|
| Kotlin compiles (app, unit tests, UI tests) | `gradle :app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` locally, JDK 21 / Gradle 8.7 / AGP 8.5.2 | **verified** |
| 27 JVM unit tests | `gradle :app:testDebugUnitTest`; JUnit XML in `app/build/test-results/` — Base64Hex 7, PeerConfigParser 5, Onboarding 6, WireguardConfigBuilder 9, **0 failures** | **verified** |
| The `POST /api/v1/peers` contract | `PeerConfigParserTest` parses a payload captured from a live `vpnd` (base64 padding, bracketed IPv6 endpoint, refusal of a server-supplied `private_key`) | **verified** |
| The private key never leaves the device | `OnboardingTest.neverSendsThePrivateKeyToTheApi`: every upload is asserted to be 64 lowercase hex and to not contain the sealed key | **verified** |
| APK is produced and installable | `apksigner verify --print-certs` → Android debug certificate; `aapt2 dump badging` → `com.winteriscoming.vpn.debug`, compileSdk 34, minSdk 24, target 34 | **verified** |
| Permissions are minimal | `aapt2 dump badging`: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS. No location, no storage, no contacts | **verified** |
| Static analysis | `gradle :app:lintDebug` → **0 errors**, 10 warnings, all dependency-freshness except a redundant manifest label (fixed) and the ChromeOS ABI note | **verified** |
| UI renders, connect/disconnect wiring, renewal copy | `VpnScreenTest` (8 instrumented cases) — **compiled**, not executed | **compiled only** |
| Tunnel actually comes up | nothing yet | **NOT verified** |
| VPN consent dialog, foreground notification | nothing yet | **NOT verified** |
| `KeyPair()` and `GoBackend.setState` at runtime | nothing yet — they compile, they have never run | **NOT verified** |
| Battery / soak behaviour | nothing yet | **NOT verified** |

### Why the device rows are empty

There is no physical device attached, and the emulator cannot start on this machine:

```
CPU Acceleration: DISABLED
CPU Acceleration status: Android Emulator hypervisor driver is not installed on this machine
ERROR | x86_64 emulation currently requires hardware acceleration!
```

Windows 11 Home has no Hyper-V, and installing AEHD or enabling WHPX needs
administrator rights and a reboot. That is an environment limit, not a code defect —
but it means the tunnel has still never moved a packet.

### To close them

```bash
# on a machine with a device or a working emulator
gradle :app:connectedDebugAndroidTest        # the 8 UI cases
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s WinterVpn:V AndroidRuntime:E   # watch for KeyPair/GoBackend failures
```

The APK's `API_BASE_URL` defaults to `https://vpn.example.com`; rebuild with
`-PapiBaseUrl=https://your.host` before installing, or the sign-in will fail with a
DNS error (which is itself a useful check that the error paths work).

## Server (separate repository)

Recorded here because a client claim is only as good as the server it talks to.

| Capability | How it was verified | Status |
|---|---|---|
| C++ unit + HTTP tests | `ctest` → 2/2 suites, includes PBKDF2 known answers and the TLS-policy cases | **verified** |
| Interop with an **independent** WireGuard implementation | `scripts/interop-local.ps1` → handshake + transport PASS | **verified** |
| Interop with the **official** `wg` client | `scripts/interop-test.sh` | **NOT verified** — needs a Linux host with `wireguard-tools` |
| Real TUN forwarding | same script | **NOT verified** — the data plane has never forwarded a packet |
| Access expiry enforced in the data plane | `scripts/verify-access-expiry.ps1` → expired refused, active/staff accepted | **verified** |
| Control-plane → data-plane peer pull | `scripts/verify-cp-dp-pull.ps1` → peers fetched, handshake served | **verified** |
| Renewal reminders | `scripts/verify-reminders.ps1` → one mail, idempotent second run | **verified** |

## The one-line summary

The client **builds, passes 27 unit tests, lints clean, and produces a signed APK**;
nothing that requires a device or a Linux host has been executed, so "it works as a
VPN" is still a claim about code, not about behaviour.
