# ABC-Vorschul App

Free, ad-free Android preschool app (ages 4–7) for German reading, speaking, and math practice.
Dark-only UI, offline after install, mixed five-task sessions over a shared content graph.

## Requirements

- JDK 17+
- Android SDK (compileSdk 36)
- Android emulator or device (German TTS voice recommended)

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install:

```bash
./gradlew :app:installDebug
```

## Manual offline session smoke

1. Enable airplane mode.
2. Launch **ABC-Vorschul App** — dark home/practice shell appears.
3. Complete a five-task session spanning reading, speech (“Sprich mit!” cue), and math.
4. Long-press **⋯** (~1.5s) → switch to **Mit Hilfe**, confirm scaffolds/silhouettes.
5. Force a wrong math answer twice → **Auflösen** → no points for resolve.
6. Background mid-session and reopen → session resumes at the next task index.
7. Finish session → reward summary → **Weiter** starts a new mix.

## Product notes

- No ads, no network permission required for core practice.
- Parent difficulty: Auto / Mit Hilfe / Ohne Hilfe behind the long-press gate.
- Content pack: `app/src/main/assets/content/`.
