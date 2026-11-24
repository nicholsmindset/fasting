# Fasting Shift for Android

The honest fasting tracker: a timer that always works, free widgets, real safety guidance for
long fasts, and data that stays on your phone. See [docs/PRD.md](docs/PRD.md).

## Status

All MVP features (F1–F10) are built, audited twice and fixed. The whole project builds (debug,
staging, release AAB) with 0 lint errors and 325 tests passing.

Moats (see `docs/moats.md`): Muslim fasting hub (Hijri date, Sunnah calendar, qada ledger, start a
fast until maghrib), doctor report PDF, trust center with delete-all, Ramadan safety card,
serverless challenges with invite links, share cards, glucose/ketone logging and Body signals
(sleep and resting heart rate from Health Connect). Needs device checks: PDF output, delete-all
restart, Health Connect reads/writes, invite links opening the app. A review pass fixed: winter Sunnah
fasts counting, notification-route allow-list, Ramadan safety card for type 1 diabetes, per-install
Health Connect record ids, wipe ordering, hidden-weight BMI in the report, unique report files,
hidden characters in challenge names, glucose retries.

Earlier test breakdown:

- `:core:domain`: 80 JVM tests (timer, safety gates and edit rules, streaks, reminders, insights,
  backup format and hardening, history stats)
- `:core:data`: 15 Robolectric tests (single-active-fast index, restore safety, settings in
  backups, export → wipe → restore round trip, undo through Room)
- `:app`: 36 tests incl. end-to-end UI flows (onboarding and gates, tappable timer modes,
  education-only), view-model tests (undo, refeeding), widget/billing decision logic

Highlights beyond the PRD: tappable timer (fasted / time left / break-fast time), eating-window
countdown, quick-add on the Timer, undo after ending, add a past fast, "started earlier",
refeeding screen, History hours ring + fast bars, database-key recovery screen, a guided first
week (first-fast card, coaching tips, end-of-fast summary with mood), weekly recap, hydration
goal, Ramadan mode (offline suhoor/iftar times for MUIS, JAKIM, KEMENAG, MWL) and an
iOS-inspired design.

Languages: English, Bahasa Melayu, Bahasa Indonesia, Simplified Chinese (per-app language
picker on Android 13+). The three translations are drafts and need a native-speaker review,
especially the safety and refeeding copy.

Not yet done: testing on real devices (alarm timing, widgets, OEM battery settings) — the cloud
build environment has no emulator.

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

Step-by-step browser prompts for Vercel, Play Console, RevenueCat, GitHub and Google Ads:
`docs/launch/codex-browser-setup.md`.

- [ ] Clinician review of all safety copy (`strings*.xml`) and thresholds (`domain/safety`)
- [ ] Legal review of claims, disclaimers and `docs/privacy-policy.md`
- [ ] Publish the privacy policy at https://fastingshift.com/privacy
- [ ] Play Console: Health apps declaration, Data safety (`docs/data-safety.md`), Health Connect
      declarations (`docs/health-connect-justifications.md`), listing (`docs/store-listing.md`)
- [ ] Rewrite body-timeline and program lesson copy in Robert's voice; map book chapters
- [ ] Verify every helpline and emergency number
- [ ] Device tests on Samsung, Xiaomi and Pixel (alarm timing, widgets, OEM battery settings)
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
