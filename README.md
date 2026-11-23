# Fasting Shift for Android

The honest fasting tracker: a timer that always works, free widgets, real safety guidance for
long fasts, and data that stays on your phone. See [docs/PRD.md](docs/PRD.md).

## Status

Fasting Shift is an everyday fasting tracker. Start or edit a fast, review your week, and share a challenge with a friend. Optional features include health logs, doctor reports, Health Connect insights, and a Ramadan and Sunnah hub. Glucose writes use the same persistent retry queue as weight and water. Religious fast eligibility and Maghrib use the selected city's date.

Android CI runs the domain, data and app unit suites. Pull requests also run lint, build the staging APK and release AAB, and capture History and the moats screens. Each pull request has a downloadable staging APK and screenshots. The release bundle built in CI is unsigned unless a release upload key is configured.

Highlights beyond the PRD: tappable timer (fasted / time left / break-fast time), eating-window
countdown, quick-add on the Timer, undo after ending, add a past fast, "started earlier",
refeeding screen, History hours ring + fast bars, database-key recovery screen, a guided first
week (first-fast card, coaching tips, end-of-fast summary with mood), weekly recap, hydration
goal, Ramadan mode (offline suhoor/iftar times for MUIS, JAKIM, KEMENAG, MWL) and an
iOS-inspired design.

Languages: English, Bahasa Melayu, Bahasa Indonesia, Simplified Chinese (per-app language
picker on Android 13+). The three translations are drafts and need a native-speaker review,
especially the safety and refeeding copy.

### Physical device checks

- Open a doctor report in a PDF viewer and check that hidden weight stays hidden when sharing.
- Delete all data, restart, and verify that records, reminders and cached reports are gone.
- Grant and revoke Health Connect access, retry a failed glucose write, and check sleep and resting heart rate reads.
- Open an invite from a messaging app, complete onboarding if needed, and join the intended challenge.
- Check reminder delivery with the phone locked, home-screen widgets and battery restrictions on Pixel, Samsung and Xiaomi phones.

| Feature | Where |
| --- | --- |
| F1 Onboarding + safety screening | `app/.../ui/onboarding`, `domain/safety/Screening.kt` |
| F2 Timer, protocols, edits, schedules | `ui/timer`, `ui/fast`, `ui/settings/SchedulesScreen.kt`, `domain/timer`, `domain/schedule` |
| F3 Reminders, ongoing notification, reliability check | `alarm/`, `notifications/`, `ui/reliability` |
| F4 Widgets | `widget/` (Glance) |
| F5 History, calendar, streaks | `ui/history`, `domain/history`, `domain/streak` |
| F6 Logging + Health Connect | `ui/log`, `health/` |
| F7 Extended-fast safety layer | `ui/safety`, timer checklist and refeeding dialogs, `domain/safety` |
| F8 Export, backup, restore, plan settings | `export/`, `ui/settings`, `domain/backup`, `data/repo/BackupRepository.kt` |
| F9 Evidence-graded body timeline | `domain/timeline/BodyTimeline.kt` (draft content) |
| F10 Shift+ (RevenueCat), program, insights | `billing/`, `ui/plus`, `domain/program`, `domain/insights`, `domain/billing` |

### Configuration

- **RevenueCat:** set `revenuecat.apiKey` (public Android SDK key) in `~/.gradle/gradle.properties`
  or pass `-Prevenuecat.apiKey=...`. Without it, billing is off and the app runs as the free tier.
  The entitlement ID must be `shift_plus`; offering packages ANNUAL, MONTHLY and LIFETIME.
- **Book codes:** create a Play Console promo/offer code giving 3 months free; the app's
  "Redeem" button opens Play's redeem page.

### Uploading to Play internal testing

1. Create an upload key once (keep it and its passwords safe, outside the repo):
   `keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. Put the details in `~/.gradle/gradle.properties` (or `FS_UPLOAD_*` env vars on CI):
   ```
   fastingshift.upload.storeFile=/path/to/upload.jks
   fastingshift.upload.storePassword=...
   fastingshift.upload.keyAlias=upload
   fastingshift.upload.keyPassword=...
   revenuecat.apiKey=goog_...
   ```
3. Build: `./gradlew :app:bundleRelease -Pfastingshift.versionCode=2` (bump the code for every upload).
4. In Play Console: create the app, enrol in Play App Signing, upload
   `app/build/outputs/bundle/release/app-release.aab` to **Internal testing**, add testers, and
   share the opt-in link.

For a quick sideload without Play, `./gradlew :app:assembleStaging` builds an installable APK
(`com.fastingshift.app.staging`, debug-signed, never upload it).

### Repeat tester updates

Use the **Internal beta APK** workflow on the default branch to produce `com.fastingshift.app.beta`. It installs alongside the staging preview. For a first transfer, keep the old app installed, create a backup there, restore into the beta, and verify the history and timer. Later beta builds use the same certificate and an increasing version code.

In the GitHub environment `internal-beta`, configure a dedicated test keystore as the secret `BETA_KEYSTORE_BASE64` with `BETA_KEY_ALIAS`, `BETA_STORE_PASSWORD` and `BETA_KEY_PASSWORD`. Set the environment variable `BETA_CERT_SHA256` to the expected public certificate fingerprint. Keep a recoverable copy of this key in your signing-key vault. The workflow checks the application ID and fingerprint before saving the installable artifact. It runs on manual dispatch. For a device upgrade test, install one beta, seed a past fast and start a timer, install the next beta over it, then verify both records and the original start time.

### Before launch (not code)

- [ ] Clinician review of all safety copy (`strings*.xml`) and thresholds (`domain/safety`)
- [ ] Legal review of claims, disclaimers and `docs/privacy-policy.md`
- [ ] Publish the privacy policy at https://fastingshift.com/privacy
- [ ] Play Console: Health apps declaration, Data safety (`docs/data-safety.md`), Health Connect
      declarations (`docs/health-connect-justifications.md`), listing (`docs/store-listing.md`)
- [ ] Rewrite body-timeline and program lesson copy in Robert's voice; map book chapters
- [ ] Verify every helpline and emergency number
- [ ] Complete the physical device checks above
- [ ] Replace the placeholder launcher icon
- [ ] Native-speaker review of `values-ms`, `values-in`, `values-zh-rCN` (safety copy first)

## Modules

```
core/domain   Pure Kotlin. All business rules. No Android dependencies (KMP-ready).
core/data     Room + SQLCipher database, repositories, DataStore settings.
app           Compose UI, Hilt wiring, AlarmManager scheduling, receivers, notifications.
```

Key design rule: a fast is stored as data (UTC start and target end). Every surface computes
elapsed time from the clock, so nothing has to keep running and a fast survives force-stop,
reboot and time-zone changes.

## Build

Requires JDK 17+ and the Android SDK (set `ANDROID_HOME` or `sdk.dir` in `local.properties`).

```bash
./gradlew :core:domain:test
```

```bash
./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Without an Android SDK, Gradle builds only `:core:domain`, so the business rules can still be
tested anywhere.

After the first build with the SDK, Room writes the schema to `core/data/schemas/` (CI also
uploads it as an artifact). Commit it; once it is tracked, CI fails if the schema changes
without being committed.

## Safety and compliance notes

- `SCHEDULE_EXACT_ALARM` only; `USE_EXACT_ALARM` must never be declared.
- Auto Backup is off and backup/device-transfer rules exclude all data (SR-9). Users back up
  through export (F8).
- All user-facing safety copy in `app/src/main/res/values/strings.xml` and the thresholds in
  `core/domain/.../safety/` are pending clinician review (see docs/PRD.md, Safety section).
