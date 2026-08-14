# ControlDeck

ControlDeck is a **LAN-only, peer-to-peer, cross-device control system**.
Install it on an Android phone, an Android tablet, and a Windows PC on the
same Wi-Fi network, and any device can build a dashboard of sliders and
buttons that control brightness, volume, mute, media playback, and app
launching — on itself **and** on the other paired devices.

There is **no central server, no cloud, no accounts.** Every device is a
peer that discovers, pairs with, and talks directly to the others over the
LAN.

```
Gaming Dashboard

Tablet Brightness   ──────────────●──── 70%
PC Brightness        ─────────●───────── 50%
Phone Brightness     ──────●──────────── 30%

[ PC Mute ]

[ Spotify ]  [ Discord ]

[ ◀ ]  [ ▶/Ⅱ ]  [ ▶ ]
```

## Contents

- [Architecture](#architecture)
- [Supported platforms](#supported-platforms)
- [Repository structure](#repository-structure)
- [How devices find each other](#how-devices-find-each-other)
- [Pairing](#pairing)
- [Dashboards](#dashboards)
- [Grouping](#grouping)
- [Building locally](#building-locally)
- [Testing](#testing)
- [CI/CD and releases](#cicd-and-releases)
- [Known limitations](#known-limitations)

## Architecture

```
                 LAN / Wi-Fi
                      │
       ┌──────────────┼──────────────┐
       │              │              │
    Android        Android        Windows
     Tablet          Phone          Agent
       │              │              │
       └──────────────┴──────────────┘
                  P2P WebSocket
```

Every device runs the same logical runtime:

```
Device Runtime
 ├── Discovery            mDNS/DNS-SD advertise + browse
 ├── Pairing              QR code / 6-digit PIN, shared-secret exchange
 ├── Transport             WebSocket server + client
 ├── Device Identity       stable UUID, editable display name
 ├── Capability Registry   what this device (and each peer) can do
 ├── Action Engine         dispatches actions to platform effects
 ├── State Manager         per-device last-known state, online/offline
 ├── Dashboard Manager     CRUD + last-write-wins sync
 ├── Group Manager         relative/absolute group semantics, reconnect policy
 └── Persistence           identity, pairings, dashboards, app registry
```

The wire protocol is fully documented and platform-independent:
see [`protocol/PROTOCOL.md`](protocol/PROTOCOL.md). The domain model and
the exact group-synchronization/reconnect algorithms are documented in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — both platforms implement
those algorithms independently against the same spec, rather than sharing
a runtime, so neither platform is a "primary" implementation.

## Supported platforms

| Platform | Role | Stack |
|---|---|---|
| Android (phone & tablet, same APK) | controller + target | Kotlin, Jetpack Compose, Coroutines, kotlinx.serialization, Room, DataStore, Ktor |
| Windows | controller + target | C#, .NET 8, WPF, System.Net.WebSockets, NAudio, WMI |

## Repository structure

```
controldeck/
├── android/          Android app (Gradle multi-module)
│   ├── domain/         pure Kotlin — models, group math, dashboard versioning (unit-testable, no Android SDK needed)
│   ├── protocol/        pure Kotlin — kotlinx.serialization DTOs matching protocol/PROTOCOL.md
│   └── app/              Android application: Compose UI, NSD discovery, Ktor WebSocket transport, Room/DataStore
├── windows/          Windows agent (.NET 8 solution)
│   ├── src/ControlDeck.Domain/     pure C# — mirrors android/domain
│   ├── src/ControlDeck.Protocol/    pure C# — mirrors android/protocol
│   ├── src/ControlDeck.Agent/        WPF app: UI, mDNS, WebSocket, Windows platform actions
│   └── tests/                          xUnit test projects per src project
├── protocol/         PROTOCOL.md — the source of truth for the wire format
├── docs/             ARCHITECTURE.md and supplementary docs
├── .github/workflows/  CI (per platform) + automated release
├── VERSION           MAJOR.MINOR base version; CI appends the run number as PATCH
├── LICENSE
└── README.md
```

## How devices find each other

ControlDeck advertises and discovers peers using **mDNS/DNS-SD**
(`_controlldeck._tcp.local.`), so you never type in an IP address. Each
device advertises a TXT record with its stable `deviceId`, name,
platform, app version, and WebSocket port. The UI always keys off
`deviceId`, never off a resolved IP — devices keep working across DHCP
lease changes and Wi-Fi reconnects. See
[`protocol/PROTOCOL.md §5`](protocol/PROTOCOL.md#5-discovery-out-of-band-not-websocket).

## Pairing

1. On the device you want to add as a target, open **Pair Device** — it
   shows a QR code and a 6-digit PIN.
2. On the controlling device, either scan the QR code or type in the PIN.
3. The two devices exchange a randomly generated shared secret over the
   already-open WebSocket connection and persist it (Android:
   `EncryptedSharedPreferences`/Keystore-backed storage; Windows: DPAPI
   -protected file). You never have to re-pair after restarting the app.
4. All later connections between the pair are authenticated with an
   HMAC-SHA256 proof derived from that secret — see
   [`protocol/PROTOCOL.md §3.2-3.3`](protocol/PROTOCOL.md#32-pairing).

## Dashboards

A dashboard is a versioned document of widgets (sliders/buttons/app
launchers), each targeting a specific paired device's capability.
Dashboards are created, edited, and deleted on any device and
automatically propagate to every paired device — there is no manual
export/import. Conflicts (near-simultaneous edits on two devices) are
resolved with **last-write-wins** by monotonically increasing dashboard
version; see [`docs/ARCHITECTURE.md §6`](docs/ARCHITECTURE.md#6-dashboard-synchronization).

If a target device is offline, its widgets render disabled with an
"offline" badge — the rest of the dashboard keeps working.

## Grouping

Multiple compatible widgets can be grouped into one logical control:

- **Slider groups** (brightness/volume) use **relative** synchronization:
  moving one member by `+10` moves every member by `+10`, each clamped to
  `[0, 100]` independently (no cross-member redistribution).
- **Mute groups** use **absolute** synchronization: the desired group
  state is computed (mute everything unless everything is already muted)
  and sent as an explicit `SET_MUTED` to every member — never a blind
  toggle.
- **Media groups** use **absolute** synchronization the same way, sending
  an explicit `PLAY`/`PAUSE` to every member based on the majority
  current state.

Full algorithms and worked examples: [`docs/ARCHITECTURE.md §4`](docs/ARCHITECTURE.md#4-group-semantics-the-trickiest-part--defined-once-centrally).

Reconnect behavior for a group member that was offline is configurable
per group: `SYNC_GROUP_STATE`, `KEEP_DEVICE_STATE`, or `NO_ACTION` — see
[`docs/ARCHITECTURE.md §5`](docs/ARCHITECTURE.md#5-reconnect-policy).

## Building locally

### Android

Requires JDK 17+ and the Android SDK (compile/target SDK 34). No signing
setup is required for a debug build.

```bash
cd android
gradle :domain:test :protocol:test   # pure-Kotlin domain/protocol tests, no SDK needed
gradle :app:assembleDebug            # debug APK, unsigned
gradle :app:testDebugUnitTest
gradle :app:lintDebug
```

Install the debug APK: `adb install app/build/outputs/apk/debug/app-debug.apk`.

### Windows

Requires the .NET 8 SDK on Windows (WPF requires the Windows desktop
workload; the Domain/Protocol projects build cross-platform).

```powershell
cd windows
dotnet restore ControlDeck.sln
dotnet build ControlDeck.sln --configuration Release
dotnet test ControlDeck.sln --configuration Release
dotnet run --project src/ControlDeck.Agent
```

A self-contained, no-install-required build (matching what the release
workflow produces):

```powershell
dotnet publish src/ControlDeck.Agent/ControlDeck.Agent.csproj -c Release -r win-x64 --self-contained true -o publish
```

## Testing

- **Domain tests** (Android `:domain`, Windows `ControlDeck.Domain.Tests`):
  slider relative sync + clamping, mute/media absolute group sync,
  reconnect policies, dashboard versioning/last-write-wins, capability
  validation, widget targeting, boundary conditions — encoding the exact
  worked examples from this README and the architecture doc.
- **Protocol tests** (Android `:protocol`, Windows `ControlDeck.Protocol.Tests`):
  serialization/deserialization of every message type, malformed
  payloads, unsupported/unknown message types, message/device ID
  handling.
- **App-level tests**: ViewModel/domain-adjacent logic on Android; the
  platform-action abstractions (volume/brightness/media/app-launch
  interfaces) on Windows, tested against fakes rather than real OS calls.

Run everything locally with the commands in [Building locally](#building-locally).

## CI/CD and releases

- **`ci-android.yml`** / **`ci-windows.yml`**: run on every push and pull
  request touching the respective platform — compile, unit test, and
  (Android) lint. No release artifacts are produced here.
- **`release.yml`**: runs on every push to `main`. It re-runs both CI
  workflows and, only if both succeed, packages and publishes a GitHub
  Release:
  - `ControlDeck-Android-v<version>.apk` (+ `.aab` when signing secrets
    are configured; otherwise an unsigned debug APK is published instead
    so `main` always produces something installable)
  - `ControlDeck-Windows-v<version>.zip` — a self-contained win-x64
    publish, runnable without installing the .NET runtime separately or
    cloning the repo.
  - If any build or test step fails, **no release is created.**

Version numbers follow semver, starting at `0.1.0`. [`VERSION`](VERSION)
holds the `MAJOR.MINOR` base; CI appends the GitHub Actions run number as
the patch component, so every release on `main` gets a unique version
without hand-editing version numbers across the repo.

### Android signing

Release builds are signed only when these GitHub Actions secrets are
present on the repository:

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64-encoded `.jks`/`.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | signing key alias |
| `ANDROID_KEY_PASSWORD` | signing key password |

No keystore, password, or key is ever committed to the repository. Until
these secrets are configured, `main` still produces an unsigned debug APK
so development isn't blocked.

## Known limitations

See the final implementation report for the current, exact list (build
verification status, unverified platform-specific code paths, etc.). In
scope-by-design, the MVP intentionally does **not** include: cloud
accounts/sync, TLS/`wss://` (the protocol is designed so it can be added
later without a wire-format change), offline action queuing, collaborative
multi-editor dashboard editing (CRDT/OT), or a freeform drag-and-drop
dashboard canvas (a grid-based editor is used instead).

## License

[MIT](LICENSE)
