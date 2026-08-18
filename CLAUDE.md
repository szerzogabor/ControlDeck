# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ControlDeck is a **LAN-only, peer-to-peer, cross-device control system**: Android
(phone/tablet) and Windows apps that discover each other over mDNS, pair via
QR/PIN, and let any device build a dashboard of sliders/buttons that control
brightness, volume, mute, media playback, and app launching on itself and on
paired peers. There is no central server, no cloud, no accounts — every
device runs the identical logical runtime and talks directly to peers over
WebSocket.

**Read these before making cross-cutting changes:**
- [`protocol/PROTOCOL.md`](protocol/PROTOCOL.md) — the source of truth for the wire format. Neither platform implementation is authoritative over the other; this file is. If you change wire behavior, update this doc first.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — domain model, group-sync algorithms, reconnect policy. Both platforms implement these algorithms independently against this spec rather than sharing a runtime.

## Repository structure

```
android/          Android app (Gradle multi-module), package com.controlldeck
├── domain/         pure Kotlin, no Android SDK — models, group math, dashboard versioning
├── protocol/        pure Kotlin — kotlinx.serialization DTOs matching protocol/PROTOCOL.md
└── app/              Compose UI, NSD discovery, Ktor WebSocket transport, Room/DataStore persistence
windows/          Windows agent (.NET 8 solution: ControlDeck.sln)
├── src/ControlDeck.Domain/     pure C#, no Windows deps — mirrors android/domain
├── src/ControlDeck.Protocol/    pure C# — System.Text.Json DTOs matching protocol/PROTOCOL.md
├── src/ControlDeck.Agent/        WPF app: UI, mDNS, WebSocket, Windows platform actions (volume/brightness/media/app-launch via NAudio/WMI)
└── tests/                          xUnit test project per src project (Agent tests use fakes, not real OS calls)
protocol/         PROTOCOL.md
docs/             ARCHITECTURE.md
```

Android and Windows are **independent implementations of the same spec**,
not a shared runtime — there is no code sharing between them beyond the
protocol contract. When adding a feature that touches the wire protocol or
group/reconnect semantics, expect to implement it twice (once per platform)
against the same documented behavior, and update both platforms' domain
tests with the same worked examples.

## Commands

### Android (from `android/`)

```bash
gradle :domain:test :protocol:test   # pure-Kotlin domain/protocol tests, no SDK needed
gradle :app:testDebugUnitTest        # app-level unit tests
gradle :app:lintDebug
gradle :app:assembleDebug            # unsigned debug APK
```

Single test class: `gradle :domain:test --tests "com.controlldeck.domain.GroupControllerTest"`.
Install the debug APK: `adb install app/build/outputs/apk/debug/app-debug.apk`.

### Windows (from `windows/`, requires Windows + .NET 8 SDK; WPF needs the Windows desktop workload)

```powershell
dotnet restore ControlDeck.sln
dotnet build ControlDeck.sln --configuration Release
dotnet test ControlDeck.sln --configuration Release
dotnet run --project src/ControlDeck.Agent
```

Single test: `dotnet test --filter "FullyQualifiedName~GroupControllerTests.RelativeSlider_ClampsIndependently"`.

