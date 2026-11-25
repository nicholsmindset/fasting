# Fasting Shift Android product roadmap

**25 November 2026 · Android beta**

The first customer to test is someone who already fasts, but whose dinner times or shifts change during the week. They should be able to see when a fast would finish, change Tuesday without changing Wednesday, and correct a missed entry. Shift+ should earn repeat use by showing what changed often enough to reconsider next week’s routine.

## What is in the app today

| Experience | Working scope |
| --- | --- |
| First use and Timer | Goal selection, screening and optional reminders. Preview a window’s local finish before starting, including an earlier start. During a fast, switch between elapsed time, remaining time and finish time. |
| My week | Inspect the actual starts on a date, compare them with the usual schedule, move the first start, skip that date or restore it. Selecting the usual first time clears the exception. |
| History and Log | View hours separately from planned days completed. Open a dated record to edit it or add a missed fast. Log water, weight and electrolytes. Full history and export are available in the free app. |
| Shift+ | Review the four most recent finished Monday–Sunday weeks. A weekday appears as a pattern after at least two distinct dates were moved or skipped. Tap it to see the dates, revised start times or skips, then open My week to decide what to change. Free users can inspect a labeled fictional example. |
| Appearance and preferences | System, light and dark appearance, optional weight display, reminder settings, backup and restore. One-day edits and reviews have English, Malay, Indonesian and Simplified Chinese resources. Native review is pending. |

## Delivery order

| Priority and stage | Deliverable | Completion check | Dependency |
| --- | --- | --- | --- |
| P0 · Candidate verification | Test the Android build, signed upgrade, local time handling, reminders, and the public entry points for History, Timer, My week and Shift+. | Save the candidate, installable APK and test results. Check that a moved alarm replaces the original, tomorrow keeps its normal time, and an upgrade preserves both history and the active target. | Engineering and emulator. Current results below. |
| P0 · Installation and recovery | Put returning testers on a consistently signed distribution track. Exercise backup, restore and upgrade on a phone with fictional records. | Install the next version over the previous version and find the same active target, dated history and settings. Document a tested recovery route for older staging installations with a different certificate. | Registered signing identity, tester account and phone. |
| P1 · UX, visual design and accessibility | Review onboarding, idle and active Timer, a date with two starts, History corrections, quick logging, Settings and the Shift+ detail. Include a small phone, both themes, large type, 12/24-hour clocks and TalkBack. | Important times remain readable, controls are reachable, individual records have meaningful spoken labels, and Back returns to the expected screen. Record the device, screen and first failed action for each issue. | Automated coverage plus physical-device and native-language review. |
| P1 · Five-person usability round | Watch five first-time Android testers complete the tasks below. Record first wrong tap, assistance and final outcome. | Fix every wrong-record edit or accidental purchase action. If two people independently get stuck at the same step, revise that step and repeat it with someone unfamiliar with the change. | Five consenting testers. |
| P1 · Fourteen-day return-use pilot | Check whether people return to History or My week after the novelty of the timer wears off. Test optional sharing with willing pairs who use different schedules. | Count week-two returners among participants whose full 14 days elapsed. Report missing responses separately. Ask each returning person what brought them back and what they did. | Consenting participants and elapsed time. |
| P1 · Paid value and price | Show the sample and intended local offer after someone has used the free workflow. Ask for a decision the review would help them make. Revisit their personal review after four completed weeks where available. | Record paid, free or undecided preference, price shown and reason. Track actual purchases and renewals separately once a real offer is available. | Localized offer and participating users. |
| P1 · Billing and store readiness | On Play internal testing, verify purchase, checkout dismissal, renewal, cancellation, expiry, reinstall, restore and restore with no entitlement. Review store disclosures, safety guidance and language. | Entitlement follows the Play purchase throughout its term. Restore works on reinstall, expiry returns the app to free, and no purchase flow interrupts the timer. Physical reminders work on Pixel and Samsung with normal battery settings. | Configured Play and billing accounts, devices and reviewers. |
| P2 · Expand the paid product | Finish one book lesson in Robert’s voice and test it with readers. Consider reusable weekly templates if participants repeatedly rebuild the same schedule. | A reader finishes the lesson and describes its practical use, or a tester demonstrates a recurring planning task a template would save. Build the feature supported by that evidence first. | Editorial review and actual pilot observations. |

The [facilitator guide and blank pilot scorecards](pilot-session.md) are ready for the five-person round and follow-up.

## Visual design and controls

Start with Settings and the daily logging flow. Compare matched Android screenshots before and after each change using the same data, device width, locale and appearance. The [screen audit and reference studies](design-direction.md) record the findings for Timer, History, Log and Settings.

| Priority | Design work | How to judge the result |
| --- | --- | --- |
| First | Organize Settings by what someone wants to do. Put the current routine and frequently changed preferences within easy reach. Move detailed alarm options into their own context. | A first-time tester can change Tuesday, choose dark mode, and locate the next reminder without having to inspect each switch. |
| First | Give primary actions, navigation rows, switches and choice lists a consistent visual treatment. Expose the current value wherever it helps predict what will happen on tap. | A tester can tell whether tapping changes a setting immediately, opens choices or navigates elsewhere. Checked, disabled and pressed states are legible in both appearances. |
| Next | Reduce repeated actions and equally weighted groups on the daily tabs. Preserve direct access to starting a fast, correcting a record and logging a portion. | In screenshots and observed use, the immediate task is easy to locate. A single tap logs exactly one entry. |
| Verify | Exercise long translated labels, 320dp screens, doubled text, back navigation and screen-reader semantics. Compare the release candidate with the baseline. | Primary actions remain reachable, values do not overlap, and a screen reader can identify each control and its state. Run a physical TalkBack session before wider release. |

