# Liftosaur v1.0.0 — coursework submission

Liftosaur is an offline-first strength-training tracker for Android 8.0 and above, built in Kotlin and Jetpack Compose as a single-activity app with Room, SQLCipher and Hilt. A lifter plans routines, logs every set with weight, reps and rest, and sees records, volume, recovery and a body map of what they trained; around that core sit outdoor cardio with GPS and a live map, a food log with a barcode scanner, strength goals, a rewards system and a coach that answers from the user's own figures. There is no backend: accounts, tokens and the whole log live on the phone in an encrypted database, and a backup that leaves it can be sealed under a passphrase. This is the build submitted for the Mobile Application Development module, tagged `v1.0.0`, from commit `3b24a4a` on branch `kotlin-upgrade` (also pushed as `main`).

## What is in this release

### UI and UX
- One dark theme on Material 3 with its own colour, type and spacing tokens. Chalk (near-white) is the only action colour; gold marks records, the streak and rewards; the bumper-plate colours (red 25, blue 20, yellow 15, green 10) are reserved for data in charts, the plate calculator and PR badges.
- Every numeric readout uses tabular figures, so a timer or a volume total does not jitter as its digits change mid-workout.
- A bottom bar with five tabs (Home, Routines, History, Progress, Profile); on tablets and phones in landscape it becomes a navigation rail with the content held to a readable column. Full-screen flows such as an active workout hide the bar and provide their own exit.
- The logger: one row per set with last session's figures greyed out beside the inputs; ticking a set starts the rest timer and shows a PR badge in the row within the same second; the rep counter and plate calculator open as bottom sheets so the workout stays in view.
- Home is a stack of sections (this week, muscle map, recovery, goals, quests, coach) that the user reorders or hides from Customise Home.
- A three-page introduction before the first sign-in, replayable from Settings, and an empty state on every list that says what to do first.
- Accessibility: text that follows the system font scale, content descriptions on icons that act, a text summary on every chart, 48 dp touch targets, and a muscle map that reads its numbers to TalkBack.

### Functionality
- Accounts: register, sign in, change password, reset with the device lock, delete with the password. The email address is verified at sign-up.
- Routines built from a 225-movement library plus custom exercises, with sets, rep ranges, rest and a target weight per exercise; double progression moves targets up or back after each session. A rule-based generator builds a plan from training goal, days per week and session length, offline.
- Workout logger with warm-up, drop and failure set types, live PR badges, a plate calculator, an accelerometer rep counter, spoken set entry ("sixty kilos for eight"), and a rest timer with notification actions and a Quick Settings tile. Any past workout can be repeated as a new one.
- Records per exercise (heaviest weight, best estimated 1RM, best set volume, most reps) and a session-volume record. Warm-ups never count, and deleting a session rebuilds the records it affected.
- History with sets, volume, duration and records for every session, shareable as text or as a rendered image card.
- Progress: weekly volume, muscle-group split, daily steps, records, per-exercise charts of top weight and estimated 1RM, and a body map for this week or any week before it.
- Strength goals (a target weight or estimated 1RM per exercise, progress from logged sets) and a per-muscle recovery model, both shown on Home.
- Coach: rule-based answers from the log, with a keyless lookup of facts on DuckDuckGo and Wikipedia that sends only the topic and can be switched off; optional Gemini Nano on phones that have it; optional Qwen 2.5 model (550 MB or 1.6 GB) downloaded once and run on the phone. Both model tiers are off until enabled.
- Nutrition: a food log with a barcode scanner (Open Food Facts lookup, opt-in), daily targets and a meal-plan generator.
- Outdoor cardio: GPS route in a foreground service with distance, pace, elevation, steps and estimated calories, drawn live on OpenStreetMap tiles, with a Bluetooth LE heart-rate strap. Every saved session shows its route on the map.
- Body measurements with trend charts, and progress photos kept in private storage with a side-by-side compare. Photos never reach the gallery.
- Rewards: quests earn coins, and a store spends them on cosmetics.
- Import of Strong and Hevy CSV exports, matched by exercise name; a backup or CSV shared into the app opens the right import dialog, which adds and never replaces.
- Export and backup: CSV exports, a JSON backup, a sealed encrypted backup, and a weekly automatic sealed backup kept locally and optionally in a cloud folder the user picks. The backup carries the account, so on a new phone the sign-in screen restores it and the user signs in with the same password.

### Device integration
- Twelve hardware and platform features: camera (progress photos, avatar, barcode scanning with on-device ML Kit), GPS, accelerometer (rep counting), step counter, microphone (spoken set entry), Bluetooth LE (heart-rate strap), notifications on four channels, a Quick Settings tile, a Glance home-screen widget, launcher shortcuts, Health Connect (steps in; exercise, distance and nutrition out) and WorkManager (daily reminder, weekly backup, model download).
- Every runtime permission goes through one rationale-first flow: the app's own explanation sheet, then the system dialog. Settings lists every permission with its rationale and current state.
- Each feature holds its hardware only while it runs. No background location and no storage permission are declared; photos and exports leave only through a FileProvider grant, and a gallery image is chosen with the system photo picker.
- Foreground services with declared types: cardio tracking (location), the rest timer (special use) and the model download (data sync through WorkManager, resumable, refusing mobile data unless asked).

