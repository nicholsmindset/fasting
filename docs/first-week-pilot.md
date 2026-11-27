# First-week pilot

Use a clean test install and fictional data. Record the build, Android version, language, theme, font size and TalkBack setting. Invite five consenting adults for the usability round. Begin with an unopened app. Read the prompts without naming the controls. Record the first attempt as unaided, assisted, incomplete or skipped. Keep individual observations in a private research workspace.

| Task | Read aloud | Observe |
| --- | --- | --- |
| Explore | “You want to look around before deciding whether to fast.” | They reach the preview, know it is an example, and can return. No timer, schedule, invite or reminder starts. |
| Plan later | “You’ll finish dinner late. Plan a start for tonight. Tell me when it would finish tomorrow. You want to decide about reminders another day.” | They read both local times and save. Close and reopen the app. Ask them to find their saved plan and change its start. |
| Stop early | Give them an eligible fictional profile with a running timer. “Your day changed and you want to stop now. What would you do? Where can you see what was saved?” | They can find End fast and identify the saved record. They can find correction or undo if they made a mistake. Ending early counts as success. |
| Support someone | Give a second test installation an unused invitation. “You want to encourage this person without fasting yourself. Before you join, what will they see? What stays private?” | They identify the supporter option, shared check-ins and encouragement. They distinguish this information from private fasting times, health entries and step counts. They can find leave. |
| Find a setting | From Settings, ask them to change a reminder, find Health Connect, then locate the history export. | Record the first wrong turn and number of navigation choices for each destination. Dismiss the system picker without saving personal data. |
| Understand the paid offer | “Find something you can try without paying. What do you think buying would unlock today?” | They identify what is available today and what is still in preparation. If a store offer is available, ask when and how much it charges. End before checkout. |

Run fictional safety-blocked and clinician-acknowledgement scenarios separately in QA. Do not ask participants to disclose their own screening answers or to undertake a fast for this pilot.

## First-open routes

| Choice | What the user sees next | What is saved |
| --- | --- | --- |
| Start now | Goal and screening, eligible protocol, then a dated preview and an explicit Start fast action. A blocked screening has no start action. | After the user starts, one active fast. |
| Plan for later | Goal and screening, eligible protocol, editable start and finish, then the reminder choice where supported. | A draft during setup and the dated plan after saving. Declining notifications keeps the plan. |
| Just exploring | A labeled example and a route back to setup. | Viewing the example creates no fast, reminder, or completed screening. |

## Pilot decisions

Proceed to a wider usability round when at least four of five participants can save and recover the same plan unaided and all five can find End fast and identify what a partner can see. Report the count of non-attempts next to each result. A safety bypass, a private record exposed to a partner or an undisclosed charge blocks release regardless of the other results. Keep compact-screen, 200% font-size and TalkBack repeats separate from first attempts.

## First-week follow-up

For participants who opt in, use anonymous IDs and relative days. Capture only yes, no or unknown for first open, plan saved or fast explicitly started within 24 hours, and a return on days 2–7. Record counts of valid invitations created and redeemed on test accounts. Exclude names, invitation codes, screening results, measurements, precise fasting times and personal journal entries. No additional analytics SDK is needed.

- Activation: unique eligible first opens with a saved plan or explicit start within 24 hours divided by eligible first opens observed for 24 hours.
- Return: activated participants with a return on days 2–7 divided by activated participants observed through day seven.
- Partner uptake: redeemed invitations divided by valid invitations created in the same pilot window. Close the observation window after the remaining invitations expire.
- Report every denominator, raw count and unknown outcome. Record exploration, early stopping and skipped reminders as valid choices. Treat reported willingness to pay separately from an actual purchase. A five-person task test cannot establish retention or revenue.

The [existing pilot protocol](pilot-session.md) covers history correction, the sample weekly report and later follow-up. The [partner pilot](partner-week-pilot.md) covers renewal and shared-week outcomes with a larger cohort.
