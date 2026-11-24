# Fasting Shift for Android — MVP PRD

Nov 20, 2026 · Robert Nichols

## Overview

The MVP ships a reliable, private and honest fasting tracker for Android in about 10 weeks. Its core is free, its safety layer is always free, and there are no subscription tricks. A small paid tier, Shift+, adds guided programs built on *The Fasting Shift*.

**Problem.** The leading apps (Zero, Simple, Fastic, BodyFast, DoFasting) share three weaknesses:

- **Paywalls and dark patterns.** Widgets and basic protocols sit behind paywalls, cancelling is hard, and renewals arrive as surprises. DoFasting holds a 2.7★ rating on Google Play.
- **Weak Android reliability.** Timers fail to start, data gets lost, and the apps are iOS-first.
- **Overclaiming.** "Autophagy zones" are sold as fact, and long fasts get no safety tooling.

The apps experienced fasters relied on (LIFE, Fastient, Vora) have left Google Play or been abandoned.

**Positioning.** The honest fasting app for Android: a timer that always works, free widgets, real safety guidance for long fasts, your data stays on your phone, built on *The Fasting Shift* and on what the evidence actually shows.

**Why us.** The book and FastingShift.com supply trust, content and an acquisition channel that no timer app can copy. Recent evidence also favours honesty. The 2026 Cochrane review found intermittent fasting gives roughly the same weight loss as standard dietary advice, so the app sells consistency, self-knowledge and safe practice rather than miracle results.

Working name: **Fasting Shift** (Play listing: *Fasting Shift: Fasting Tracker & Widget*).

## Goals and success metrics

The MVP has to prove two things: people keep fasting with it every day, and a trust-first freemium model can still convert.

1. Ship a timer that never loses a fast and never fails to notify on Android, including Samsung and Xiaomi devices.
2. Make the free tier generous enough to win the "no subscription trap" reviews.
3. Make safety a visible feature: screening, red-flag checks and refeeding guidance.
4. Validate Shift+ conversion through the book funnel and the onboarding trial.

The core Android experience covers the timer, personal schedule and editable fasting history. A friend can join through a private link. Optional modes live in Settings.

| Metric | Target | Why |
| --- | --- | --- |
| D1 / D7 / D30 retention | ≥ 40% / 25% / 15% | Fasting is a daily habit, so aim above category norms |
| Users who finish a first fast | ≥ 60% of installs | Activation |
| Download-to-trial | ≥ 7% | RevenueCat Health & Fitness median is 6.9% |
| Trial-to-paid | ≥ 38% | Category median is 37.7% |
| Play rating | ≥ 4.6★, no billing-themed 1★ reviews | Trust positioning |
| Crash-free sessions | ≥ 99.5% | Reliability promise |
| End-of-fast alerts within 1 minute (exact alarm granted) | ≥ 98% | Reliability promise |
| Fasts lost to bugs | 0 | The top complaint about competitors |

## Target users

| Persona | Who | What they need from the MVP | Phase |
| --- | --- | --- | --- |
| P1 Experienced faster (primary) | 30–55, does 16:8 daily plus a 36–72h fast now and then, Android user, tired of paywalls and hype | A timer that just works, free widgets, extended-fast support, export | MVP |
| P2 Fasting Shift reader | A motivated beginner who trusts Robert's voice | Simple onboarding, guided program, plain-English explanations | MVP |
| P3 Religious faster | Observes Ramadan or Lent, often in Southeast Asia | Prayer-time-aware schedules, no weight-loss framing | Phase 1 |
| P4 GLP-1 user | On a GLP-1 drug or coming off one | Protein and hydration focus, gentle windows | Phase 2 |

### Core jobs to be done

- "When I start a fast, I want one tap and then to forget about it until it's time to eat."
- "When I'm doing a long fast, I want to know I'm doing it safely and when to stop."
- "When I look back, I want to see that I'm being consistent, without being shamed for a broken fast."

## MVP feature scope

F1–F8 are P0 (launch blockers). F9 and F10 are P1 and can slip by up to two weeks without delaying launch. Everything except F10 is free.

