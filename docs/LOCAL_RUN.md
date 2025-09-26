# Local Run Guide

The Android client expects the lobby REST API and realtime WebSocket described in `NETWORK_PROTOCOL.md` (pv=1) to be available on localhost.

## Prerequisites
- Java 17+
- Android SDK (compileSdk 35) if you plan to build the client locally.
- A server implementation that honors `NETWORK_PROTOCOL.md` v1.2.1, exposing:
  - `http://localhost:8080` for REST endpoints (e.g., `/v1/rooms`, `/v1/rooms/{id}/start`).
  - `ws://localhost:8081/v1/ws` for realtime traffic.

The Android build scripts already point to these URLs through `BuildConfig.API_BASE` and `BuildConfig.WS_URL`. When running on an emulator, these resolve to `10.0.2.2`, forwarding to the host machine.

## Steps
1. Start your backend server locally so that REST is reachable at `http://localhost:8080` and the WebSocket endpoint at `ws://localhost:8081/v1/ws`.
2. In Android Studio (or via Gradle), build and install the `androidApp` module.
3. Launch the app; create or join a room. The client will connect to the configured WebSocket and wait for the `start` payload (including the roster) before accepting inputs.

## Testing
- Run `./gradlew test` to execute the shared/unit tests. (An Android SDK must be present or Gradle will abort with an SDK error.)
