# Liftosaur roadmap

This is the backlog after the v1.0.0 coursework submission, derived from the Limitations, Challenges and Conclusion sections of the report, plus one gap the Functionality section states (`Liftosaur-report.pdf` on the v1.0.0 release). Each item below is written as a GitHub issue: the heading is the issue title, and the label, milestone and body follow, so an item can be pasted into a new issue as it stands. Once created, the items are tracked as GitHub issues at https://github.com/abhaykeesoon0/Ironlog-liftosaur-/issues, and this file is only the starting list; an issue that is opened gets its number written next to its heading here.

Milestones: **v1.1** for small follow-ups that fit the current structure, **v2.0** for work that touches a large part of the app or adds a new target.

## Done in v1.0.0

Three defects found by testing and fixed before the release:

- The introduction's Skip button sat under the status bar on edge-to-edge devices, where the system takes the taps. The screen is now padded for the system bars, and the case is covered by the instrumented test that walks a new user from the introduction to Home.
- The map library (osmdroid, hosted through AndroidView) destroyed its overlays whenever Compose detached the view during layout, crashing on the next update. Destroy-on-detach is now off and the map is released when the composable leaves.
- Returning to a cardio session that was already recording hung the app: the live map was asked to frame the route while its view was still being attached, before it had a size, and osmdroid's framing loop never ends on a zero-sized view. The route is now framed after the first layout.

## Milestone v1.1

### Refresh the certificate pins before September 2027
Label: security
Milestone: v1.1

The pins on the five hosts the app can reach (Wikipedia, wger, Open Food Facts, DuckDuckGo and Firebase Authentication through googleapis.com) expire on 24 September 2027 in `app/src/main/res/xml/network_security_config.xml`. After that date the platform falls back to ordinary certificate validation, so nothing breaks, but the pinning the report describes is silently gone. Acceptance: a release made before that date carries pin sets taken from each host's current chain with a backup pin, a new expiration at least a year out, and a line in its release notes saying so.

### Sealed export and restore of progress photos
Label: enhancement
Milestone: v1.1

Progress photos stay in private app storage and are left out of every backup on purpose, because a backup goes through a share sheet or a cloud folder. That means they are lost with the phone, and the report names a photo-only sealed export as the way to close the gap. Acceptance: Settings offers a photo export sealed under a passphrase with the same AES-256-GCM format as the backup (`BackupCrypto`), the restore side adds the photos and their dates to the photo screen without duplicating ones already there, and nothing is ever written to shared media storage.

### Tell the user what the downloaded coach model will cost in time on their phone
Label: enhancement
Milestone: v1.1

The compact 550 MB model answers in tens of seconds on a mid-range phone and takes minutes on the emulator, so on a low-end phone a 550 MB or 1.6 GB download can end in a disappointing wait. The rule-based coach is instant and Gemini Nano is faster where a phone has it, and the user should see that before committing to a download. Acceptance: the download screen states the expected answer time of each model on this phone, or that it is not known, and offers Gemini Nano first where the phone has it.

### Define what sign-up does when the verification service cannot be reached
Label: security
Milestone: v1.1

Email verification is the only feature that cannot work offline or without a third party: a new account must open the link in its verification email before the app can be used, and the app only degrades to no verification when the Firebase project key is absent at build time. A phone with no connection at sign-up, or an outage at the service, needs a stated outcome, which the report does not give. Acceptance: a sign-up with the radio off either completes and shows the verify-email gate the next time a connection is available, or fails with a message that names the cause; an instrumented test covers the chosen behaviour, and the privacy page describes it.

### Rebuild personal records after a backup restore or a CSV import
Label: enhancement
Milestone: v1.1

Record detection runs only when a workout is finished in the app, so sessions brought in through a backup restore carry no record badges and the records table starts from the first session logged live (the report notes this under the Functionality figures); by the same rule a Strong or Hevy CSV import brings none either. Deleting a session already rebuilds the records it affected, so the same pass can run once after an import. Acceptance: after restoring a backup onto a wiped install, the records table and the badges in History match what the app would have produced had the sessions been logged live, with warm-up sets still excluded.

### Instrumented test for the map's lifecycle inside Compose
Label: testing
Milestone: v1.1

Two of the three defects fixed in v1.0.0 were in the osmdroid map hosted through AndroidView: overlays destroyed on detach, and a framing loop on a zero-sized view when returning to a recording session. Both were found by testing and fixed by taking the lifecycle in hand, and none of the nine instrumented tests covers the map, so a later change to `RouteMap.kt` could bring either back unnoticed. Acceptance: an instrumented test that starts a cardio session, navigates away and back while it is recording, and saves it, passing without a crash or a hang within a fixed timeout.

### Audit every screen for edge-to-edge system-bar insets
Label: ui
Milestone: v1.1

The introduction's Skip button was drawn under the status bar on edge-to-edge devices until the screen was padded for the system bars. That case is now covered by a test, but it was found on one screen, and the other screens with controls near the top or bottom edge have not been checked the same way. Acceptance: on the Android 15 emulator and the Galaxy S25 Ultra, every screen's topmost and bottommost interactive control sits inside the system-bar insets, and any screen that needed padding gets a step in the new-user instrumented test or a test of its own.

## Milestone v2.0

### Move every user-facing string into string resources
Label: enhancement
Milestone: v2.0

Every user-facing string is written in Kotlin rather than in `res/values/strings.xml`, so the app is English only and cannot be translated without a mechanical refactor across the UI; the report names this as the largest gap against a production app. The work is to move the strings screen by screen into resources, plurals included, and read them through `stringResource`, without changing any wording along the way. Acceptance: no user-facing literal remains in Kotlin under `ui/`, `core/permissions` and `core/notifications`, and a second locale folder with one translated string shows that string in the app when the device language changes.

### Wear OS companion for the rest timer and set logging
Label: device-integration
Milestone: v2.0

The rest timer and set logging would suit a watch: both are used between sets, when the phone is in a pocket or on the floor. v1.0.0 covers the phone side with the Quick Settings tile and the home-screen widget and has no watch app. Acceptance: a Wear OS module that shows the running rest countdown with Skip and +30 s and lets the wearer tick off the current set, driving the existing rest-timer service and workout repository on the phone rather than keeping a second copy of the log.