| ID | Feature | Priority | Tier |
| --- | --- | --- | --- |
| F1 | Onboarding and safety screening | P0 | Free |
| F2 | Fasting timer and protocols | P0 | Free |
| F3 | Reminders and reliable notifications | P0 | Free |
| F4 | Home-screen widgets | P0 | Free |
| F5 | History, calendar and consistency streaks | P0 | Free |
| F6 | Weight, water and electrolyte logging + Health Connect | P0 | Free |
| F7 | Extended-fast safety layer (24h+) | P0 | Free, always |
| F8 | Export, backup and settings | P0 | Free |
| F9 | Evidence-graded body timeline | P1 | Free |
| F10 | Shift+ subscription: guided programs + insights | P1 | Paid |

### F1 Onboarding and safety screening

Onboarding takes under 90 seconds, asks no account questions, and ends with the user ready to start a fast.

- Steps: welcome, then goal (consistency / weight / metabolic health / long fasts), safety screen, protocol pick, optional notification permission, optional skippable Shift+ offer.
- The safety screen asks about age, pregnancy or breastfeeding, eating-disorder history, type 1 diabetes, diabetes medication (insulin, sulfonylureas, SGLT2 inhibitors), blood-pressure medication or diuretics, GLP-1 drugs, and height and weight (to compute BMI).
- Acceptance: a hard-block answer leads to education-only mode with no protocols offered. A soft-gate answer requires a clinician acknowledgement and locks fasts over 24h. Answers can be changed in settings and re-run the gates. Copy is reviewed for non-diagnostic, non-shaming tone.

### F2 Fasting timer and protocols

One tap starts a fast. The timer is stored data, not a running process, so it survives app kill, reboot and a time-zone change.

- Presets: 12:12, 14:10, 16:8, 18:6, 20:4, OMAD, 24h, 36h, 48h, 72h and custom (1–120h, gated by F1 and F7). 5:2 and ADF run as a weekly schedule.
- Users can edit the start and end time of active and past fasts, end a fast early with no shame copy, and set the eating window between fasts.
- Acceptance: elapsed time is correct after force-stop, reboot and a time-zone change in automated tests. Edits update history and streaks immediately. Ending a fast takes at most 2 taps.

### F3 Reminders and reliable notifications

Reminders cover fast start, fast end, user-chosen milestones and a "forgot to end your fast?" nudge (default: 4h past target).

- The app requests `SCHEDULE_EXACT_ALARM` in context and falls back to windowed alarms if it is denied. It must never declare `USE_EXACT_ALARM`.
- Alarms are rescheduled on boot, time change, time-zone change and permission change.
- An optional ongoing notification shows a live chronometer during a fast.
- A Reliability check screen detects battery optimisation and deep-links to OEM settings (Samsung, Xiaomi, Oppo, Vivo).
- Acceptance: at least 98% of end-of-fast alerts fire within 1 minute on test devices when exact alarm is granted, and within 15 minutes when it is not.

### F4 Home-screen widgets

Two Glance widgets, both free: a small progress ring, and a medium widget with elapsed and remaining time, the next milestone and a start/stop button.

- Acceptance: start and stop from the widget work without opening the app. The display is never more than 1 minute stale while the screen is on. Widgets support light and dark modes.

### F5 History, calendar and consistency streaks

History is unlimited and free, with a month calendar, a list of fasts and weekly totals.

- Streaks count days the user completed their planned fast, never fast length. Breaking a streak shows neutral copy and offers a "streak freeze" (1 per week).
- Acceptance: there are no badges or rewards tied to duration. The calendar and list match the database after edits.

### F6 Weight, water and electrolyte logging + Health Connect

Users log weight, water and electrolytes (sodium, potassium, magnesium). Logs are quick-add from the home screen.

- Health Connect: write WeightRecord and HydrationRecord, and read WeightRecord. Permissions are asked only when the user turns on sync.
- A Hide weight mode removes weight from every screen.
- Acceptance: with Health Connect on, entries appear in Health Connect within 1 minute. The app works fully with Health Connect off. Each permission has a written justification ready for Play review.

### F7 Extended-fast safety layer (24h+)

Starting a fast of 24h or more shows a short checklist: hydration, electrolytes, medications and who to call. Fasts over 72h need an explicit acknowledgement.

- Symptom check-ins arrive every 8h during fasts of 24h or more. Red-flag answers (fainting, chest pain, palpitations, confusion, severe weakness) open a full-screen "End your fast and seek medical care" screen with the local emergency number (Singapore: 995).
- Ending a fast of 48h or more shows refeeding guidance scaled to the fast's length.
- Acceptance: this layer cannot be turned off for fasts of 24h or more and is never behind the paywall. The red-flag screen works offline.

