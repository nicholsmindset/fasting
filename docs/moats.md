# Fasting Shift — competitive moats (research, Nov 2026)

## 1. The market in one paragraph

General fasting apps have converged on the same product: a timer, fasting "stages", weight
tracking and a subscription. The 2026 roundups agree on roles — **Zero** is the cleanest timer,
**Simple** is the AI-coach app, **Fastic** is the community/challenge app and **BodyFast** is
the weekly-plan coach. Paid tiers cost about US$40–80 a year, and the sources disagree on
prices, so check the stores before quoting them. A timer alone is not defensible: it can be
copied in a weekend.

| App | Positioning | Free | Paid (approx.) | Gaps we can exploit |
| --- | --- | --- | --- | --- |
| Zero | Clean timer, data | Timer, basic stats | Plus ~$70/yr: plans, analytics, AI meal logging | No religious fasting, no safety screening, iOS-first look |
| Simple | AI coach, food photos | Limited | ~$60–80/yr | Heavy upsell; diet-app framing |
| Fastic | Community, challenges | Timer | Plus ~$60–80/yr | Social needs an account and a feed |
| BodyFast | Weekly coach plans | Timer, 10+ plans | Coach ~$40–70/yr | Reports of bans and language bugs |
| Muslim trackers (Sawm, Thabit, Inziva, Qadaa Tracker, FastTrekker) | Ramadan, qada, Sunnah calendars | Mostly free or one-time | Small IAPs | Single-purpose, mostly iOS, no real fasting timer, no safety layer, few Malay/Indonesian versions |

## 2. What users complain about

From 1–3★ reviews summarized in the 2026 comparisons (several written by competing apps, so
treat percentages as indicative):

1. **Basic timer features behind a paywall** — the most common complaint across all apps.
2. **Billing traps** — auto-renewal, charges after cancelling, unclear trials.
3. **Distrust about data** — users suspect their health data is used for more than tracking.
   There is little hard evidence either way; the feeling alone is an opening.
4. Support quality and localization bugs.

## 3. Our moats (ranked by defensibility)

### Moat 1 — The complete Muslim fasting companion (strongest)
No app combines a real fasting timer, safety screening and Health Connect with Ramadan,
Sunnah fasts and qada. Muslim trackers are small single-purpose iOS apps; the big timer apps
ignore religious fasting. We already have offline suhoor/iftar times for MUIS, JAKIM, KEMENAG
and MWL, plus Malay, Indonesian and Chinese — the core of Southeast Asia's ~250M Muslims.

Build: Hijri calendar with local moon-sighting offset; Sunnah fast calendar (Mon/Thu,
Ayyamul Bidh, Arafah, Tasu'a/Ashura, 6 of Shawwal; never on Eid or tashreeq days) with
reminders; qada (make-up) tracker; one tap to start a suhoor→maghrib fast.

Why it lasts: correctness per country (calculation methods, moon sighting), language and
community trust take years; a general-market competitor has no reason to invest.

### Moat 2 — Safety & trust as the brand
We are the only app with a clinical-style safety gate (screening, medication rules, check-ins,
refeeding). Make it visible and useful outside the app:
- **Doctor report PDF** to bring to an appointment (fasts, symptoms, red flags, weight trend,
  screening and medications) — turns clinicians into referrers.
- **Trust center**: on-device only, encrypted, no account, no ads or trackers; what leaves the
  device and when; one-tap export and delete.
- **Ramadan safety** for people on glucose-lowering medication (talk to your clinician first;
  symptoms that mean break the fast).
- Never paywall the timer (answers complaint #1); honest billing reminders already shipped.

### Moat 3 — Social without surveillance
Fastic's community needs an account and a feed. Ours needs neither:
- **Challenges** shared as a link (parameters only, no server); each person tracks locally.
- **Share cards** for a finished fast, weekly recap or challenge progress — accountability
  through the chat apps people already use (WhatsApp, Telegram).
Family and mosque groups doing Sunnah fasts together is the natural use.

### Moat 4 — Data depth
- Glucose (Health Connect) and ketone (local) logging.
- Sleep and resting heart rate from Health Connect, shown on fasting vs non-fasting days, with
  an honest "correlation, not cause" note.
- Wear OS tile: deferred (needs a device to test); the ongoing notification already mirrors to
  watches.

## 4. What we deliberately don't build
- A server-backed social feed (cost, moderation, privacy).
- AI food-photo logging (not our job; competes with diet apps).
- Medical claims: everything stays wellness and education, with clinician review before launch.

## Sources
- [Best Fasting App (2026): 9 Intermittent Fasting Apps Compared](https://www.lilachbullock.com/best-fasting-app/)
- [5 Intermittent Fasting Apps Ranked: Zero, Simple… (2026) — Unstar](https://unstar.app/blog/zero-simple-fastic-life-fasting-bodyfast-intermittent-fasting-apps-ranked-2026)
- [Calorie tracking apps ranked by 1-star reviews (2026) — Unstar](https://unstar.app/blog/calorie-tracking-apps-ranked-1-star-reviews-2026)
- [Best Intermittent Fasting Apps in 2026 — Kompanion](https://kompanionapp.com/en/best-intermittent-fasting-apps/)
- [Best Fasting Tracker Apps 2026 — fitness-tracking.com](https://www.fitness-tracking.com/fasting/)
- [Best Intermittent Fasting Apps (2026–2027) — PlateLens](https://platelens.app/blog/best-intermittent-fasting-apps)
- [8 Best Fasting Apps in 2026 — Nutrola](https://nutrola.app/en/blog/8-best-fasting-apps-in-2026)
- [Sawm: Fasting Tracker (App Store)](https://apps.apple.com/us/app/-/id6763971933)
- [Thabit: Ramadan Fast Tracker (App Store)](https://apps.apple.com/us/app/id6759208175)
- [Inziva: Fasting Tracker (App Store)](https://apps.apple.com/app/id6757693344)
- [Qadaa Tracker (App Store)](https://apps.apple.com/app/id6749829131)
- [Muslim Fasting Calendar Alarm (App Store)](https://apps.apple.com/sa/app/id6504528287)
- [Certified Fasting reviews — Trustpilot](https://ca.trustpilot.com/review/certifiedfasting.com)
- [Building complication data sources for Wear OS — Android Developers Blog](https://android-developers.googleblog.com/2025/08/building-complication-data-sources-wear-os.html)
