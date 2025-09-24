# Jumping Game 2D

A production-grade, Android-first Kotlin multiplatform prototype of a vertical platformer inspired by Doodle Jump. The repository is structured around a deterministic :core simulation engine and a Jetpack Compose renderer that demonstrates a playable loop on Android.

```
+-----------------------------+
|         androidApp          |
|  Compose UI, sensors, SFX   |
|   |                         |
|   v                         |
|        GameViewModel        |
|            |                |
+------------|----------------+
             v
       +-----------+
       |   :core   |
       | deterministic engine |
       | RNG, systems, DTOs   |
       +-----------+
```

## Modules & Determinism Strategy

- `:core` – Kotlin Multiplatform module (commonMain + jvmTest) containing math utilities, deterministic RNG (SplitMix64), fixed timestep engine, world model, gameplay systems, and network DTO scaffolding for future multiplayer. The update order enforces `input → physics → collisions → spawns → score → tick++` for reproducibility.
- `:androidApp` – Android application with Jetpack Compose UI, accelerometer/touch input plumbing, SoundPool/BGM stubs, DataStore-backed settings, and analytics hooks. It consumes the simulation via `GameViewModel`, ensuring all gameplay decisions stay inside `:core`.

Determinism is preserved by:
- Running the simulation under a fixed 1/60s timestep accumulator.
- Feeding seeded `WorldRandom` instances everywhere procedural content is generated.
- Avoiding wall-clock time, random APIs, or per-frame allocations within `:core` hot paths.

## Getting Started

Prerequisites: JDK 17, Android Studio Koala (or newer), Android SDK 35.

```bash
./gradlew tasks          # verify the toolchain
./gradlew :androidApp:installDebug  # deploy to a device or emulator
```

### Running Tests & Quality Gates

```bash
./gradlew test                 # JVM tests (determinism checks)
./gradlew ktlintCheck          # Kotlin style
./gradlew detekt               # Static analysis
./gradlew :androidApp:lint     # Android lint
./gradlew :androidApp:assembleRelease  # Play-ready bundle
```

CI (GitHub Actions) runs the full suite: build, tests, lint, detekt, and assembles the release artifact.

## Architecture Highlights

- **Simulation core**: Allocation-free world updates, platform pooling, deterministic RNG, and future-facing network DTOs plus prediction buffer stubs.
- **Rendering**: Compose `Canvas` renderer that scales and flips world coordinates for an “upward” camera while overlaying HUD elements.
- **Input**: Accelerometer tilt and touch gestures wired into the deterministic input model. Settings allow tuning tilt sensitivity and toggling music.
- **Audio & Analytics**: SoundPool wrapper and BGM stub stand ready for asset injection. Analytics registry exposes a single place to swap in Firebase Crashlytics/Analytics instances without leaking keys into source control.

## Roadmap

- Ghost replays seeded from deterministic session logs.
- Client/server lockstep with real websocket transport, using the existing DTOs and prediction buffers.
- Optional LibGDX (or other platform) renderer pointing at the same `:core` engine for desktop experimentation.

## Contributing

1. Follow Conventional Commit messages.
2. Keep gameplay logic in `:core`; platform-specific code lives in its module.
3. Run `./gradlew ktlintFormat detekt` before opening a PR.
4. Ensure deterministic tests remain green when touching simulation code.

MIT licensed. Have fun jumping!
