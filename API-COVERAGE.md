# API coverage: which server endpoints does this client use?

Derived from the server's own router (`server/src/api/router.cpp`) and `docs/API.md`, not
from memory. Re-runnable: `python scripts/api-contract.py --external --port 8080` in the
server repo sends **exactly the requests `VpnApi` builds** and asserts the fields
`PeerConfigParser`/`OnboardingRepository` read.

## A. Endpoints this client calls (7 of 13 customer-facing)

| Endpoint | Client method | What the client reads | Verified by |
|---|---|---|---|
| `POST /api/v1/auth/login` | `VpnApi.login` | `access_token`, `user_id` | contract A1/A1b, `OnboardingTest` |
| `POST /api/v1/auth/rotate` | `VpnApi.rotateToken` (also automatic on 401) | `access_token` | contract A8/A8b (`A8b` proves the old token dies, which is what the client assumes) |
| `POST /api/v1/auth/logout` | `VpnApi.logout` (on sign-out) | status only | contract B16, `OnboardingTest.signingOutRevokesTheTokenAndForgetsTheKey` |
| `GET /api/v1/me` | `VpnApi.me` | `access.plan`, `access.status`, `access.days_left` | contract A2 |
| `POST /api/v1/devices` | `VpnApi.createDevice` | `id` | contract A3 |
| `PUT /api/v1/devices/{id}/key` | `VpnApi.uploadDeviceKey` | status only (204) | contract A4 |
| `POST /api/v1/peers` | `VpnApi.createPeer` | all of `interface.*` and `server.*` | contract A6 — asserts every field `PeerConfigParser` requires, including `private_key == null` |
| `GET /api/v1/servers` | `VpnApi.listServers` | `data[].endpoint`, `data[].public_key` | contract A7 |

## B. Customer-facing endpoints the client does **not** call

| Endpoint | Why not | What using it would buy |
|---|---|---|
| `GET /api/v1/devices` | the device id is cached in the session store | after app data is cleared while the key survives, the client creates a *second* device instead of reusing the first. Harmless server-side (devices are cheap) but it accumulates rows. |
| `GET /api/v1/peers` | onboarding always tries `POST` first | a device that already holds an active peer gets `422` and the user sees a failure; listing first would make onboarding idempotent against a peer that exists but whose config was lost. |
| `GET /api/v1/peers/{id}` | same | status display |
| `GET /api/v1/peers/{id}/config` | the config arrives in the `POST /peers` response | re-installing the tunnel after process death without a cached `PeerConfig` currently requires creating a peer. |
| `DELETE /api/v1/peers/{id}` | no revoke action in the UI | "revoke this device" without an admin |
| `POST /api/v1/me/password` | no change-password screen | a customer is stuck with the password an admin sent them |

These are **features, not defects**: each needs a UI affordance and, for the peer ones, a
decision about what "reuse" means for a device whose key rotated. They are listed here so
the gap is a known, sized item instead of a surprise.

## C. Operator-only endpoints (correctly absent from a customer app)

`POST /api/v1/nodes`, `GET /api/v1/dp/config`, `POST /api/v1/admin/reminders/run`,
`GET /api/v1/admin/audit`, `POST|GET /api/v1/admin/users`,
`GET /api/v1/admin/users/{id}`, `POST /api/v1/admin/users/{id}/access|password|revoke`.

All are covered by the contract script's section B so a client-relevant change to them
still shows up in the same run.

## D. What the check found

1. **`POST /api/v1/auth/logout` was missing from the client** — sign-out cleared the device
   but left a valid bearer token server-side until its TTL expired. Now wired, and a
   failed revoke no longer vetoes the local wipe.
2. **A fresh deployment could not be administered at all.** Bootstrap hardcoded role
   `owner`, and `owner` is not an administrator, so `/admin/*` and `/dp/config` answered
   `403` to the only account that existed. `VPN_BOOTSTRAP_ROLE=admin` is now supported
   (default unchanged, so a box still cannot come up with admin access by accident), and
   `server/tests/unit/test_store_bootstrap.cpp` pins both directions: a bootstrapped admin
   can list users, an owner cannot, and a nonsense role is refused at startup.
3. Everything else in section A answered with exactly the shape the client parses, so the
   seven endpoints the app depends on are contract-tested against the real binary, not
   just against hand-written fixtures.