### F8 Export, backup and settings

Users can export all data to CSV and JSON, back up locally and restore from a backup file. Settings show the subscription's renewal date and a one-tap cancel link to Play.

- Acceptance: a full round trip (export, reinstall, restore) loses no data.

### F9 Evidence-graded body timeline

A timeline under the active timer explains what is probably happening at each stage, such as glycogen falling and ketones rising. Every claim carries one of four labels: Established, Emerging, Animal-only or Unknown. The timing of autophagy in humans is labelled Unknown.

- The content is static, versioned in the app and written in Robert's voice.
- Acceptance: every claim has a label and a source in the content file.

### F10 Shift+ subscription

Shift+ launches with only two things:

- The Fasting Shift guided program, a 21-day plan with a daily lesson and a fasting target, linked to the book chapters.
- Insights: weekly consistency trends, weight versus fasting hours, and average fast by weekday.

Acceptance: purchase, restore, upgrade and cancel all work through Play Billing. A book code unlocks 3 months free.

## Safety and compliance

Safety is a P0 launch requirement, and no safety feature is ever paywalled. A clinician must review this section, and a lawyer must review the claims and disclaimers, before launch.

| ID | Requirement | Covered by |
| --- | --- | --- |
| SR-1 | Hard block (education-only mode): under 18, pregnant or breastfeeding, current or past eating disorder, BMI under 18.5, type 1 diabetes | F1 |
| SR-2 | Soft gate (clinician acknowledgement; fasts over 24h locked): insulin or sulfonylureas, SGLT2 inhibitors, diuretics or blood-pressure drugs, GLP-1 drugs | F1 |
| SR-3 | Fasts over 24h need the checklist; fasts over 72h need explicit acknowledgement; no rewards for duration | F2, F5, F7 |
| SR-4 | Red-flag symptoms lead to the full-screen stop screen with the local emergency number | F7 |
| SR-5 | Refeeding guidance appears automatically after fasts of 48h or more | F7 |
| SR-6 | Eating-disorder-safe design: no shaming copy, no leaderboards, a hide-weight mode, helpline links (Singapore first) | F1, F5, F6 |
| SR-7 | Every physiological claim carries an evidence label | F9 |
| SR-8 | "Not medical advice" disclaimer in onboarding, the store listing and at the start of extended fasts | F1, F7 |
| SR-9 | Health data stays on the device, encrypted at rest, and never goes to analytics or ad SDKs | Architecture |

### Google Play checklist

- [ ] Complete the Health apps declaration in Play Console (App content)
- [ ] Publish the privacy policy as a live web page on FastingShift.com, not a PDF
- [ ] Fill in the Data safety form to match SR-9
- [ ] Write a justification for each Health Connect data type and add the privacy-policy rationale activity
- [ ] Use `SCHEDULE_EXACT_ALARM` only; never declare `USE_EXACT_ALARM`
- [ ] Rate the app for adults; no "lose X kg in Y days" claims in the listing

**Claims rule.** Wellness language only ("track your fasting", "build a consistent habit"). No disease claims such as "reverses diabetes" or "treats PCOS", which would push the app toward medical-device rules under the FDA General Wellness guidance (revised January 2026) and Singapore's HSA.

## Monetization

Generous freemium with no ads, plus Shift+ at US$34.99 a year, about half what Zero and BodyFast charge. Trust is the product, so billing must never produce a 1★ review.

| Plan | Price (US) | Notes |
| --- | --- | --- |
| Shift+ annual | $34.99 | 7-day free trial; regional pricing (about S$34.98, lower in ID/MY/PH/VN) |
| Shift+ monthly | $5.99 | No trial |
| Shift+ lifetime | $79.99 | Signals "no subscription trap"; suits book readers |
| Book code | 3 months free | QR code in *The Fasting Shift* opens the guided program |

### Paywall rules

1. The onboarding offer is skippable, with "Continue free" as visible as "Start trial".
2. After that, upsells appear only when the user taps a Shift+ feature. No pop-ups on launch or after a fast.
3. The app sends a reminder 3 days before a trial ends and 7 days before an annual renewal.
4. Settings always show the plan, the renewal date and a one-tap cancel link.
5. Safety, widgets, every protocol, history and export are never paywalled.

Billing uses Play Billing through RevenueCat: base plans, offers, grace periods, real-time notifications and paywall A/B tests without building a backend.

