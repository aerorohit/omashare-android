# Omashare Android — DESIGN

Android client for the Omashare protocol. Sibling of the Rust repo `omashare`.
This repo is UI + platform glue only. Discovery, consent, and bytes stay in
`omashare-core` via UniFFI (`omashare-ffi`).

First iteration: **continuous sharing** between phone↔PC and **phone↔phone**.
No Wi-Fi / cellular restrictions.

## What we already have (sample in `omashare/android`)

- Kotlin `MainActivity` + `TransferService` (foreground, `DATA_SYNC`, no VPN)
- UniFFI `OmashareNode` + JSON `EventSink`
- `NsdManager` advertise/browse (Rust mDNS is off on Android)
- HTTP `GET /omashare/info` then `injectPeer`
- Single-file `GetContent`, copy-into-app staging, then `sendFile`
- Incoming offer dialog + copy-out to Downloads

This sample is a prototype. This repo replaces it with a small, themed app.
Do not fork LocalSend. Speak **Omashare only**.

## Lessons from AirDrop and LocalSend

### AirDrop

- Receiver is always on (`sharingd`). Opening the app is not “receive mode.”
- Discover → Ask (names/sizes) → user Accept/Decline → upload.
- Share sheet is a first-class send entry (files already chosen).

### LocalSend

- Same protocol on every device → **two phones just work**.
- Home screen = nearby devices + send.
- Receive: in-app prompt **and** notification actions (Accept / Decline).
- Multi-file; progress; cancel.
- Android needs extra discovery help (NSD / multicast lock) because Wi-Fi
  stacks drop random UDP.

### What we copy

| Behavior | v1 |
| --- | --- |
| Always listening | Foreground service as long as the user wants sharing on |
| Consent | Accept / Decline; no silent receive |
| Native picker | System document UI, not an in-app file tree |
| Share sheet | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` |
| Phone ↔ phone | Same node on both; NSD + Iroh |
| Relays | On, so a path change does not stop the transfer |

### What we skip (v1)

- “Wi-Fi only” / block cellular (keep `MeteredPolicy::Allow`; auto-consent if
  the core still emits `MeteredConsentNeeded`)
- Quick Share / Nearby Share / LocalSend interop
- Pairing, PIN UI (optional later; core already supports PIN)
- Folders as nested trees, media gallery of our own

## Architecture

```
┌─────────────────────────────────────────┐
│  Compose UI  (nearby, offer, progress) │
│  Share activity  ·  file picker         │
└───────────────┬─────────────────────────────┘
                │ binder / app singleton
┌───────────────▼─────────────────────────────┐
│  ShareService  (foreground)                 │
│  NsdDiscovery  ·  BleDiscovery  ·  multicast lock │
│  UniFFI OmashareNode  (same core as CLI)    │
└───────────────┬─────────────────────────────┘
                │ Omashare protocol
                ▼
         PC agent  or  other phone
```

Build: this repo vendors or CI-builds `libomashare_ffi.so` from `omashare`
(`cargo ndk -t arm64-v8a`). UniFFI Kotlin is generated, not hand-written.

### Why a separate repo

- Android Gradle / NDK lifecycle is noisy; keep it off the CLI crate graph.
- App releases independently of the Linux package.
- Protocol changes still land in `omashare` first; this app consumes them.

## Protocol (consumed, not defined here)

From `omashare` README / `protocol.rs`:

- Discovery UDP/TCP **53318**, multicast `224.0.0.169`, `GET /omashare/info`
- BLE: service `6f6d6173-6861-7265-0000-000000005318`, GATT Presence on `…5319`
  (handshake only; bytes still iroh LAN/WAN/relay)
- Offer ALPN `omashare/offer/0`
- Payload `iroh-blobs`
- Device kind: `mobile`

Phone↔phone requirements (must work with two copies of this app):

1. Both run `ShareService` (or the equivalent always-on node).
2. Both **advertise and browse** NSD (`_omashare._udp`).
3. Both hold a Wi-Fi multicast lock while sharing is on.
4. `injectPeer` with Iroh `addrs` from `/omashare/info` so magicsock can go LAN.
5. Relays enabled so AP isolation / bad multicast still has a fallback.
6. BLE advertise/scan while sharing is on; GATT Presence → `injectPeer` (`bluetooth`)
   so two phones meet without the same Wi-Fi, then transfer over WAN/relay.

If two phones on the same LAN cannot see each other, fix NSD + info fetch +
addr rewrite first — not the UI.

## App UX (v1)

Omarchy-like: dark, quiet, few controls. One main screen.

```
  Omashare                    [sharing on]
  ────────────────────────────────────────
  Nearby
    Pixel 8          mobile
    omarchy-box      desktop
  ────────────────────────────────────────
  [ Send files ]