### Security
- Passwords hashed with PBKDF2-HMAC-SHA256 at 120,000 iterations with a random 16-byte salt per account, compared in constant time, carried through the app as `CharArray` and wiped after use. A password change issues a new salt and invalidates every session.
- Sessions are HMAC-SHA256-signed tokens minted and verified on the device: a 30-minute access token and a 30-day refresh token, stored in EncryptedSharedPreferences under an Android Keystore master key (AES-256-SIV for keys, AES-256-GCM for values). A tampered token destroys the session.
- Sign-in throttling: five free attempts, then a wait that starts at 30 s and caps at 15 minutes, keyed by a hash of the email; one shared message for a wrong password and a missing account.
- Email verification through Firebase Authentication's REST API, called with the app's own HTTP code and no SDK; Firebase holds the email, a credential and the verified flag, nothing about training.
- The Room database is encrypted at rest with SQLCipher; a plaintext database from an older install is migrated into the encrypted one on first launch.
- Backups are sealed with AES-256-GCM under a passphrase stretched with PBKDF2 at 120,000 iterations, with a fresh salt and nonce per file and an authenticated header, so a wrong passphrase or a changed byte fails cleanly.
- Optional app lock with fingerprint, face or the device credential, re-locking after a grace period (15 s by default). The progress-photo screen always blocks screenshots and recents thumbnails; a setting extends that to every screen.
- Every free-text field is sanitised before storage, and anything that can reach a CSV export is guarded against formula injection.
- No backend and no cleartext traffic. The five hosts the app can reach are pinned to their certificate roots. Cloud backup and device-to-device transfer are disabled in the manifest, and account deletion requires the password and removes every row, photo, export and backup the account owns.

## Build and tests
- Signed with a 4096-bit RSA key kept outside the repository, minified and shrunk with R8, split per CPU architecture with native libraries stored compressed. The APK reports `versionName` 1.0 (`versionCode` 1). A clone without the keystore and the Firebase key still builds, unsigned and with verification off.
- 170 JVM unit tests and 9 instrumented tests (every database migration from version 1 to 8 against the exported schemas, DAO aggregate queries, the workout repository with real record detection, and the app as a new user meets it). Android lint reports 0 errors.

## Assets
- `Liftosaur-release-arm64-v8a.apk`: the signed release build for 64-bit ARM phones, which is any modern phone (26.5 MB).
- `Liftosaur-release-universal.apk`: the same build for any ABI, including Intel emulators (70 MB).
- `Liftosaur-demo.mp4`: an 8 min 52 s screen recording (720 x 1560, no sound) on a Galaxy S25 Ultra, on a demo account seeded with eight weeks of training, in five parts: 0:00 Home and routines; 1:22 a workout from start to finish; 3:22 History, Progress, the coach and quests; 5:23 Nutrition, body, cardio, Settings and the privacy page; 7:58 the three-page introduction.
- `Liftosaur-report.pdf`: the coursework report, covering the four assessed areas, testing, challenges and limitations.
- `Liftosaur-source-code.md`: every text file tracked in the repository at commit `3b24a4a`, in one Markdown document. Binary files (icons, drawables, the exercise demo animations) are not in it.

## Install
1. On the phone, allow installing from unknown sources for the browser or file manager you will open the APK with.
2. Install `Liftosaur-release-arm64-v8a.apk`. It fits any modern phone; use the universal APK only on an emulator or a device whose architecture you do not know.
3. Minimum Android 8.0 (API 26). The app works with the radio off; the only network use is the verification email at sign-up, the coach's keyless lookup (switched off with one chip on the Coach screen) and the features you opt in to.
4. A fresh install has no account, because accounts live only on the phone: register one. A new account must open the link in its verification email before it can be used; a build made without the Firebase key runs with verification off.

## Known limitations
- English only: every user-facing string is written in Kotlin rather than in string resources, so the app cannot be translated without a mechanical refactor across the UI.
- No Wear OS companion. The Quick Settings tile and the widget cover the phone-side conveniences instead.
- The downloaded coach model is slow on low-end phones: tens of seconds on a mid-range device, minutes on the emulator. Gemini Nano is faster where a phone has it, and the rule-based coach is instant.
- Email verification is the only feature that needs a third party and a connection. A build without the Firebase project key runs with verification off.
- Progress photos are not part of any backup. A photo-only sealed export would close the gap.
- The certificate pins on the named hosts expire in September 2027, after which the platform falls back to ordinary validation. A release before then should refresh them.
