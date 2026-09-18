# IronLog — project instructions for Claude Code

Android strength-training tracker. University coursework for a Mobile Application
Development module. Marked on UI/UX (30%), Functionality (30%), Device
Integration (20%), Security Implementation (20%).

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM: Screen (composable) → ViewModel → Repository → DAO → Room
- Hilt for DI, KSP for annotation processing
- Room for persistence, DataStore for preferences
- Navigation Compose, single activity
- No remote backend. Auth tokens are minted and verified on-device.

## Build and run

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # install on connected device/emulator
./gradlew lint                   # static analysis
adb logcat -s IronLog:V          # app logs
```

Requires JDK 17 and Android SDK 35. minSdk is 26.

## Directory layout

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

## Conventions that matter

**Money-equivalent rule: never silently swallow an error.** Every repository
method returns `Result<T>` or a sealed result type. ViewModels convert failures
into user-facing messages. Do not use bare `try { } catch { }` that logs and
continues.

**All weights are stored in kilograms.** Conversion to pounds happens at the
presentation layer only, driven by `UnitSystem` on the user record. Never store
a converted value.

**Warm-up sets do not count.** `SetType.WARMUP` is excluded from volume totals
and from PR detection. Check `SetType.countsTowardVolume` rather than comparing
enum values inline.

**Every runtime permission shows our rationale before the system dialog.** Use
`rememberPermissionState(AppPermission.X)` and `runWithPermission { }`. Never call
`launcher.launch()` directly — a cold system dialog is the main cause of
permanent denials, and a permanent denial cannot be undone from inside the app.

**All user text is sanitised before storage.** Route it through `Validators`.
Free text that can reach a CSV export must use `Validators.sanitiseForCsv`.

**Passwords are `CharArray`, never `String`.** Call `.wipe()` when done. A String
cannot be cleared from the heap.

**Numeric readouts use `NumericType` styles**, which set tabular figures so
columns do not jitter as values change mid-workout.

**Colour discipline.** Chalk (near-white) is the only action colour. The bumper
plate colours (`Plate25`, `Plate20`, `Plate15`, `Plate10`) are reserved for data:
charts, the plate calculator, PR badges. They are never decoration.

## When fixing build errors

Prefer fixing the cause over suppressing the warning. If a Compose recomposition
or Hilt injection error appears, read the full stack trace before changing
signatures — Hilt errors are usually a missing `@Inject` constructor or a module
binding, not a problem with the call site.

## Things not to do

- Do not add Firebase, Retrofit or any network dependency. The app is offline by
  design and the report documents that decision.
- Do not write progress photos to shared media storage. They stay in internal
  app storage; this is a stated privacy decision.
- Do not add a light theme. The app is dark-only by design.
- Do not use `localStorage`-style global mutable singletons for app state. State
  lives in Room or in a ViewModel.
