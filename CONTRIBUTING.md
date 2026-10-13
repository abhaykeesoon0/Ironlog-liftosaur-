# Contributing

Liftosaur is university coursework. The bar for a change is that it keeps the
app checkable: every security claim in the report points at one file in the
source, and a contribution should not blur that. This page is the short
version; [CONVENTIONS.md](CONVENTIONS.md) is the full one.

## Building

You need JDK 17 and Android SDK 35; minSdk is 26. Open the project folder in
Android Studio with File > Open (not New Project) and let Gradle sync. Run on
a physical phone where you can: the emulator has no step counter, a stubbed
accelerometer and no Bluetooth, so the rep counter, daily steps and the
heart-rate strap do not work there.

```bash
./gradlew assembleDebug               # build; the debug app installs as com.ironlog.app.debug
./gradlew installDebug                # install on a connected device or emulator
./gradlew lint                        # static analysis; the project is at 0 errors
./gradlew testDebugUnitTest           # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests; needs a device or emulator
adb logcat -s IronLog:V               # app logs; the tag kept the old name
```

A clone builds without any secret. `local.properties` may carry
`firebase.webApiKey=...` to turn email verification on; `keystore.properties`
and the keystore it names sign a release. All of them are git-ignored and
must stay that way. The release build (`./gradlew assembleRelease`) is
described in the README.

## Conventions in ten lines

1. MVVM, strictly: Screen (composable) → ViewModel → Repository → DAO → Room.
   State lives in Room or in a ViewModel, never in a global mutable singleton.
2. Every repository method that can fail returns `Result<T>` or a sealed
   result; ViewModels turn failures into user-facing messages. Never a bare
   `try { } catch { }` that logs and continues.
3. All weights are stored in kilograms. Conversion to pounds happens at the
   presentation layer only, driven by `UnitSystem` on the user record.
4. Warm-up sets never count. Check `SetType.countsTowardVolume`; do not
   compare enum values inline.
5. Every runtime permission shows the app's rationale before the system
   dialog: `rememberPermissionState(AppPermission.X)` and
   `runWithPermission { }`. Never call `launcher.launch()` directly.
6. All user text goes through `Validators` before storage, and anything that
   can reach a CSV export through `Validators.sanitiseForCsv`.
7. Passwords and passphrases are `CharArray`, never `String`, and are wiped
   with `.wipe()` when done.
8. Numeric readouts use the `NumericType` styles, which set tabular figures.
9. Chalk is the only action colour; the plate colours (`Plate25`, `Plate20`,
   `Plate15`, `Plate10`) are reserved for data and are never decoration. The
   app is dark-only.
10. No Retrofit or other general network dependency, no new host, no photos in
    shared media storage, and no Firebase beyond the verification REST call.

The reasons behind each line are in [CONVENTIONS.md](CONVENTIONS.md). The
Kotlin code style is the shared one under `.idea/codeStyles/`.

## Database changes

Room exports its schema to `app/schemas/`, which is tracked on purpose. A
schema change bumps the version in `AppDatabase`, adds a migration in
`di/AppModule.kt`, commits the new `N.json`, and extends `MigrationTest`,
which walks a version-1 database through every migration to the current one.

## Branches and pull requests

`main` is the default branch and is what the report and the submission
describe. Do not push to it directly.

1. Branch from `main` with a short descriptive name.
2. Keep one change per commit. The commit title says what the user gains or
   what stopped going wrong, in plain words, in the style of the existing log.
3. Open a pull request against `main` and fill in the template: what changed,
   how it was tested on a device, screenshots for any UI change, and the
   checklist.
4. The Android CI workflow (`.github/workflows/android.yml`) runs on every
   pull request: the JVM unit tests, Android lint and a debug build. It must
   pass before the merge.
5. A security problem is not an issue or a pull request. Follow
   [SECURITY.md](SECURITY.md) instead.
