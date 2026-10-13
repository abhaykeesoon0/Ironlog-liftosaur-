## What changed

<!-- What the change does and why. Link the issue if there is one. -->

## How it was tested on device

<!-- Which phone or emulator, which Android version, and what you did by hand.
     Say if the change touches a sensor, a permission, a foreground service,
     a database migration or the backup format: none of those is covered by
     the JVM tests alone. -->

- Device or emulator, and Android version:
- `./gradlew testDebugUnitTest`: pass / fail
- `./gradlew lint`: 0 errors / not run
- `./gradlew connectedDebugAndroidTest` (needed if the change touches the
  database, a DAO, a repository or the sign-in flow): pass / fail / not run

## Screenshots

<!-- Before and after for any UI change. Not possible for the progress-photo
     screen, which blocks capture; describe it instead. -->

## Checklist

- [ ] No secrets committed: no `local.properties`, `keystore.properties`,
      keystore file or API key
- [ ] Result-returning repositories, no swallowed errors: every repository
      method that can fail returns `Result`, and there is no bare
      `try { } catch { }` that logs and continues
- [ ] Weights stored in kilograms; conversion only at the presentation layer
- [ ] Warm-up sets excluded through `SetType.countsTowardVolume`, not an
      inline comparison
- [ ] Every runtime permission goes through `rememberPermissionState` and
      `runWithPermission`; no direct `launcher.launch()`
- [ ] User text routed through `Validators`; anything that can reach a CSV
      export through `Validators.sanitiseForCsv`
- [ ] Passwords and passphrases as `CharArray`, wiped after use
- [ ] If the schema changed: version bumped in `AppDatabase`, migration added
      in `di/AppModule.kt`, the new `app/schemas/.../N.json` committed, and
      `MigrationTest` extended
- [ ] No new network dependency and no new host (see `CONVENTIONS.md`); if a
      host had to be added, it is pinned in `network_security_config.xml` and
      listed on the privacy page
- [ ] Photos stay in private app storage; nothing written to shared media
- [ ] No light theme, no global mutable singletons for app state
