# Fasting Shift — competitive moats (research, Nov 2026)

## 1. What competing apps offer

These comparisons use the publishers' descriptions, checked on 23 November 2026. Features and offers can vary by platform and country. The sources establish product behavior. They do not establish which features improve health or retention.

| Product | What the publisher describes | Decision for Fasting Shift |
| --- | --- | --- |
| [Zero](https://zerofasting.zendesk.com/hc/en-us/articles/55193563343003-Is-Zero-still-free-Understanding-the-free-experience-and-Zero-Plus) | Free timer and editing, one rolling week in the free in-app view, longer-range statistics in Plus. | Show people how to find unlimited History and correct yesterday's fast. Use Shift+ for interpretation and optional programs. |
| [Fastic](https://play.google.com/store/apps/details?id=de.fastic.app) | Its Play listing places fasting buddies and progress sharing in Plus. | Keep a free invitation between friends and test whether pairs choose to use it again. |
| [Easy Fast](https://easyfastapp.com/) | Accountless timer, optional cloud sync, and widgets in Pro. | Lead with free widgets and local history. Demonstrate the timer across a restart and the backup before promising reliability. |
| [BodyFast](https://help.bodyfast.app/hc/en-001/articles/34673414292242-How-does-the-BodyFast-Coach-work) | Adjustable weekly schedules, challenges and a weekly break day in Coach. | Help people adjust their next week from History. Check whether the revised plan matches the start they see on Timer. |

## 2. The first week

| Moment | What the user needs | How to check the release |
| --- | --- | --- |
| First launch | Understand what is free, complete screening, pick a plan and reach the timer. | Walk through the normal, restricted and education-only paths. Time five first-time users against the PRD's 90-second goal. Record where they hesitate. They should be able to continue free and decline notifications. |
| First fast | See when the fast began, when it ends and how to stop or correct it. | Start, background, lock, change time zone and reopen. End early and correct the time. History and the widget should show the same data. |
| End of the week | Review progress without being rewarded for longer fasts. | Check the consistency arc against planned days. Test an empty week, a fast crossing midnight, and a calendar edit with TalkBack and large text. |
| Returning after a break | Pick a new schedule without explaining a missed fast. | Ask a tester to reschedule a day, review the next reminder and export a backup unaided. |
| Considering Shift+ | Know what is free, what costs money and how to recover a purchase. | On a Play test build, check localized prices, failed purchase, restore and expiration against the actual entitlement. |

Keep four familiar destinations. Use **Timer** for today, **History** for reviewing, editing and sharing, **Log** for optional measurements, and **Settings** for schedules, reminders, privacy and optional modes. Keep core actions accessible without a subscription. Use the Nichols Mindset teal palette, neutral early-end state and text alternatives. Tapping a calendar date opens its plan and fasting records, including entries too short to tap easily. Check that a tester can distinguish time on that date from a whole overnight fast. The [calendar concept on Dribbble](https://dribbble.com/shots/15594275-Fasting-App) inspired the layout. Validate the interaction with real records and assistive technology.

## 3. Product priorities

**Fasting Shift is an everyday intermittent-fasting tracker for adults.** The core experience is starting a fast, adjusting it when life changes, and reviewing a week of progress. Private sharing is optional. The main navigation and store screenshots should lead with those everyday jobs.

### Product decisions after reviewing the builds and earlier proposals (24 November 2026)

| Decision | Product scope | Evidence to collect |
| --- | --- | --- |
| Keep the free daily experience | Timer, editable history, My Week, reminders, CSV history transfer and local backup. Show one everyday example in the store listing: a late dinner changes Wednesday, the user corrects the time, and Thursday keeps its usual plan. | Ask new users to complete screening, find their first action, correct yesterday and move one planned start unaided. Record where they pause. |
| Extend the existing weekly recap | Ask one optional question, “Did this schedule fit your week?” Save the answer on the phone. Keep, Adjust and Take a break are separate choices for next week. | Observe whether people take the action they intended and voluntarily use the recap again the following week. A skipped question should leave the schedule untouched. |
| Pilot a private week with a friend | Each participant can keep the invitation's routine or follow their own planned days. A person can send encouragement through their usual chat. Apply the same screening rules on every entry path. | Try with pairs on two phones for two weeks. Ask each person what the other can see and whether they would do another week. Record confusion and pressure to disclose. |
| Give Shift+ a distinct reason to exist | Test interest in Robert’s book companion and descriptive personal insights. The build offers a sample of the program. Release full lessons after the author approves completed copy and test purchase restoration on Play. | Ask sample readers what they used and whether the complete program would be worth buying. Confirm access after reinstall with a license tester. |
| Keep optional features easy to find | The four main destinations are Timer, History, Log and Settings. Health Connect measurements, doctor reports and seasonal observances belong behind an explicit choice. Use everyday fasting for default onboarding, groups and the first store screenshots. | Check that a first-time user can reach Timer without configuring a health integration or seasonal mode. Test those optional paths separately. |

**Design and competitive evidence.** Simple documents adding a past fast from its calendar and requires a running fast to finish before its times can be edited. Give a tester an incorrect start time while their timer is running and observe whether they find the correction. The Ritmo design study offers a reference for clear timer hierarchy and consistent icons. Keep our teal, text labels, neutral early endings and a consistency-based ring. [Simple help](https://help.simple.life/en/articles/9887903-how-to-log-and-edit-fasts) · [Ritmo design study](https://dribbble.com/shots/27884137-Ritmo-Intermittent-Fasting-App)

**Android beta scope.** Focus on the timer, flexible planning, weekly review and private invitations. Keep sharing voluntary and the app ad-free. Google Ads acquisition is a separate launch decision. Confirm that people can install, start and return before budgeting for it.

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
| Pilot | Test a “Follow my own plan” week. Each person chooses their own schedule. Offer an optional “Cheering you on this week!” message through an existing chat. | Ask friends, partners and coworkers to try it for two weeks. Record whether anyone returns the second week, whether the host needs to chase replies and whether anyone feels pressure to explain an absence. |

Joining uses the same screening rules as a solo fast. The invitation opens the original challenge on each phone. Personal schedules, weekly reflections and individual fasts stay on the participant’s phone unless they explicitly export a backup. The encouragement message contains only its text.

### Optional fasting modes

People who opt in to Ramadan and Sunnah can use a local prayer timetable, Hijri calendar and qada tracker. Keep these features under optional settings and test them in each supported language. Invitations, onboarding and the default store story use everyday fasting examples.

### Personal insights
- Glucose (Health Connect) and ketone (local) logging.
- Sleep and resting heart rate from Health Connect, shown on fasting vs non-fasting days, with
  an honest "correlation, not cause" note.

## Scope for the first release
- Use private invitations and Android’s share sheet for encouragement.
- Keep the log focused on fasting and optional measurements.
- Present fasting information as wellness education and have a clinician review the safety wording before launch.

## Release decision

**Internal testing.** Build the release AAB with the registered upload key and an increasing version code. Install from the Play internal track and verify an upgrade preserves a running timer and history. Inspect 64-bit native libraries, including SQLCipher, for 16 KB alignment and open the encrypted database on a 16 KB device. Exercise the manual checks in the [README](../README.md). [App signing](https://developer.android.com/studio/publish/app-signing) · [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)

**Public release.** Verify the public privacy URL, named publisher and monitored contact. Reconcile the release manifest and billing SDK with the Health apps declaration, Health Connect justifications and Data safety form. Have the appropriate reviewers sign off on clinical copy and localized safety wording. Test purchases, cancellations, restoration and expiration using a Play license tester. Confirm any testing requirements that apply to the actual developer account. [Google Play Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291) · [Google Play health policy](https://support.google.com/googleplay/android-developer/answer/16679511) · [RevenueCat testing](https://www.revenuecat.com/docs/test-and-launch/sandbox/google-play-store)

**After launch.** Use consented beta feedback and aggregate Play vitals to prioritize fixes. In the pilot, record task completion and obstacles without collecting fasting times or health logs. Use the second week of testing to identify confusing steps in the invitation and schedule choices.
