# Fasting Shift beta worklist

The question for this beta is whether people can manage a real change of plan on their phone, then find enough value in the four-week review to choose Shift+. Use fictional fasting records for observed sessions. Testers can use the timer, edit history, adjust My week and export records for free.

## Ready to try in the candidate

| Area | What to look for |
| --- | --- |
| First use and Timer | Get started leads to goal selection and screening. The tour is optional. The selected window and Start sit together. While a fast runs, Change view offers elapsed time, remaining time and finish time. |
| History and Log | Hours and completed planned days have separate labels. Dated bars open individual records. Water portions and electrolyte amounts are available together with their controls. |
| Settings | My week and Appearance open directly. Related settings have their own pages. The whole switch row responds to a tap, the current choice is visible, and Back returns to the overview. |
| Shift+ | The free example uses labeled fictional records. The personal review covers the last four **finished** Monday–Sunday weeks, including planned days completed, moved starts, skipped dates and optional weekly reflections. It links back to My week. A book sample is available. |

[Candidate changes and automated checks](https://github.com/nicholsmindset/fasting/pull/14) · [Design findings and sources](layout-review.md)

## 1. Watch five people use the app

The **beta facilitator** recruits five Android users who have not seen the redesign. Give them five tasks without a walkthrough. Record completion unaided, first wrong tap, and any wording they misunderstood. For accessibility, repeat the main paths on a physical phone with large text and TalkBack.

| Task prompt | Observe |
| --- | --- |
| “Start the window shown. What time will it finish?” | Starts from Timer and finds the finish time. Declining an optional reminder still allows starting. |
| “Dinner is late on Tuesday. Move that start from 8 pm to 9:30 pm. What happens on Wednesday?” | Changes Tuesday only, finds the updated reminder time and identifies Wednesday’s usual start. |
| “Yesterday’s record ended at the wrong time. Correct it, then tell me how many hours you fasted this month.” | Edits the correct record even when two bars have the same duration. Distinguishes monthly hours from completed planned days. |
| “Log a glass of water, then switch the app to dark mode.” | Finds the portion control, operates the appearance picker and can navigate back. |
| “Whose records are in the example review? Explain a week that began partway through and a week off. Where would you change next week?” | Recognizes the fictional sample, explains what counts toward each week and reaches My week. Use matching seeded examples for any states the free sample does not show. |

Repeat the affected task after a change if two people independently get stuck at the same point. Investigate every wrong-record edit, unwanted purchase launch, or accessibility barrier before widening the beta.

## 2. Verify delivery and distribution

| Owner | To do | Done when |
| --- | --- | --- |
| Android QA | Run the Android build, signed-upgrade test and locked-start reminder check for the candidate. | Save the tested commit, run links and installable APK. The old fast and running timer survive the upgrade. The moved reminder fires while PIN-locked and the cancelled alarm does not produce a duplicate. |
| Device QA | Move tomorrow’s start, relaunch and lock a physical Pixel and Samsung under their normal battery settings. Repeat with reminders disabled and with a timezone change. | Record build, model, Android version, expected and actual local delivery time, any duplicate, and what Timer shows for the next regular day. Granting or denying permission produces the expected behavior on both phones. |
| Play Console owner | Match the registered upload certificate, configure the internal tester list, and distribute the tested release through the internal track. | A tester opts in with the intended Google account, installs from Play and upgrades over a previous Play version. History and the original active target survive. [Internal publishing instructions](play-internal-testing.md) |
| Billing QA | On the configured Play build, use license testers for purchase, accelerated renewal, dismissal of checkout, subscription cancellation, expiry, reinstall and restore. Repeat restore with an account that owns nothing. | The app shows the offer’s Play-localized price, billing interval and renewal terms. Access follows the entitlement, survives reinstall after restore, continues through a cancelled paid term and ends at expiry. The free timer and history remain accessible throughout. Save the account region, offer and result without recording account identifiers. |

## 3. Test return use and the paid proposition

The **beta facilitator and Robert** run a voluntary 14-day pilot. Use a short opt-in diary or a follow-up interview. Record task outcomes without collecting fasting logs or health readings. Pairs can use the [two-phone invitation guide](friends-beta.md).

| Question | Evidence to collect | Decision |
| --- | --- | --- |
| Did the app earn a second week? | Report the number who reviewed History or changed their plan on days 8–14 divided by all enrolled participants whose full 14 days elapsed. Include unknown outcomes separately and explain them. Ask what brought each returning person back. | Prioritize the first recurring friction. If people start timers but never revisit their plan, investigate that before expanding the review. |
| Would someone choose Shift+? | After a participant has tried the free workflow, show the sample and the intended local annual price. Ask which real decision it would help them make and whether they would choose that offer, keep the free app, or decide later. Record counts, reasons and the price shown. | Treat responses as stated interest. License-test purchases verify checkout. Measure actual paid uptake separately if a commercial offer is launched. |
| Does the personal review stay useful over time? | Invite willing participants with four completed weeks of real plan history to explain their own review and describe a decision it helped them make. New two-week users can only assess their available history and the fictional example. | Use actual repeat use and reasons for choosing or declining the offer when deciding whether to expand the paid product. |

## 4. Prepare for a wider release

- [ ] **Robert and editorial** – approve one finished book lesson in Robert’s voice and test it with sample readers. Keep full-program promotion tied to the chapters approved for publication.
- [ ] **Clinical and language reviewers** – verify safety screening, duration guidance, refeeding and emergency wording. Have native speakers review Malay, Indonesian and Simplified Chinese on-device, including the schedule and billing terms.
- [ ] **Release owner** – close the device and Play checks above, record unresolved findings and review the privacy, store listing and Health Connect declarations in the [launch checklist](../README.md#before-launch-not-code) before a public rollout.