```

Flows:

1. **Send** — `OpenMultipleDocuments` (`*/*`). Then tap a peer. Show hashing /
   offering / progress / done. Cancel on the same screen.
2. **Receive** — full-screen (or dialog) with alias, file names, sizes.
   Accept / Decline. Progress. Files land in public Downloads/`Omashare`.
3. **Share sheet** — files already selected; this screen is only the peer list.
4. **Empty nearby** — one line: “Waiting for devices on this network…”
   No fake radar animation. BLE-discovered peers appear in the same list.

Sharing stays on while the service runs. A single toggle in a tiny overflow
menu: Stop sharing (stops the service). v1 does not add network-type toggles.

Notifications:

- Persistent low-priority: “Omashare is sharing”
- Offer: Accept / Decline actions (must work when the activity is dead)
- Transfer complete / failed

Permissions (minimum): notifications, nearby Wi-Fi / location as required
by `NsdManager` on the target SDK, Bluetooth scan/advertise/connect (never for
location), read URIs via SAF. No VPN permission.

## Visual design (Omarchy)

Omarchy is a dark, terminal-first Arch desktop (Hyprland). The app should feel
like that machine, not Material green / generic MD3 purple.

**v1 tokens** — Catppuccin Mocha (stock Omarchy theme `catppuccin`):

| Token | Hex | Use |
| --- | --- | --- |
| background | `#1e1e2e` | Window |
| surface | `#313244` | Cards, list rows |
| overlay | `#45475a` | Selection |
| text | `#cdd6f4` | Primary |
| muted | `#6c7086` | Secondary |
| accent | `#89b4fa` | Buttons, active peer, progress |
| green | `#a6e3a1` | Complete |
| red | `#f38ba8` | Decline / fail |
| yellow | `#f9e2af` | In progress |

Type: one sans (system `sans-serif` is fine). Large enough tap targets.
No gradients, no glass, no mascot splash. Hairline dividers, 8dp rhythm.
Accent used sparingly (primary button + progress).

Compose Material3 with a **custom ColorScheme** from these tokens, not the
default dynamic wallpaper scheme.

## Code layout (keep small)

```
app/
  ui/          MainScreen, OfferSheet, theme
  service/     ShareService, NsdDiscovery, BleDiscovery
  share/       Send/Share activities, SAF staging
  ffi/         generated UniFFI (generated/)
```

Rules:

- One foreground service, one node.
- UI on main thread; FFI calls on a small executor.
- Prefer streaming / copy-to-cache only when the blob API needs a real path.
  Multi-file: stage then `send_files` (CLI FFI work in the other repo).
- No network-type `if` around send/receive in v1.
- Delete the sample `ListView` XML UI; Compose only.

## Implementation order

1. Repo skeleton: Compose, theme tokens, empty nearby screen.
2. Link `omashare-ffi` (arm64). Start/stop `ShareService`, persist identity.
3. NSD + `injectPeer` + live nearby list (verify **two phones** here).
4. Incoming offer UI + notification actions + save to Downloads.
5. Native multi-file picker + send + progress + cancel.
6. Share-sheet entry. Manual matrix: phone↔PC, phone↔phone, app backgrounded.

## Non-goals (v1)

- Cellular / Wi-Fi policy UI
- iOS
- Custom in-app file manager
- Changing the wire protocol (file issues upstream in `omashare`)

## Code rules

- Minimal: no extra DI framework, no multi-module Gradle unless NDK forces it.
- Fast: no bitmap decode of every send file on the UI thread; progress is events.
- Clean: generated UniFFI stays generated; do not edit it.
