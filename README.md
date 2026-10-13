# Liftosaur

An offline-first Android strength-training tracker: the whole log lives in an
encrypted database on the phone, twelve device and platform features are used
behind in-context permissions, and a coach answers from the lifter's own
figures, with an optional language model that runs on the handset.

[![Android CI](https://github.com/abhaykeesoon0/Ironlog-liftosaur-/actions/workflows/android.yml/badge.svg)](https://github.com/abhaykeesoon0/Ironlog-liftosaur-/actions/workflows/android.yml)

Built by Abhay Keesoon for a Mobile Application Development module, which
marks on UI/UX, Functionality, Device Integration and Security. Kotlin,
Jetpack Compose, Room with SQLCipher, Hilt. (Formerly IronLog; the package
`com.ironlog.app`, the database, the preference files and the `IronLog` log
tag keep the original name so existing installs and backups carry over.)

![Home after eight weeks of training: this week and the muscle map, the recovery card, goals, recent workouts and bodyweight](docs/figures/home.jpg)

## Download

Everything a marker needs is attached to the
[latest release](https://github.com/abhaykeesoon0/Ironlog-liftosaur-/releases/latest):

- `Liftosaur-release-arm64-v8a.apk`: the signed release build for any modern
  phone (26.5 MB). `Liftosaur-release-universal.apk` (70 MB) is the same build
  for any other architecture, including Intel emulators.
- `Liftosaur-demo.mp4`: an 8 min 52 s walk through the whole app on a Galaxy
  S25 Ultra. 0:00 Home, Routines, the generator and editor; 1:22 a workout
  from start to finish; 3:22 History, Progress, goals, the coach and rewards;
  5:23 Nutrition, body, cardio, Settings and the privacy page; 7:58 the
  introduction.
- `Liftosaur-report.pdf`: the coursework report.

The APK is signed with the project's own key, so Android asks once to allow
installs from the browser or file manager. The debug build from source
installs as `com.ironlog.app.debug` and can sit beside it.

## Screenshots

Figure numbers match the report. Most panels are from the Android 15 emulator
with eight weeks of training restored from a backup; Figures 1, 17 and 22 and
single frames of 11, 13 and 18 are from a Galaxy S25 Ultra on Android 16.

![Home in landscape](docs/figures/landscape.jpg)
*Figure 1. Home in landscape: the bottom bar becomes a navigation rail and the content is held to a readable width.*

![The logger](docs/figures/logger.jpg)
*Figure 2. The logger: last session's figures in grey beside each set, a PR badge and the rest bar after a tick, and the plate calculator sheet in competition colours.*

![Home](docs/figures/home.jpg)
*Figure 3. Home as a stack of cards: this week and the muscle map, recovery, goals, the last 14 workouts and bodyweight. Chalk is the only action colour; the plate colours mark data and gold marks records.*

![Introduction](docs/figures/onboarding.jpg)
*Figure 4. The three-page introduction: the log, where the data stays, and how permissions are asked for.*

![Empty states](docs/figures/empty_states.jpg)
*Figure 5. Empty states on a fresh account: each says what to do first.*

![Routines](docs/figures/routines.jpg)
*Figure 6. Routines: saved routines with one-tap start, the editor with its steppers, and the generator building a week from goal, days and session length.*

![History](docs/figures/history.jpg)
*Figure 7. History: level and streak, the month heatmap, the month's workouts, and the summary shown after finishing a workout.*

![Progress](docs/figures/progress.jpg)
*Figure 8. Progress: weekly volume and sessions, the muscle split with warm-ups excluded, and one lift session by session.*

![Exercise library and detail](docs/figures/exercises.jpg)
*Figure 9. The exercise library, where the play icon opens a movement demo, and an exercise detail with the top-weight and estimated 1RM charts and the predicted next top set.*

![Goals, coach and rewards](docs/figures/goals_coach_rewards.jpg)
*Figure 10. Strength goals with a chart per goal, the offline coach answering from the user's own recovery figures with its source named, and the quests and store.*

![Nutrition and body](docs/figures/nutrition_body.jpg)
*Figure 11. Nutrition: the day's log against the target, the Open Food Facts result for a packet scanned on the phone, a generated plan, and body measurements with trend charts.*

![Import and export](docs/figures/csv.jpg)
*Figure 12. A Strong or Hevy CSV opened from a file manager lands on the import dialog; the exported workouts CSV goes out through the share sheet via a FileProvider grant, so no storage permission is needed.*

![Permission flow](docs/figures/permission_flow.jpg)
*Figure 13. The permission flow for the barcode scanner: the app's own rationale first, the system dialog second, and the scanner only once granted. The third frame is the phone reading a real packet.*

![Rest timer, rep counter and microphone](docs/figures/rest_timer_sensors.jpg)
*Figure 14. The rest timer notification with +30s and Skip beside the lit Quick Settings tile, the accelerometer rep counter sheet, and the microphone rationale shown by the mic button.*

![Notifications](docs/figures/notifications.jpg)
*Figure 15. The cardio foreground service's ongoing notification, and the Rest over alert grouped with the daily reminder.*

![Outdoor cardio](docs/figures/cardio.jpg)
*Figure 16. Outdoor cardio with a replayed GPS route: the live screen following the runner, the saved session framed once with duration, pace, elevation and calories, and the session history.*

![Widget, shortcuts and steps on the phone](docs/figures/phone.jpg)
*Figure 17. On a Galaxy S25 Ultra: the home-screen widget, the three launcher shortcuts, and the Today card counting steps from the hardware step counter.*

![Sign in, throttling, registration and verification](docs/figures/auth.jpg)
*Figure 18. Sign in, the throttle banner after the fifth wrong password, registration, the verify-email gate, and sign-in after an account was restored from a backup.*

![Backups and the app lock](docs/figures/backup_security.jpg)
*Figure 19. Sealing a backup asks for a passphrase that is never stored; restoring adds and never replaces; the Security card with the app lock on.*

![Screenshot blocking, delete account, change password](docs/figures/destructive.jpg)
*Figure 20. A capture of the progress-photo screen comes back black by design; deleting the account needs the password; changing the password.*

![Settings, privacy page and Customise Home](docs/figures/settings_privacy.jpg)
*Figure 21. Settings lists every permission with its rationale and state; the privacy page names each service and what it receives; Customise Home reorders or hides every card.*

![Frames from the demo video](docs/figures/demo_frames.jpg)
*Figure 22. Eight moments from the demo video on the Galaxy S25 Ultra, with their positions in `Liftosaur-demo.mp4`.*

## Features

The app is offline by design. There is no backend: accounts, tokens and the
whole training log live on the phone. The only network calls are the coach's
keyless web lookup (on by default, sends only the topic), the verification
email at sign-up, and the keyless lookups the user turns on.

### Training

- **Routines** — build routines from a 225-movement library (plus custom
  exercises) with sets, rep ranges, rest and a target weight per exercise.
  Double progression moves the target up when a session earns it and back
  when it clearly did not. A routine generator builds a plan from the
  user's goal, days per week and session length.
- **Workout logger** — previous-session values beside every set, warm-up /
  drop / failure set types, live PR badges, a plate calculator for the bar,
  an accelerometer rep counter, spoken set entry ("sixty kilos for eight"),
  and a foreground-service rest timer with notification controls and a Quick
  Settings tile. Repeat any past workout as a new one.
- **Records** — heaviest weight, best estimated 1RM, best set volume and
  most reps per exercise, plus a session-volume record. Warm-ups never count.
- **History** — every session with sets, volume, duration and records;
  deleting a session rebuilds the affected personal records. A session can
  be shared as text or as a rendered image card.
- **Progress** — weekly volume, muscle-group split, daily steps, records,
  per-exercise charts of top weight and estimated 1RM with a predicted next
  top set, and a body map of what was trained this week or any week before.
- **Strength goals** — a target weight or estimated 1RM per exercise, with
  progress on Home and a chart per goal.
- **Coach** — an offline question-and-answer coach with a recovery model per
  muscle. It looks facts up through a keyless web lookup (DuckDuckGo, then
  Wikipedia), on by default and switched off with one chip; only the topic
  of the question is sent, never the log. Two optional models sit on top,
  each off until enabled: on-device Gemini Nano where the phone has it, and
  a language model downloaded once (Qwen 2.5, 550 MB or 1.6 GB) and run on
  the handset by MediaPipe. No cloud model and no API key.

### Body and cardio

- **Body** — measurements with trend charts; progress photos from the camera
  or photo picker, kept in private app storage only, with a side-by-side
  compare view. The photos screen blocks screenshots.
- **Outdoor cardio** — GPS route tracking in a foreground service with
  distance, pace, elevation, steps, an estimated calorie figure and, when a
  Bluetooth LE heart-rate strap is paired, live and average heart rate. The
  route is drawn live on a real map from OpenStreetMap tiles (osmdroid, no
  key), and every saved session shows its route. The tile server only ever
  sees which map area was viewed; the route itself never leaves the phone.
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
  recreates the account, and the user signs in with the same password.
- **Export and backup** — CSV for workouts, measurements and cardio; a plain
  JSON backup; a passphrase-sealed encrypted backup for moving phones; and a
  weekly automatic encrypted backup kept in private storage, with a one-tap
  restore of the latest copy. Optionally each weekly backup is also written
  to a folder the user picks in Google Drive, OneDrive, Dropbox or any other
  storage app through the Storage Access Framework; the provider syncs a
  file it cannot open, and the latest cloud copy restores in one tap.

## Architecture

![MVVM layers and core services](docs/figures/architecture.png)

A single activity hosts one Compose navigation graph, and every feature
follows the same path:

```
Screen (composable) → ViewModel → Repository → DAO → Room (SQLCipher)
```

- A screen observes its ViewModel's state flow and never touches a
  repository directly.
- Every repository method that can fail returns a `Result<T>` (or a sealed
  result type). ViewModels turn failures into user-facing messages; nothing
  is silently swallowed and no bare `try { } catch { }` logs and continues.
- Hilt provides every dependency; KSP runs the Room and Hilt processors.
  App state lives only in Room or in a ViewModel; the `core` packages are
  cross-cutting infrastructure that screens and repositories inject.
- The Room database is at schema version 8 and holds 18 tables. Every
  foreign key cascades, so deleting the account removes every row it owns.
  Each migration from version 1 is kept in the app and validated against the
  exported schemas in `app/schemas/` by an instrumented test.
- Weights are stored in kilograms and converted for display only. Warm-up
  sets are excluded from volume and records through one property,
  `SetType.countsTowardVolume`, mirrored in the aggregate SQL.

Directory layout under `app/src/main/java/com/ironlog/app/`:

```
core/         cross-cutting infrastructure
  database/     Room database + type converters
  security/     password hashing, JWT, token store, biometrics, validation
  permissions/  runtime permission model and Compose request flow
  notifications/ channels, rest timer foreground service
  sensors/      accelerometer rep counter, hardware step counter
  location/     fused location tracking, cardio foreground service, geo math
  media/        camera photo storage, CSV/JSON export
data/
  entity/       Room entities
  dao/          DAOs and relation POJOs
  repository/   repository interfaces + implementations
domain/       models and use cases (1RM, PR detection, volume aggregation)
ui/
  theme/        colour, type, spacing tokens
  components/   shared composables
  navigation/   nav graph and bottom bar
  screens/      one package per feature area
```

`CONVENTIONS.md` lists the rules the code follows.

## Security

The threat model is a lost or borrowed phone, and a backup file in the wrong
hands. Each claim below names the file where it can be checked, most of
them under `core/security/`; `SECURITY.md` has the longer write-up.

- **Passwords** — PBKDF2-HMAC-SHA256 at 120,000 iterations with a random
  16-byte salt per account, compared in constant time (`PasswordHasher.kt`).
  Passwords travel as `CharArray` and are wiped after use; a password change
  issues a new salt and invalidates every session.
- **Sessions** — HMAC-SHA256 signed tokens minted and verified on the device:
  a 30-minute access token and a 30-day refresh token (`TokenService.kt`),
  stored in `EncryptedSharedPreferences` under an Android Keystore master
  key, AES-256-SIV for keys and AES-256-GCM for values (`SecureTokenStore.kt`).
- **Database** — encrypted at rest with SQLCipher; the key is generated once
  per device and kept in the same Keystore-backed store
  (`DatabaseEncryption.kt`). A plaintext database from an older install is
  migrated into the encrypted one on first launch.
- **Backups** — sealed with AES-256-GCM under a key stretched from the
  passphrase with PBKDF2 at 120,000 iterations, a fresh salt and nonce per
  file, and the header authenticated so a file cannot be relabelled
  (`BackupCrypto.kt`). A cloud folder only ever holds ciphertext.
- **Email verification** — a new account must open the link in a
  verification email. Firebase Authentication sends it, spoken to over its
  REST API with the app's own HTTP code, no SDK (`EmailVerifier.kt`); it
  holds the email, a credential and the verified flag, nothing about
  training. The key is read from `local.properties` (`firebase.webApiKey=`);
  without one the app builds with verification off.
- **Network** — cleartext is blocked. Each of the five hosts the app reaches
  for a lookup or the verification email (Firebase Authentication, Open Food
  Facts, wger, DuckDuckGo, Wikipedia) is pinned to its certificate authority's
  root in `res/xml/network_security_config.xml`; OpenStreetMap tiles and the
  model download use the system trust store over HTTPS. The pins expire on
  24 September 2027, after which the platform falls back to ordinary
  validation.
- **Sign-in throttling** — five free attempts, then a back-off from 30 s to
  a 15-minute cap, keyed by a hash of the email. One shared message for a
  wrong password and a missing account.
- **App lock** — optional biometric or device-credential lock that re-locks
  after a grace period (15 s by default) in the background.
- **Screenshots** — the progress-photo screen is always uncapturable; an
  optional setting extends that to every screen and the recents thumbnail.
- **Input** — every free-text field is sanitised through `Validators.kt`;
  anything that can reach a CSV export is quoted with a leading `=`, `+`,
  `-` or `@` neutralised, so a note cannot run as a spreadsheet formula.
- **Permissions and storage** — no background location, no storage
  permission, none held for a feature not in use. Cloud backup and
  device-to-device transfer are disabled in the manifest. Account deletion
  requires the password and removes every row, photo, export and backup.
- **What leaves the phone** — by default only the topic of a coach question,
  sent without any account or key. Everything else leaves only when the
  user turns a feature on: a barcode, a search term, a map area, a sealed
  backup to a folder the user chose, and the email and password at sign-up,
  to Firebase Authentication only. The log never leaves the device in clear.

## Device integration

| Hardware or platform feature | Used for |
|---|---|
| Camera | Progress photos, avatar, barcode scanning |
| GPS | Cardio route, distance, pace, elevation |
| Accelerometer | Rep counting |
| Step counter | Daily steps, in-session steps |
| Microphone | Spoken set entry |
| Bluetooth LE | Heart-rate strap during cardio |
| Notifications | Rest timer, cardio status, daily reminder, model download |
| Quick Settings tile | Rest timer |
| Home-screen widget | Weekly summary |
| Launcher shortcuts | Start training, log food, ask the coach |
| Health Connect | Steps in; exercise, distance and nutrition out |
| WorkManager | Daily reminder, weekly automatic backup, model download |

Every runtime permission shows an in-app rationale before the system dialog,
through `rememberPermissionState(AppPermission.X)` and `runWithPermission { }`,
so a cold system prompt never causes a permanent denial. Each is used only
while its feature runs: cardio tracking is a location-type foreground
service, the rest timer a special-use one. Biometrics back the app lock.

## Build and run

Requires JDK 17 and Android SDK 35 (compileSdk and targetSdk 35, minSdk 26,
so Android 8.0 and above). The wrapper pins Gradle 9.3.0; the version
catalog pins AGP 8.13.2, Kotlin 2.3.21, KSP 2.3.12 and Hilt 2.58. CI
(`.github/workflows/android.yml`) runs `testDebugUnitTest`, `lintDebug` and
`assembleDebug` on every push and pull request to `main` or `kotlin-upgrade`.

```bash
./gradlew assembleDebug              # debug APK
./gradlew installDebug               # install on a connected device or emulator
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests, needs a device or emulator
./gradlew lint                       # static analysis
adb logcat -s IronLog:V              # app logs (the tag kept the old name)
```

1. Open the project folder in Android Studio (File > Open — do **not** use
   New Project) and let Gradle sync.
2. A clean clone builds as it is. The Firebase key (`local.properties`) and
   the signing key (`keystore.properties` and the keystore) are git-ignored:
   without them the app builds with email verification off and the release
   APK is unsigned. Outside Android Studio, point Gradle at the SDK with
   `ANDROID_HOME`.
3. Run on a **physical device** for the full experience. The emulator has no
   step counter, a stubbed accelerometer and no Bluetooth, so the rep
   counter, daily steps and heart-rate strap will not work there.

The debug build installs as `com.ironlog.app.debug`, so it can sit beside a
release install.

### Release build

`./gradlew assembleRelease` produces one signed, minified APK per CPU
architecture under `app/build/outputs/apk/release/`, plus a universal one.
Native libraries are stored compressed, so the `arm64-v8a` APK is 26.5 MB to
download and the universal APK 70 MB. Signing reads `keystore.properties`,
which names the keystore and holds its passwords; the key is 4096-bit RSA and
both files are ignored by git. Keep a private copy of the keystore: an APK
signed with a different key cannot update an existing install.

## Testing

| Level | Count | What it covers |
|---|---|---|
| JVM unit tests | 170 | Training maths (1RM, volume, plates, streaks), PR detection rules, double progression, goal maths, recovery and prediction models, routine and meal-plan generators, the spoken-set parser, CSV import, input validation and CSV-injection guarding, GPS maths, the backup format across versions, the Firebase response parsers, the model catalogue and file hashing, and a ViewModel driven through a faked repository |
| Instrumented tests | 9 | Every database migration from version 1 to 8 validated against the exported schemas; DAO aggregate queries on an in-memory database, including the rule that warm-up sets never count; the workout repository from start to finish with real record detection; and the app as a new user meets it, from the introduction to Home and the privacy page |
| Manual on device | Emulator (Android 15) and Galaxy S25 Ultra (Android 16) | Sign-up with a live verification email, a replayed 2.4 km GPS run on the map, the on-device model answering, backup export and restore on a wiped install, share-into-app import, screenshot blocking, cloud-folder copy and restore; on the phone, the widget, shortcuts, hardware step counter, camera scanner and landscape navigation rail |

Android lint reports 0 errors. The instrumented tests check the `Result`
rule by asserting on the message text of expected failures. Unit tests live
under `app/src/test/`, instrumented tests under `app/src/androidTest/`.

## Project documents

- [Latest release](https://github.com/abhaykeesoon0/Ironlog-liftosaur-/releases/latest):
  the signed APKs, the demo video `Liftosaur-demo.mp4` and the report PDF.
- [`SECURITY.md`](SECURITY.md): threat model, controls, what leaves the
  phone, and how to report a vulnerability.
- [`LICENSE`](LICENSE): copyright notice; the code is published for
  assessment, all rights reserved.
- [`CONVENTIONS.md`](CONVENTIONS.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md):
  the rules the code follows and how to propose a change.
- [`docs/RELEASE-NOTES-v1.0.0.md`](docs/RELEASE-NOTES-v1.0.0.md) and
  [`docs/ROADMAP.md`](docs/ROADMAP.md): what is in the submitted build, and
  the backlog after it.
- `docs/figures/`: the screenshot panels above and the architecture diagram.
- `app/schemas/`: the exported Room schema for each of the 8 database
  versions, which the migration test walks.
- `tools/`: the scripts that generate the exercise seed and the movement
  patterns, fetch the exercise demo media and make the launch sound.
