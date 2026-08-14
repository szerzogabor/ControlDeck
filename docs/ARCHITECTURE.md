# ControlDeck Architecture

## 1. Principles

- **No central server.** Every device is a peer running the same runtime
  shape (see below). "Controller" and "target" are roles a device plays
  simultaneously, not separate deployables.
- **Layering.** `Widget → Action → Target → Transport → Protocol →
  Remote Device → Platform Action`. UI code never talks to sockets, and
  socket code never knows about widgets.
- **No cloud.** No accounts, Firebase, Supabase, analytics, ads, or
  payments. LAN-only.

## 2. Device Runtime (identical shape on every platform)

```
Device Runtime
 ├── Discovery          (mDNS advertise + browse)
 ├── Pairing            (QR/PIN, shared-secret exchange)
 ├── Transport           (WebSocket server + client per protocol/PROTOCOL.md)
 ├── Device Identity     (stable deviceId, editable deviceName)
 ├── Capability Registry (what this device can do; what peers can do)
 ├── Action Engine       (dispatches ACTION -> platform-specific effect; executes locally-originated group semantics)
 ├── State Manager       (per-device last-known state: brightness/volume/muted/media, ONLINE/OFFLINE)
 ├── Dashboard Manager    (CRUD + last-write-wins sync, per protocol/PROTOCOL.md §3.7)
 ├── Group Manager        (relative/absolute group semantics, reconnect policy)
 └── Persistence          (device identity, paired devices + secrets, dashboards, app registry, prefs)
```

