# REPORT

## Deviations Identified
- **Start roster missing from client model:** The Android client previously ignored the `players` roster required in the `start` message, so downstream UI never received the authoritative player list once the match began. This also left the local role state stale after master hand-off.
- **Snapshots never pruned vanished peers:** Remote player state was kept forever, even when the server broadcast a FULL snapshot without a given player, leading to ghost avatars after disconnects.
- **Socket hard to exercise in tests:** `NetController` directly depended on `RtSocket`, preventing deterministic protocol tests and leaving the roster/backpressure logic untested against the updated spec.

## Fixes Delivered
- Added the roster payload to `S2CStart`, persisted it in `NetController`, and wired it into `NetState` so UI and character selection continue using the authoritative list during the running phase.
- Updated `NetController` to rebuild its roster from lobby/start messages, react to role transfers, and drop remote players that disappear from FULL snapshots.
- Introduced the `RtSocketClient` interface, allowing an in-memory fake for protocol unit tests and new coverage that ensures the first post-start snapshot is processed and remote peers are removed when absent from FULL snapshots.
- Extended the shared wire definitions with the optional `character_select` client message so the Android build matches `NETWORK_PROTOCOL.md` v1.2.1.
