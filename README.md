# Liftosaur

Android strength-training tracker built for a Mobile Application Development
coursework assignment. (Formerly IronLog; the package `com.ironlog.app` and
the `IronLog` log tag keep the original name.)

## Features

- **Accounts & security** — registration and sign-in with PBKDF2 password hashing,
  HMAC-signed session tokens in Keystore-backed encrypted storage with silent
  refresh, optional biometric app lock (re-locks after 15 s in the background),
  password change, and password-confirmed account deletion
- **Routines** — build routines from a 70-movement library (plus custom
  exercises) with sets, rep ranges and rest per exercise
- **Workout logger** — previous-session values beside every set, warm-up/drop/
  failure set types, live PR badges, foreground-service rest timer with
  notification controls, accelerometer rep counter, plate calculator
- **History** — every session with sets, volume, duration and records; deleting
  a session rebuilds the affected personal records
- **Progress** — weekly volume, muscle-group split, daily steps, records, and
  per-exercise charts of top weight and estimated 1RM
- **Body** — measurements with trend charts; progress photos from the camera or
  photo picker, kept in private app storage only
- **Outdoor cardio** — GPS route tracking in a foreground service with distance,
  pace, elevation, steps and an estimated calorie figure
- **Settings** — metric/imperial units, rest-timer defaults, weekly goal, daily
  reminder (WorkManager), live permission status, CSV/JSON export via share sheet

### Device integration

| Hardware | Used for |
|---|---|
| Camera | Progress photos, avatar (FileProvider into private storage) |
| GPS | Cardio route, distance, pace, elevation |
| Accelerometer | Rep counting |
| Step counter | Daily steps, in-session steps |
| Notifications | Rest timer, cardio status, daily reminder |
| Biometrics | App lock |

Every runtime permission shows an in-app rationale before the system dialog.

## Running it

1. Open the project folder in Android Studio (File > Open — do **not** use New Project)
2. Let Gradle sync; it will download the Android and Compose dependencies
3. Run on a **physical device**. The emulator has no step counter and a stubbed
   accelerometer, so the rep counter and daily step features will not work there.

Requires JDK 17 and Android SDK 35. minSdk 26.

## Architecture

MVVM throughout: Composable screen → ViewModel → Repository → DAO → Room.
Hilt provides dependencies. See `CLAUDE.md` for the conventions the codebase
follows.

## Tests

```bash
./gradlew testDebugUnitTest
```

Unit tests cover the training maths (1RM, volume, progression, plate loading,
streaks), input validation and CSV-injection guarding, and GPS maths.
