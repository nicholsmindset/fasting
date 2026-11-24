# Fasting Shift — competitive moats (research, Nov 2026)

## 1. What competing apps offer

These comparisons use the publishers' descriptions, checked on 23 November 2026. Features and offers can vary by platform and country. The sources establish product behavior. They do not establish which features improve health or retention.

| Product | What the publisher describes | Decision for Fasting Shift |
| --- | --- | --- |
| [Zero](https://zerofasting.zendesk.com/hc/en-us/articles/55193563343003-Is-Zero-still-free-Understanding-the-free-experience-and-Zero-Plus) | Free timer and editing, one rolling week in the free in-app view, longer-range statistics in Plus. | Show people how to find unlimited History and correct yesterday's fast. Use Shift+ for interpretation and optional programs. |
| [Fastic](https://play.google.com/store/apps/details?id=de.fastic.app) | Its Play listing places fasting buddies and progress sharing in Plus. | Test a free invitation between two existing friends before charging for group features. |
| [Easy Fast](https://easyfastapp.com/) | Accountless timer, optional cloud sync, and widgets in Pro. | Lead with free widgets and local history. Demonstrate the timer across a restart and the backup before promising reliability. |
| [BodyFast](https://help.bodyfast.app/hc/en-001/articles/34673414292242-How-does-the-BodyFast-Coach-work) | Adjustable weekly schedules, challenges and a weekly break day in Coach. | Help people adjust their next week from History. Measure whether they use the existing planner before building adaptive coaching. |

## 2. The first week

| Moment | What the user needs | How to check the release |
| --- | --- | --- |
| First launch | Understand what is free, complete screening, pick a plan and reach the timer. | Walk through the normal, restricted and education-only paths. Time five first-time users against the PRD's 90-second goal. Record where they hesitate. They should be able to continue free and decline notifications. |
| First fast | See when the fast began, when it ends and how to stop or correct it. | Start, background, lock, change time zone and reopen. End early and correct the time. History and the widget should show the same data. |
| End of the week | Review progress without being rewarded for longer fasts. | Check the consistency arc against planned days. Test an empty week, a fast crossing midnight, and a calendar edit with TalkBack and large text. |
| Returning after a break | Pick a new schedule without explaining a missed fast. | Ask a tester to reschedule a day, review the next reminder and export a backup unaided. |
| Considering Shift+ | Know what is free, what costs money and how to recover a purchase. | On a Play test build, check localized prices, failed purchase, restore and expiration against the actual entitlement. |

Keep four familiar destinations. Use **Timer** for today, **History** for reviewing, editing and sharing, **Log** for optional measurements, and **Settings** for schedules, reminders, privacy and optional modes. Keep core actions accessible without a subscription. Use the Nichols Mindset teal palette, neutral early-end state and text alternatives. A day-detail sheet is worth prototyping if testers struggle to edit a short calendar bar. The [calendar concept on Dribbble](https://dribbble.com/shots/15594275-Fasting-App) inspired the layout. Validate the interaction with real records and assistive technology.

## 3. Product priorities

**Fasting Shift is an everyday intermittent-fasting tracker for adults.** The core experience is starting a fast, adjusting it when life changes, and reviewing a week of progress. Private sharing is optional. The main navigation and store screenshots should lead with those everyday jobs.

### Reliability, safety and trust

- Make the timer and unlimited history useful from day one. Keep stopping, correcting a time, and exporting history easy to find.
- Keep screening and safety guidance available on the free plan. Users choose whether to share a doctor report or enable Health Connect.
- Explain which records stay on the phone and what happens when users export, join a challenge, buy Shift+ or delete their data.

### Fasting with friends

Today a host sends a challenge with a name, schedule and dates. Each person previews the plan and tracks their own progress. The host cannot see who joined. Sending a progress card is a separate, deliberate action.

Two friends can want accountability while following different schedules. The strongest group opportunity is a single invitation to plan a week together and an easy way to check in. Easy Fast advertises private groups. FastingPals offers buddies and reactions. Their listings establish that these features exist. The pilot will test whether Fasting Shift users want them. [Easy Fast](https://easyfastapp.com/invite/nbrO) · [FastingPals](https://play.google.com/store/apps/details?id=com.fastingpals.app)

| Priority | Next improvement | How to test it |
| --- | --- | --- |
| First beta | Let a friend or partner invite someone to a shared week from Challenges. Preview the dates and explain what others see. Give recipients without the app a web preview, install link and reusable invitation. | Send a real invite to two Android phones, including one fresh install. Both should reach the intended plan and understand that progress is private. |
| Pilot | Test a “Follow my own plan” week. Each person chooses their own schedule. Offer a voluntary “Joining tomorrow” or “Cheering you on” message through an existing chat. | Ask friends, partners and coworkers to try it for two weeks. Record whether anyone returns the second week, whether the host needs to chase replies and whether anyone feels pressure to explain an absence. |
| After the pilot | If participants want to see who joined, prototype an invite-only group with a nickname, voluntary check-in, one encouragement reaction, mute, leave and expiring invites. | On two devices confirm that only explicitly shared check-ins appear, revoked links cannot join and removing a check-in removes it from the other member's view. |

A synced group would introduce off-device membership and check-in data. Explain this before joining and give members control over removal. Fast length, exact times, health readings, weight and reasons for skipping stay private. A group invite must preserve the same personal safety gates as a solo fast. Group success is measured by voluntary participation and repeat use.

### Optional fasting modes

People who opt in to Ramadan and Sunnah can use a local prayer timetable, Hijri calendar and qada tracker. Keep these features under optional settings and test them in each supported language. Invitations, onboarding and the default store story use everyday fasting examples.

### Personal insights
- Glucose (Health Connect) and ketone (local) logging.
- Sleep and resting heart rate from Health Connect, shown on fasting vs non-fasting days, with
  an honest "correlation, not cause" note.
- The ongoing notification already mirrors to watches. Revisit a dedicated Wear OS tile after testing on a watch.

## Scope for the first release
- Use private invites and the share sheet for accountability. A public feed would require moderation and new privacy controls.
- Keep the log focused on fasting and optional measurements.
- Present fasting information as wellness education and have a clinician review the safety wording before launch.

## Release decision

**Internal testing.** Build the release AAB with the registered upload key and an increasing version code. Install from the Play internal track and verify an upgrade preserves a running timer and history. Inspect 64-bit native libraries, including SQLCipher, for 16 KB alignment and open the encrypted database on a 16 KB device. Exercise the manual checks in the [README](../README.md). [App signing](https://developer.android.com/studio/publish/app-signing) · [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)

**Public release.** Verify the public privacy URL, named publisher and monitored contact. Reconcile the release manifest and billing SDK with the Health apps declaration, Health Connect justifications and Data safety form. Have the appropriate reviewers sign off on clinical copy and localized safety wording. Test purchases, cancellations, restoration and expiration using a Play license tester. Confirm any testing requirements that apply to the actual developer account. [Google Play Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291) · [Google Play health policy](https://support.google.com/googleplay/android-developer/answer/16679511) · [RevenueCat testing](https://www.revenuecat.com/docs/test-and-launch/sandbox/google-play-store)

**After launch.** Use consented beta feedback and aggregate Play vitals to prioritize fixes. In the pilot, record task completion and obstacles without collecting fasting times or health logs. Only expand group functionality if people voluntarily return for a second week and identify a need that the simple invite cannot meet.
