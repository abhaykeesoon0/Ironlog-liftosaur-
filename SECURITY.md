# Security policy

Liftosaur is an offline-first Android strength-training tracker. It has no
backend: the account, the session tokens and the whole training log live on
the phone, and every control below exists to keep them there or to make any
copy that leaves the phone useless without its owner. This page says what the
app protects, who it protects against, how, and where the gaps are. Each
control names the file in the source where it can be checked; Kotlin paths
are relative to `app/src/main/java/com/ironlog/app/`.

## Supported versions

Only the current release is supported. A fix goes into the next build from
`main`; there are no maintenance branches for older builds.

| Version | Supported |
|---|---|
| 1.0, the current release, built from `main` | Yes |
| Anything older | No |

## Reporting a vulnerability

Email abhaykeesoon0@gmail.com with what you found, how to reproduce it, and
the build it was found in (the commit hash, or debug / release). Please do not
open a public GitHub issue for a security problem until it has been fixed.

I will reply within a week. There is no bug bounty: this is university
coursework. If you want it, you will be credited in the commit that fixes the
problem.

## Threat model in brief

**What the app protects**

- The training log: workouts, sets, records, routines, body measurements,
  cardio routes and the food log. This is health data.
- Credentials: the account email, the password hash and its salt, and the
  session tokens.
- Progress photos.

**Who it protects against**

- Someone holding the unlocked phone. With the app lock on they should not get
  into the app at all. Either way they should not be able to read the database
  or the token store from the file system, capture the photo screens, or guess
  a password by trying many on the sign-in screen.
- Someone holding a backup file, whether it was shared to the wrong place, left
  in a cloud folder, or copied off a stolen phone. A sealed backup should be
  useless without its passphrase, and a changed byte should fail cleanly rather
  than restore something else.
- A network observer. The app makes few network calls; each is HTTPS, the
  five named hosts with their root certificate pinned, and none carries the
  log. The only calls
  that carry a credential are sign-up and, until the address is verified,
  sign-in, when the email address and password go to Firebase Authentication
  and nowhere else.

Out of scope: a rooted phone, a compromised operating system, malware with
accessibility access, and a user who hands over their passphrase. The app
relies on the Android sandbox and the Android Keystore and does not try to
defend against an attacker who already has both.

## Controls

- **Password hashing.** PBKDF2-HMAC-SHA256 at 120,000 iterations with a random
  16-byte salt per account, compared in constant time with
  `MessageDigest.isEqual`. A password change issues a new salt as well as a new
  hash and invalidates every existing session.
  (`core/security/PasswordHasher.kt`)
- **Passwords in memory.** A password travels through the app as a
  `CharArray`, never a `String`, and is wiped with `.wipe()` after use, because
  a `String` cannot be cleared from the heap. The sign-up request body is
  zeroed after it is sent. (`core/security/PasswordHasher.kt`,
  `core/auth/EmailVerifier.kt`)
- **Sessions.** Sign-in issues an HMAC-SHA256-signed access token that lasts
  30 minutes and a refresh token that lasts 30 days, both minted and verified
  on the device. A malformed or tampered token destroys the session rather than
  repairing it. (`core/security/TokenService.kt`)
- **Token storage.** The tokens sit in `EncryptedSharedPreferences` under an
  Android Keystore master key, AES-256-SIV for keys and AES-256-GCM for values.
  The database key lives in the same store.
  (`core/security/SecureTokenStore.kt`)
- **Database at rest.** The Room database is encrypted with SQLCipher. Its key
  is generated once per device and held in the Keystore-backed store. A
  plaintext database from an older install is migrated into the encrypted one
  on first launch. (`core/security/DatabaseEncryption.kt`,
  `core/database/AppDatabase.kt`)
- **Backups.** A backup file is sealed with AES-256-GCM under a key stretched
  from the user's passphrase with PBKDF2 at 120,000 iterations, with a fresh
  salt and nonce per file and the header authenticated so a file cannot be
  relabelled. A wrong passphrase or a changed byte fails cleanly. The weekly
  automatic backup, and its optional copy to a cloud folder, are the same
  sealed file; the passphrase stays in the Keystore-encrypted store.
  (`core/security/BackupCrypto.kt`, `core/backup/AutoBackup.kt`)
