# Fasting Shift for Android

The honest fasting tracker: a timer that always works, free widgets, real safety guidance for
long fasts, and data that stays on your phone. See [docs/PRD.md](docs/PRD.md).

## Status

Weeks 1–2 of the MVP build plan (foundations):

| Area | State |
| --- | --- |
| Timer engine, protocols, streaks, reminder planning | Done, unit-tested (`:core:domain`) |
| Safety screening gates, fast policy, red flags, refeeding tiers | Done, unit-tested (`:core:domain`) |
| Room schema + SQLCipher encryption, repositories, settings | Written (`:core:data`), Robolectric tests |
| Alarm scheduling with exact/windowed fallback, reschedule on boot/time/zone/permission | Written (`:app`) |
| Minimal timer screen with extended-fast checklist and refeeding guidance | Written (`:app`) |
| Onboarding, widgets, history UI, logging, Health Connect, export, Shift+ | Not started (weeks 3–10) |

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