## Technical architecture

Native Kotlin with Jetpack Compose. Works offline and needs no account or backend. The fast is stored as data (start time and target end), so the UI, widgets and notifications all compute elapsed time from the clock and nothing has to keep running.

Every surface reads from one local database, so a timer can never drift or lose a fast. Health Connect and export are optional mirrors, not the source of truth.

| Layer | Choice |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | Android MVVM with a separate Kotlin domain module |
| Storage | Room + SQLCipher (encrypted at rest), DataStore for settings |
| Background | AlarmManager for fast alerts; WorkManager for check-ins and widget refresh |
| Widgets | Jetpack Glance |
| Health | Health Connect SDK |
| Billing | RevenueCat SDK over Play Billing |
| DI / tests | Hilt; JUnit + Turbine; Compose UI tests; device tests on Samsung, Xiaomi, Pixel |
| Min / target SDK | 26 / latest required by Play |

### Data model (core tables)

| Table | Key fields |
| --- | --- |
| fast | id, start_at, target_end_at, ended_at, protocol_id, status, note |
| protocol | id, name, type (window / duration / weekly), fast_hours, eat_hours, is_custom |
| log_weight / log_water / log_electrolyte | id, at, value, unit, source (manual / health_connect) |
| symptom_checkin | id, fast_id, at, answers, red_flag |
| screening | answered_at, answers, gate_result (ok / soft / block) |
| program_progress | program_id, day, completed_at |

## Product priorities after the core tracker

Keep the free Android experience usable from the first fast through a change of routine. The beta will test these priorities.

| Priority | What the tester can do |
| --- | --- |
| Review a week | Open a calendar date, correct or add a fast, and answer or skip a dated weekly reflection. Keep next week's plan, adjust the schedule or take a week off. |
| Invite a friend | Share the invitation through an existing chat. Both people can choose their own scheduled days and send a short message of encouragement. |
| Evaluate Shift+ | Read the sample of Robert’s 21-day book companion. Full lessons require author-approved copy. Test purchase restoration on Google Play before distribution. |
| Keep specialist tools optional | Open the existing health logs, clinician report, Health Connect insights or Ramadan and Sunnah settings when wanted. Test the supported languages with native readers. |

## Build plan, risks and open questions

10 weeks to a Play closed test, then 2 weeks of closed test before production. New personal developer accounts must run a closed test with testers for 14 days before production access; an organisation account under ONN Group avoids that.

1. **Weeks 1–2 Foundations:** project setup, Room schema, timer engine, alarm scheduler with boot and time-zone handling, plus unit tests for timer maths.
2. **Weeks 3–4 Core loop:** onboarding and safety screening (F1), the timer screen and protocols (F2), reminders and the reliability check (F3).
3. **Weeks 5–6 Daily use:** widgets (F4), history and streaks (F5), weight, water and electrolytes plus Health Connect (F6).
4. **Week 7 Safety:** the extended-fast layer (F7), then clinician review of the copy.
5. **Week 8 Trust:** export and restore (F8), the body timeline content (F9), the privacy policy page.
6. **Week 9 Revenue:** RevenueCat, the Shift+ paywall, the guided program and insights (F10), book codes.
7. **Week 10 Ship:** Play declarations, Data safety form, store listing and screenshots, then the closed test on Samsung, Xiaomi and Pixel devices.

### Top risks

| Risk | Mitigation |
| --- | --- |
| A user is harmed during an extended fast | SR-1 to SR-5, clinician review, conservative copy, adults only |
| The app enables disordered eating | Screening, no duration rewards, hide-weight mode, helplines |
| Play rejects the app (health declaration, alarms, Health Connect) | Minimal permissions, SCHEDULE_EXACT_ALARM only, matching privacy policy |
| Freemium converts too little | Onboarding trial, lifetime plan, book funnel, RevenueCat paywall tests |
| OEM battery killers delay alerts | Reliability-check screen, alerts tested on Samsung and Xiaomi |

### Open questions

- [ ] Final app name and package ID: is "Fasting Shift" available on Play? (Code uses `com.fastingshift.app` as a placeholder.)
- [ ] Developer account: personal or organisation (ONN Group LLP)?
- [ ] Who is the clinician reviewer for the safety copy?
- [ ] Which book chapters map to the 21-day program?
- [ ] Analytics tool: privacy-respecting, opt-in (for example PostHog with health fields excluded)?