- **Sign-in throttling.** Five free attempts, then a growing wait that starts
  at 30 s and caps at 15 minutes, keyed by a hash of the email so the throttle
  store is not a list of accounts. A fixed delay and one shared message for a
  wrong password and a missing account stop the screen from confirming which
  addresses exist. (`core/security/LoginThrottle.kt`)
- **App lock.** An optional fingerprint, face or device-credential lock drawn
  over the app, which re-locks after a grace period the user picks, 15 s by
  default. (`core/security/BiometricGate.kt`)
- **Screenshots.** `FLAG_SECURE` is always set on the progress-photo screen,
  so a capture or a recents thumbnail comes back black. A setting extends it
  to every screen. (`ui/screens/body/PhotosScreen.kt`, `MainActivity.kt`)
- **Input.** Every free-text field is sanitised before storage through one
  `Validators` object. Anything that can reach a CSV export goes through
  `sanitiseForCsv`, which quotes the value and neutralises a leading `=`, `+`,
  `-` or `@`, so a note typed as a spreadsheet formula cannot run when the
  export is opened in Excel. (`core/security/Validators.kt`)
- **Network.** Cleartext traffic is blocked everywhere. Each of the five hosts
  the app can reach (Firebase Authentication, Open Food Facts, wger,
  DuckDuckGo, Wikipedia) is pinned to its certificate authority's root with a
  backup pin; OpenStreetMap tiles and the coach-model download use the system
  trust store over HTTPS. There is no general network library in the app.
  (`app/src/main/res/xml/network_security_config.xml`)
- **Platform backup off.** `android:allowBackup="false"` and
  `android:fullBackupContent="false"`, and the data-extraction rules exclude
  the database, shared preferences and files from both cloud backup and
  device-to-device transfer. (`AndroidManifest.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`)
- **Permissions and storage.** No background location and no storage
  permission; no permission is held for a feature not in use. Photos and
  exports live in private app storage and leave only through a `FileProvider`
  grant. (`core/permissions/`, `core/media/PhotoStore.kt`)
- **Account deletion.** Requires the password and removes every row, photo,
  export and backup the account owns. (`data/repository/ProfileRepository.kt`)
- **No secrets in the repository.** The signing keystore, its passwords and
  the Firebase web API key live in git-ignored files. A clone without them
  still builds, unsigned and with verification off. (`.gitignore`,
  `app/build.gradle.kts`)

## What leaves the phone

By default, only the topic of a coach question, sent to DuckDuckGo or
Wikipedia without any account or key; that can be switched off with the
"Looking things up online" chip on the Coach screen. Everything else leaves
only when the user turns a feature on:

- **Barcode lookup:** the barcode digits go to Open Food Facts. No account,
  no key.
- **Exercise browser:** search terms go to wger. No account, no key.
- **Coach model on the phone:** if the user downloads one, the file comes from
  huggingface.co once and is checked against its published checksum. After
  that the model answers on the phone and sends nothing.
- **Email verification:** at sign-up, the email address and password go to
  Firebase Authentication, which sends the verification link and records that
  it was opened. Nothing about training goes there.
- **Health Connect:** sessions and meals are written to it only after the user
  connects it, and that can be revoked.
- **Cardio map:** while a route is on screen, the map tiles for that area come
  from OpenStreetMap. The tile server sees which area was viewed, never the
  route, which is drawn on the phone and stored only there.
- **Cloud copy of backups:** if the user picks a folder in a cloud storage
  app, each weekly backup is written there already sealed. The provider only
  ever holds the encrypted file; the passphrase stays on the phone.

Every connection is HTTPS. The five hosts named under Controls are pinned to
the service's root certificate; the OpenStreetMap tiles and the model download
use the system trust store. The full training log never leaves the device in
the clear. There is no
analytics and no account server. The privacy page inside the app (Settings >
Privacy & your data) lists the same things, so the person whose data it is can
read them without this file.

## Known limitations

- **Verification depends on one external service.** Email verification is the
  only feature that cannot work offline or without a third party. When no
  Firebase project key is present the app runs with verification off; this is
  deliberate and documented rather than hidden. Accounts created before the
  check existed are treated as verified.
- **Progress photos are not backed up.** A backup goes through a share sheet
  or a cloud folder, and body photos are excluded on purpose. A photo-only
  sealed export would close the gap.
- **Certificate pins expire.** The pins in `network_security_config.xml`
  expire on 24 September 2027, after which the platform falls back to ordinary
  certificate validation. A release before then should refresh them.
