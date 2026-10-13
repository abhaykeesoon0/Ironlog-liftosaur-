# Liftosaur

Android strength-training tracker built for a Mobile Application Development
coursework assignment. (Formerly IronLog; the package `com.ironlog.app`, the
database, the preference files and the `IronLog` log tag keep the original
name so existing installs and backups carry over.)

The app is offline by design. There is no backend: accounts, tokens and the
whole training log live on the phone. The only network calls are keyless:
the coach's web lookup, which sends the topic of a question and is on by
default, and the optional ones the user turns on (food lookup and the online
exercise library).

## Features

### Training

- **Routines** — build routines from a 200-movement library (plus custom
  exercises) with sets, rep ranges, rest and a target weight per exercise.
  Double progression moves the target up when a session earns it and back
  when it clearly did not. A routine generator builds a plan from the
  user's focus, days per week and equipment.
- **Workout logger** — previous-session values beside every set, warm-up /
  drop / failure set types, live PR badges, a plate calculator for the bar,
  an accelerometer rep counter, spoken set entry ("100 kilos for 8"), and a
  foreground-service rest timer with notification controls and a Quick
  Settings tile. Repeat any past workout as a new one.
- **History** — every session with sets, volume, duration and records;
  deleting a session rebuilds the affected personal records. A session can
  be shared as text or as a rendered image card.
- **Progress** — weekly volume, muscle-group split, daily steps, records,
  per-exercise charts of top weight and estimated 1RM, and a body map of
  what was trained this week or any week before it.
- **Strength goals** — a target weight or estimated 1RM per exercise, with
  progress on Home and a chart per goal.
- **Coach** — an offline question-and-answer coach with a recovery model per
  muscle. It looks facts up on its own through a keyless web lookup
  (DuckDuckGo, then Wikipedia), on by default and switched off with one chip
  on the Coach screen; only the topic of the question is sent, never the log.
  Two optional models sit on top of it, each off until the user turns it on:
  on-device Gemini Nano where the phone has it, and a language model
  downloaded once from a public repository (Qwen 2.5, 550 MB or 1.6 GB) and
  run on the handset by MediaPipe with no key and no connection. Both read the
  same brief: the user's own figures and the web extract. No cloud model and
  no API key anywhere.

### Body and cardio

- **Body** — measurements with trend charts; progress photos from the camera
  or photo picker, kept in private app storage only, with a side-by-side
  compare view. The photos screen blocks screenshots.
- **Outdoor cardio** — GPS route tracking in a foreground service with
  distance, pace, elevation, steps, an estimated calorie figure and, when a
  Bluetooth LE heart-rate strap is paired, live and average heart rate. The
  route is drawn live on a real map from OpenStreetMap tiles (osmdroid, no
  key), following the runner, and every saved session shows its route on the
  map. The tile server only ever sees which map area was viewed; the route
  itself never leaves the phone.
- **Nutrition** — a food log with a barcode scanner (on-device ML Kit, looked
  up on Open Food Facts), daily targets, and a meal-plan generator.

### Around the app

- **Home** — a dashboard the user can reorder or hide section by section.
- **Rewards** — quests that earn coins, and a store that spends them on
  cosmetics.
- **Home-screen widget** — this week's sessions, streak and today's food.
- **Launcher shortcuts** — start training, log food, ask the coach.
- **Health Connect** — reads steps, writes exercise, distance and nutrition.
- **Import** — Strong and Hevy CSV exports, matched by exercise name. A
  backup or CSV shared from another app, or opened from a file manager,
  lands straight on the right import dialog.
- **Moving phones** — a backup carries the account itself (email plus the
  password hash and salt, never the password) along with workouts, routines,
  measurements, cardio, the food log, goals, rewards and settings. On a new
  phone, "Moving from another phone? Restore a backup" on the sign-in screen
  recreates the account from the file, and the user signs in with the
  password they already know. There is no server: the phone is the account.
- **Export and backup** — CSV for workouts, measurements and cardio; a plain
  JSON backup; a passphrase-sealed encrypted backup for moving phones; and a
  weekly automatic encrypted backup kept in private storage, with a one-tap
  restore of the latest copy. Optionally, each weekly backup is also written
  to a folder the user picks in Google Drive, OneDrive, Dropbox or any other
  storage app, through the Storage Access Framework. The app makes no
  network call of its own; the provider syncs a file it cannot open, and the
  latest cloud copy can be restored in one tap on a new phone.

## Security

