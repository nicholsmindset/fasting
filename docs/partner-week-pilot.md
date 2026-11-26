# Partner Week pilot

**Question.** When two people who already know each other finish a free Partner Week, do they voluntarily choose another one and use it together? Recruit up to 20 known pairs of adults. Give each accepted pair 14 full days from acceptance. Include people who want to support someone without fasting. No one needs to start a fast, change a meal, earn three marks or buy anything.

## Before enrollment

Run the two-device invitation, offline retry, replay, leave and third-device access checks on the candidate. Check notification opt-out, TalkBack, large text and that ending a fast preserves marks. Record the build once for the round. Assign a facilitator and a safety contact who can pause enrollment.

Ask **each person privately** before sharing a pilot link. Read this aloud:

> You can check in on a rest day, cheer someone on, skip a day or leave at any time. Please eat when you planned to. Your partner can see your chosen name, role, check-in days, preset encouragement and choice about another week. Your timer, fasting and health logs, and reasons for checking in stay private. Leaving online removes the shared week for both people. Would you like to try this with someone you know?
>
> Separately, may we contact you privately at the end and combine your answers about using the feature in a group tally? Either choice is fine. You can skip questions and withdraw your answers before we combine them.

Use one random pilot code per pair. Only create a scorecard row when **both** opt in to pooling. Others can use the feature independently. Keep contact arrangements separately from the completed scorecard. Share aggregate counts only. Never collect the app’s invite codes, member IDs, aliases, screenshots, logs, fasting hours or medical information. The scorecard relies on voluntary self-report. No analytics SDK or backend export is needed.

## Run the two weeks

1. **Invitation.** Let the host send the invite privately. Count one invitation per pair, even if a link is retried. Mark accepted when the guest joins and the shared week starts. A link preview or install alone is pending. Close the invitation window seven days after the first send. Record a decline, confirmed expiry or unknown outcome. Keep enrolled pairs who never send a link separate.
2. **First 48 hours.** Each person may privately note when the week started and whether they checked in within the following 48 elapsed hours, including exactly 48 hours. They keep the times. For two participants, activation requires at least one confirmed shared mark from each within that window. A rest-day mark counts. A retry, preset cheer or personal timer does not count as a check-in. If an offline attempt never appeared as a mark, or the timing is uncertain, record the uncertainty.
3. **End of week one.** Let the app offer its normal recap. Give no reminder or incentive to renew. A mutual start means both chose another week and saw a new shared week start. One person’s renewal request, opening the recap or saying they would return is insufficient.
4. **After 14 full days.** Ask each person separately about their own actions, including whether they chose another week without a facilitator prompt and used it before the observation window closed. For two participants, week-two use requires at least one shared mark from each during the renewed week. For a supporter pair, track the host’s check-in and whether the supporter voluntarily revisited the shared screen or sent a preset cheer. A cheer is always optional. Record the two roles separately even if the supporter does nothing.
5. **Close out.** Ask “What could the other person see?” and “Was there a day when you wanted to show up differently?” Record recurring interface issues in aggregate. No need to ask what someone ate or why they skipped a day.

## Ask about pressure privately

At enrollment offer a way to reach the safety contact independently at any time. At the day-14 follow-up or an earlier exit, ask **each person, away from their partner**:

> During either week, did you ever delay eating when you otherwise would have eaten because you wanted to protect a mark, finish the week or avoid disappointing your partner? You can say yes, no, unsure, or prefer not to answer.

If someone says yes, thank them, offer to stop their participation and ask permission to discuss which part of the feature contributed. A facilitator must **pause recruitment and expansion**, notify the designated safety contact privately and investigate the design before resuming. Keep the person's answer away from their partner and out of the pair scorecard. Use a separate restricted record only with permission, collect the minimum necessary and give people the option to report without identifying themselves. Track counts of answers and nonresponses separately. Never turn silence into a “no.” For an unsure answer, offer the same private follow-up. Document the design decision and retest any correction before expansion.

## Score and decide

Use a private copy of the [blank pair scorecard](partner-week-pilot.csv). Enter `yes`, `no` or `unknown` for activity. If either person withdraws research consent before aggregation, remove that row and disclose aggregate withdrawals only where consent allows. Delete the scheduling/contact list and working rows after the round is reviewed. Publish only pooled totals and avoid identifiable small-group details.

| Field | What to enter |
| --- | --- |
| `pair_code` | One unrelated code, P01 to P20. Keep the roster outside the repository. |
| `invite` | `unsent`, `pending`, `accepted`, `declined`, `expired` or `unknown`. Only accepted pairs fill the remaining fields. |
| `mode` | `participants` or `supporter`, chosen at acceptance. Unknown invitation outcomes have no confirmed role. |
| `first48_host`, `first48_guest` | Each person's qualifying action defined above. For a supporter, guest activity is an optional return to the shared screen or a cheer after acceptance. |
| `day14` | `pending` until 14 elapsed days after acceptance, then `due` even if follow-up gets no response. |
| `mutual_start`, `week2_host`, `week2_guest` | Leave blank until due. Enter confirmed voluntary mutual renewal and each person's qualifying week-two use. If renewal did not happen, all three are `no`. If either person is unsure or silent, record `unknown` for unconfirmed actions. |

From the repository root run `python3 scripts/score-partner-pilot.py /path/to/private-copy.csv`. The script reads the local file and prints counts. It does not contact a server.

Report accepted / unique sent invitations. For two-participant pairs report dual activation / accepted, plus unknown activation. The **primary return rate** is activated, due two-participant pairs with verified mutual start **and** verified week-two use by both, divided by **all** activated, due two-participant pairs. Missing follow-up and confirmed dropouts stay in that denominator while consent stands. Report pending pairs and unknowns explicitly. Show supporter-mode results separately using the host’s first check-in for activation. The supporter’s participation never gates the host’s in-app progress.

After all enrollment outcomes close and all accepted pairs have had 14 days, consider expansion if **at least half** of activated two-participant pairs voluntarily started and used week two and the delayed-eating review is complete. With zero activated pairs the rate is undefined. Report the fraction and missing data alongside the decision. If uncertain activation or missing safety responses could change the interpretation, continue learning before expanding. A qualifying supporter rate cannot offset a low primary rate. Twenty pairs can guide a design choice, not establish population demand.