Self-contained publish (matches the release workflow's output):
```powershell
dotnet publish src/ControlDeck.Agent/ControlDeck.Agent.csproj -c Release -r win-x64 --self-contained true -o publish
```

## Architecture

### Layering (both platforms)

`Widget → Action → Target → Transport → Protocol → Remote Device → Platform Action`.
UI code never talks to sockets; socket code never knows about widgets. Each
platform's runtime has the same set of responsibilities: Discovery, Pairing,
Transport, Device Identity, Capability Registry, Action Engine, State
Manager, Dashboard Manager, Group Manager, Persistence — see
`docs/ARCHITECTURE.md` §2 for what each owns.

### Group semantics — the part most likely to need care

All group math lives in **one pure function per group kind** in the domain
layer (`GroupController.apply(group, originWidgetId, userInput)`); UI layers
never compute deltas or target states themselves. This is deliberate so
Android and Windows can't drift apart on behavior. Three kinds, each with an
exact algorithm in `docs/ARCHITECTURE.md` §4:

- **RELATIVE_SLIDER** (brightness/volume groups): every member shifts by the
  same delta, clamped to `[0,100]` **independently** — no redistribution
  when one member saturates. Worked example: `95, 80, 90` with `+20` →
  `100, 100, 100`.
- **ABSOLUTE_TOGGLE** (mute groups): compute `desiredMuted = !allMuted`,
  broadcast an explicit `SET_MUTED` to every member — never a blind local
  toggle.
- **ABSOLUTE_MEDIA** (media groups): explicit play/pause based on majority
  current state; `MEDIA_NEXT`/`MEDIA_PREVIOUS` are edge-triggered and always
  broadcast unconditionally.

Reconnect policy (`SYNC_GROUP_STATE` | `KEEP_DEVICE_STATE` | `NO_ACTION`,
default `SYNC_GROUP_STATE`) is evaluated by the `GroupManager`, not the UI,
whenever a group member transitions offline → online. See
`docs/ARCHITECTURE.md` §5.

### Dashboard sync

Last-write-wins by a monotonically increasing `version` integer, no vector
clocks, no CRDTs. On receipt, a peer applies an incoming `DASHBOARD_SYNC`
iff `incoming.version > local.version`; equal versions are broken by
message `timestamp` then by `sourceDeviceId` string ordering, deterministic
identically on both platforms. Full rule: `docs/ARCHITECTURE.md` §6.

### Protocol essentials

- Every message shares a common envelope (`protocolVersion`, `type`,
  `messageId`, `sourceDeviceId`, `targetDeviceId`, `timestamp`, `payload`).
  Unknown `payload` fields must be ignored; unknown `type` produces an
  `ERROR{UNSUPPORTED_MESSAGE_TYPE}` rather than a crash.
- Connection lifecycle: `HELLO` → (if unpaired) `PAIR_REQUEST`/`PAIR_RESPONSE`
  → `AUTH`/`AUTH_RESULT` → `DEVICE_INFO`/`CAPABILITIES` both directions →
  steady state (`ACTION`/`ACTION_RESULT`, `STATE_UPDATE`,
  `DASHBOARD_SYNC`/`ACK`, `PING`/`PONG` every 15s, 3 missed marks offline).
- Pairing produces a persisted 256-bit `sharedSecret`; every later
  connection re-authenticates with an HMAC-SHA256 `proof` over
  `messageId + timestamp` (binds the secret to one message, not a
  replayable bearer token). Plain `ws://` for the MVP; the envelope has a
  `secure: false` field reserving a future `wss://` upgrade with no
  wire-format change.
- `appId` (app-launch widgets) is always opaque on the wire — resolved to a
  package name / executable path locally by the target device, never
  transmitted as a filesystem path. See `PROTOCOL.md` §8.
- Discovery is mDNS/DNS-SD (`_controlldeck._tcp.local.`), out of band from
  the WebSocket transport. The UI always keys off the advertised
  `deviceId`, never a resolved IP.

## CI/CD

- `ci-android.yml` / `ci-windows.yml` run on push to `main` only when files
  under their platform dir (or `protocol/PROTOCOL.md`) change; they are also
  invoked as reusable workflows by `release.yml`. Neither runs on pull
  requests.
- `release.yml` runs on every push to `main`, re-runs both CI workflows, and
  only on success packages a GitHub Release (Android APK/AAB, Windows
  self-contained win-x64 zip). Android release signing only activates when
  `ANDROID_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD`/`ANDROID_KEY_ALIAS`/`ANDROID_KEY_PASSWORD`
  secrets are present on the repo; otherwise an unsigned debug APK is
  published so `main` always produces something installable.
- `VERSION` holds the `MAJOR.MINOR` base; CI appends the Actions run number
  as `PATCH`, so releases never need hand-edited version bumps.

## Scope boundaries

The MVP intentionally excludes cloud accounts/sync, TLS (`wss://` is a
planned upgrade, not implemented), offline action queuing, collaborative
multi-editor dashboard editing (CRDT/OT), and a freeform drag-and-drop
canvas (widgets are grid-based). Don't introduce these unless explicitly
asked — they represent deliberate scope cuts, not gaps to fill.