Each box is implemented per-platform (Kotlin on Android, C# on Windows)
against the same responsibilities; there is deliberately no shared binary
runtime between Android and Windows — only the JSON contract in
`protocol/PROTOCOL.md` is shared. Sharing a JVM/CLR runtime across both
would force artificial abstractions the spec explicitly warns against.

Within each platform, the pure domain logic (group math, dashboard
versioning, reconnect policy) is isolated in a dependency-free module so
it is unit-testable without a device/emulator and without any platform
API.

## 3. Domain model

```
Dashboard
 ├── id: DashboardId
 ├── name: String
 ├── version: Long            (monotonic, incremented on every edit)
 ├── widgets: List<Widget>
 └── groups: List<Group>

Widget
 ├── id: WidgetId
 ├── type: WidgetType          (SLIDER_BRIGHTNESS | SLIDER_VOLUME | BUTTON_MUTE |
 │                               BUTTON_MEDIA_PLAY_PAUSE | BUTTON_MEDIA_NEXT |
 │                               BUTTON_MEDIA_PREVIOUS | APP_LAUNCH)
 ├── position: GridPosition (x, y)
 ├── size: GridSize (w, h)
 ├── targetDeviceId: DeviceId
 ├── action: ActionSpec        (mirrors protocol Action, appId for APP_LAUNCH widgets)
 └── configuration: Map<String,String>  (widget-specific, e.g. display label)

Group
 ├── id: GroupId
 ├── name: String
 ├── kind: GroupKind            (RELATIVE_SLIDER | ABSOLUTE_TOGGLE | ABSOLUTE_MEDIA)
 ├── memberWidgetIds: List<WidgetId>
 └── reconnectPolicy: ReconnectPolicy (SYNC_GROUP_STATE | KEEP_DEVICE_STATE | NO_ACTION)

DeviceState
 ├── deviceId
 ├── connection: ONLINE | OFFLINE
 ├── brightness: Int?
 ├── volume: Int?
 ├── muted: Boolean?
 └── mediaState: PLAYING | PAUSED | null
```

`Widget.action` and `Group.kind` are intentionally separate: a group does
not invent new action semantics, it re-interprets how the *same* widget
actions are dispatched to *multiple* targets.

## 4. Group semantics (the trickiest part — defined once, centrally)

All group math lives in one pure function per group kind, inside the
domain layer, called by a `GroupController`. UI layers only ever call
`GroupController.apply(group, originWidgetId, userInput)` — they never
compute deltas or target states themselves. This guarantees Android and
the (conceptually mirrored) Windows controller UI can't drift apart, and
is exactly what `docs/ARCHITECTURE.md`/spec item 36 requires.

### 4.1 RELATIVE_SLIDER (brightness/volume groups)

Given each member's current absolute value `v_i ∈ [0,100]`, and the
origin widget moved from `old` to `new`:

```
delta = new - old
for each member i (including origin):
    v_i' = clamp(v_i + delta, 0, 100)
```

Clamping is defined once as `clamp(x, 0, 100)`; it is **not** renormalized
across the group (i.e. if one member saturates at 100 while another has
headroom, the saturated one simply stays at 100 — no delta redistribution).
This matches the worked example in the spec:
`95, 80, 90` with `+20` → `100, 100, 100` (each clamped independently).

Only the member whose value actually changes has an `ACTION` sent to its
target device (a member already saturated in the direction of travel is
skipped to avoid redundant no-op traffic).

### 4.2 ABSOLUTE_TOGGLE (mute groups)

Desired group state is computed, then broadcast as one absolute command
per member:

```
desiredMuted = !allMembersCurrentlyMuted(group)
for each member:
    send SET_MUTED(desiredMuted) to member.targetDevice
```

i.e. "mute everything unless everything is already muted, in which case
unmute everything." This avoids blind toggles: a member's local state is
never inverted independent of the others.

### 4.3 ABSOLUTE_MEDIA (media groups)

Desired play state is chosen explicitly by the user action (a
play/pause **button pair**, not a single toggle, to avoid ambiguity), or
if the UI only exposes one button, the desired state is the opposite of
the majority current state across members:

```
desiredState = majority(members.map { it.mediaState }) == PLAYING ? PAUSED : PLAYING
for each member:
    send MEDIA_SET_STATE(desiredState) to member.targetDevice
```

`MEDIA_NEXT`/`MEDIA_PREVIOUS` in a group are edge-triggered and always
absolute-broadcast (sent to every member unconditionally — there is no
"state" to reconcile for a skip event).

## 5. Reconnect policy

Evaluated by the `GroupManager` (not the UI) whenever a device transitions
`OFFLINE -> ONLINE` and it is a member of one or more groups:

| Policy | Behavior on reconnect |
|---|---|
| `SYNC_GROUP_STATE` | The reconnecting member's value/state is overwritten with the group's current authoritative value (for `RELATIVE_SLIDER`: the shared post-delta baseline; for absolute kinds: the group's current desired state). An `ACTION` is sent to the reconnecting device. |
| `KEEP_DEVICE_STATE` | The reconnecting member keeps whatever value it drifted to while offline; the group's aggregate view is refreshed from its `STATE_UPDATE`/current value instead. No `ACTION` is sent. |
| `NO_ACTION` | Nothing is sent and the group's aggregate view is not recomputed from this member until the next explicit user interaction. |

Policy is a per-`Group` field, defaulting to `SYNC_GROUP_STATE`, editable
by the user per group.

## 6. Dashboard synchronization

- Each `Dashboard` carries a monotonically increasing `version`.
- On local edit, the editing device increments `version` by 1 and
  broadcasts `DASHBOARD_SYNC` to every currently-connected paired peer.
- On receipt, a peer applies the incoming dashboard **iff**
  `incoming.version > local.version` (or the dashboard doesn't exist
  locally yet). Otherwise it discards the incoming copy and (if
  `incoming.version < local.version`) replies with its own newer
  `DASHBOARD_SYNC` so the sender catches up — this is the entire
  last-write-wins rule, applied symmetrically, no vector clocks, no CRDTs.
- Equal versions with different content (a rare race where two devices
  edited "simultaneously" while briefly disconnected) are broken by
  comparing `timestamp` on the enclosing message as a tie-breaker, then by
  `sourceDeviceId` string ordering as a final deterministic fallback —
  documented here so both platforms implement the identical tie-break.
- `DASHBOARD_ACK` lets the sender know its edit was applied and is only
  used for diagnostics/logging, not for correctness.
- A newly-connected/paired peer receives a full `DASHBOARD_SYNC` for every
  dashboard the connecting device owns, immediately after `CAPABILITIES`
  is exchanged.

## 7. Offline handling

- A device with no open, authenticated WebSocket connection is `OFFLINE`
  in every peer's `State Manager`.
- Widgets targeting an offline device render disabled/greyed with an
  "offline" badge; they never block or hide the rest of the dashboard.
- Actions sent to an offline target are not queued for later delivery in
  the MVP (explicitly out of scope) — the UI simply prevents sending them
  and surfaces the offline state. Reconnect policy (§5) is what
  reconciles state once the device comes back, not a message queue.
- Discovery/transport failures never propagate as uncaught exceptions to
  the UI layer; they are surfaced as state (`OFFLINE`, `ERROR` log entries).

## 8. Repository layout

```
/
├── android/     Kotlin/Compose app. Gradle multi-module:
│                  :domain    (pure Kotlin, no Android deps — models, group math, dashboard versioning)
│                  :protocol  (pure Kotlin, kotlinx.serialization DTOs/codec matching protocol/PROTOCOL.md)
│                  :app       (Android app: Compose UI, NSD, Ktor WebSocket, Room/DataStore persistence)
├── windows/     .NET 8 solution:
│                  ControlDeck.Domain      (pure C#, no Windows deps — mirrors :domain)
│                  ControlDeck.Protocol    (pure C#, System.Text.Json DTOs matching protocol/PROTOCOL.md)
│                  ControlDeck.Agent       (WPF app + Windows platform actions, mDNS, WebSocket)
│                  *.Tests projects (xUnit) per project above
├── protocol/    PROTOCOL.md (source of truth for the wire format)
├── docs/        this file + any supplementary docs
├── .github/workflows/  CI (per platform) + release automation
├── README.md
└── LICENSE
```