## The usability session

Use fictional schedules and records. Let the participant act before giving a hint.

1. **First fast.** “If you start this window now, when will it finish?” Ask them to choose another window, then enter a start before midnight. Watch whether they can predict the new finish.
2. **A late dinner.** “Tuesday changed from 8 pm to 9:30 pm. Change only Tuesday. What happens Wednesday?” Have them reopen Tuesday and restore the original time.
3. **A missed record.** “Yesterday’s fast ended at the wrong time. Correct it and tell me how many hours you logged this month.” Include two same-duration records to reveal wrong-date selections.
4. **An everyday log.** “Add a glass of water, switch the app to dark mode, and return to your timer.” Repeat a core path with larger text or TalkBack.
5. **A reason to pay.** “Whose information is in this example? Which weekday changed more than once? Show me the dates. Would you change anything next week?” Show the local price after the task and record the person’s choice in their own words.

## How pilot results change the backlog

| Observation | Next action |
| --- | --- |
| People struggle to start, correct a date or trust a reminder. | Fix that path and rerun the affected task before adding features or recruiting more testers. |
| People use the planner but cannot find the four-week review. | Test a clearly labeled entry beside the History summary, then see whether testers can find it unaided. |
| People open the review but cannot name a decision it helps with. | Observe what they do next in the planner. Prototype one action tied to that decision and retest it before expanding the subscription. |
| People repeatedly move the same weekday and want a permanent change. | Prototype editing that weekday’s recurring time with a preview of affected dates and an undo. Preserve one-day edits and the target of a fast already running. |
| People say they would pay but do not purchase or return. | Compare the stated reason with actual behavior, offer terms and checkout results. Investigate the first point where interest drops. |

## What informed these choices

- Easy Fast markets a free timer with paid trends. Zero includes journaling and statistics. A timer alone offers little differentiation. [Easy Fast](https://easyfastapp.com/) · [Zero on Google Play](https://play.google.com/store/apps/details?id=com.zerofasting.zero)
- BodyFast already supports moving an individual fasting period. Oura lets members open reports and inspect the underlying date range. Those examples informed the test of a repeated weekday with dated evidence. They do not establish demand for Shift+. [BodyFast scheduling](https://help.bodyfast.app/hc/en-001/articles/34652209248914-How-do-I-adjust-my-fasting-plan) · [Oura reports](https://support.ouraring.com/hc/en-us/articles/360046061373-Oura-Reports)
- Mobbin’s onboarding study informed setup and progress cues. Purrweb’s Dribbble concept distinguishes the live timer from the journal. The Android app keeps its own Nichols identity and native interactions. [Mobbin](https://mobbin.com/blog/the-long-onboarding-a-study-of-1-460-flows) · [Dribbble](https://dribbble.com/shots/22392705-Intermittent-Fasting-Tracker-Mobile-iOS-App)

## Candidate evidence

The Settings redesign puts the next planned start and the coming week above grouped preferences. Reminders shows the common alerts first and counts the enabled options inside collapsed groups. Timer now places the 250 ml action beside the daily total. Log keeps the latest entry visible and opens older entries on demand. The Android build passed 404 executed unit tests, including a check that Privacy stays in the same position while the weekly schedule loads. [Settings and daily controls](https://github.com/nicholsmindset/fasting/pull/17) · [Android verification](https://github.com/nicholsmindset/fasting/actions/runs/45518596771)

History places **View records by date** beside the recent chart. The full list shows the start date and time first. The pilot task is to find the earlier of two 16-hour fasts, correct its note, and return to the chart with the other record intact. Also test the first-time empty list, an active fast, and imported records. Physical-device TalkBack and participants’ paid-review decisions remain open.

The 25 November candidate was merged in [PR #15](https://github.com/nicholsmindset/fasting/pull/15). The [Android build](https://github.com/nicholsmindset/fasting/actions/runs/45505089270) passed 396 executed unit tests with 22 skipped. Lint reported 0 errors and 47 warnings. The [signed Android 15 upgrade](https://github.com/nicholsmindset/fasting/actions/runs/45505089057) preserved the original timer target, History entry, moved start, skipped date and weekly reflection after relaunch. Six web invitation tests also passed. The first reminder check saved the default routine time during setup. On the same build, the retry observed no notification at the replaced time, recorded delivery one second after the moved time on a PIN-locked emulator, and confirmed the following day rearmed at its usual time. Physical-device delivery is still open.

[Staging APK](https://github.com/nicholsmindset/fasting/actions/runs/45505089270/artifacts/14973765956) · [Screen captures](https://github.com/nicholsmindset/fasting/actions/runs/45505089270/artifacts/14972612214) · [Locked-screen reminder check](https://github.com/nicholsmindset/fasting/actions/runs/45505089039) · [Detailed beta worklist](beta-worklist.md) · [Play setup](play-internal-testing.md)