- **Email verification** — a new account must open the link in a
  verification email before it can be used. The email is sent by Firebase
  Authentication, spoken to over its REST API with the app's own HTTP code
  (no SDK): Firebase holds the email, a credential and the verified flag,
  and nothing else. The Firebase web API key is read from
  `local.properties` (`firebase.webApiKey=...`); without one, the app builds
  and runs with verification switched off. Accounts that predate the check
  are treated as verified.
- **Passwords** are hashed with PBKDF2-HMAC-SHA256 (120,000 iterations,
  random salt) and compared in constant time. They travel through the app
  as `CharArray` and are wiped after use.
- **Sessions** are HMAC-SHA256 signed tokens: a 30-minute access token and a
  30-day refresh token with silent refresh, stored in `EncryptedSharedPreferences`
  under an Android Keystore master key.
- **The database** is encrypted at rest with SQLCipher; a plaintext database
  from an older install is migrated into the encrypted one on first launch.
- **Backups** are sealed with AES-256-GCM under a PBKDF2-stretched passphrase,
  with a fresh salt and nonce per file. The automatic weekly backup keeps its
  passphrase in the same Keystore-encrypted store as the database key.
- **App lock** — optional biometric or device-credential lock that re-locks
  after a configurable grace period (15 s by default) in the background.
- **Sign-in throttling** — five free attempts, then a back-off that starts at
  30 s and caps at 15 minutes.
- **Screenshots** — the progress-photo screen is always uncapturable, and an
  optional setting extends that to every screen, including the recents
  thumbnail.
- **Input** — every free-text field is sanitised before storage, and anything
  that can reach a CSV export is guarded against formula injection.
- **Account deletion** requires the password and removes every row, photo,
  export and backup for the account.
- Cloud backup and device transfer are disabled in the manifest, so health
  data never leaves the handset unless the user exports it.

## Device integration

| Hardware or platform feature | Used for |
|---|---|
| Camera | Progress photos, avatar, barcode scanning |
| GPS | Cardio route, distance, pace, elevation |
| Accelerometer | Rep counting |
| Step counter | Daily steps, in-session steps |
| Microphone | Spoken set entry |
| Bluetooth LE | Heart-rate strap during cardio |
| Notifications | Rest timer, cardio status, daily reminder |
| Quick Settings tile | Rest timer |
| Home-screen widget | Weekly summary |
| Health Connect | Steps in; exercise, distance and nutrition out |
| WorkManager | Daily reminder, weekly automatic backup |
| Biometrics | App lock |

Every runtime permission shows an in-app rationale before the system dialog,
so a cold system prompt never causes a permanent denial.

## Running it

1. Open the project folder in Android Studio (File > Open — do **not** use New Project)
2. Let Gradle sync; it will download the Android and Compose dependencies
3. Run on a **physical device** for the full experience. The emulator has no
   step counter, a stubbed accelerometer and no Bluetooth, so the rep counter,
   daily steps and heart-rate strap will not work there.

Requires JDK 17 and Android SDK 35. minSdk 26.

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # install on a connected device or emulator
adb logcat -s IronLog:V          # app logs
```

The debug build installs as `com.ironlog.app.debug`, so it can sit beside a
release install.

### Release build

```bash
./gradlew assembleRelease
```

Produces one signed, shrunk APK per CPU architecture under
`app/build/outputs/apk/release/`, plus a universal one. Install the
`arm64-v8a` APK on any modern phone (about 27 MB to download; the native
libraries, half of them the coach model's inference runtime, are stored
compressed and extracted on install); the universal APK (about 70 MB) works
on anything.

Signing reads `keystore.properties` in the project root, which names the
keystore file and holds its passwords. Both are ignored by git. Without the
file the release build still runs but produces an unsigned APK that a phone
will refuse to install. Keep a private copy of the keystore: an APK signed
with a different key cannot update an existing install.

## Architecture

MVVM throughout: Composable screen → ViewModel → Repository → DAO → Room.
Hilt provides dependencies. Every repository method returns a `Result`, and
ViewModels turn failures into user-facing messages; nothing is silently
swallowed. Weights are stored in kilograms and converted for display only.
See `CONVENTIONS.md` for the conventions the codebase follows.

## Tests

```bash
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests, needs a device or emulator
```

Unit tests cover the training maths (1RM, volume, progression, plate loading,
streaks), PR detection rules, goal maths, the recovery and prediction models,
the routine and meal-plan generators, the spoken-set parser, CSV import,
input validation and CSV-injection guarding, GPS maths, and a ViewModel
driven through a faked repository.

Instrumented tests cover every database migration from version 1, the DAO
aggregate queries (including the rule that warm-up sets never count), the
workout repository from start to finish with real PR detection, and the app
as a new user meets it: introduction, account creation, Home, and the
privacy page.
