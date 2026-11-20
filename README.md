# Fasting Shift for Android

The honest fasting tracker: a timer that always works, free widgets, real safety guidance for
long fasts, and data that stays on your phone. See [docs/PRD.md](docs/PRD.md).

## Status

All MVP features (F1–F10) are written. The business rules in `:core:domain` are unit-tested
(58 tests). **The Android modules (`:core:data`, `:app`) have not been compiled yet**: the build
environment could not reach Google's Maven repository, and GitHub Actions did not start a job.
Expect a round of compile fixes on the first build with the Android SDK.

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

### Before launch (not code)

- [ ] Clinician review of all safety copy (`strings*.xml`) and thresholds (`domain/safety`)
- [ ] Legal review of claims, disclaimers and `docs/privacy-policy.md`
- [ ] Publish the privacy policy at https://fastingshift.com/privacy
- [ ] Play Console: Health apps declaration, Data safety (`docs/data-safety.md`), Health Connect
      declarations (`docs/health-connect-justifications.md`), listing (`docs/store-listing.md`)
- [ ] Rewrite body-timeline and program lesson copy in Robert's voice; map book chapters
- [ ] Verify every helpline and emergency number
- [ ] Device tests on Samsung, Xiaomi and Pixel (alarm timing, widgets, OEM battery settings)
- [ ] Replace the placeholder launcher icon

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
