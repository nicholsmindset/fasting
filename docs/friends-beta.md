# Fasting with a friend

Staging and internal beta generate a link to the [beta invite page](https://nicholsmindset.github.io/fasting/c/). The part after `#` holds the challenge settings. The browser reads it locally. Joining requires confirmation in Fasting Shift. The sender sees no member list, fasting records or health readings.

## Try it on two Android phones

1. On the first phone, open **History → Challenges**, create a challenge and tap **Invite someone**. Send the link through your usual messaging app.
2. On the second phone, open the link before installing the app. Check the name, dates and schedule. Install the staging APK, then return to the message. Choose **Fasting Shift (staging)** on the invite page and tap **Open in Fasting Shift**. Finish the usual screening. If your browser keeps the link open on the web, copy the code and paste it under **History → Challenges → Have an invite link?**
3. Try different days on the two phones. On one, turn on **Follow my own plan** and tap **Join**. Open **My week** from the challenge and pick your days. On the other, keep the invitation’s routine. A fast you end on your own plan counts once on the planned date it started, including one you ended early. A skipped date or a week off has no target.
4. Reopen the invitation and tap **Open challenge**. Your choice and progress should survive relaunch. Try **Send encouragement**. The messaging preview should contain only “Cheering you on this week!” You decide whether to send it. A progress card is a separate action.
5. On **History**, find “Did this schedule fit your week?” and try **Skip this week**. Use **Add a reflection** to answer later or **Change answer** to revise it. Check the week’s date range and confirm that changing the answer leaves next week’s plan alone.

On each phone, try **Settings → Appearance**, relaunch the app and check that the selected appearance is still in use. Use sample records to correct yesterday’s fast, move one planned start and find its next reminder. Lock the phone before that reminder is due and record whether it arrived.

Ask each tester what they expected the other person to see and where they got stuck. Record the step, phone model, Android version and app build. Avoid recording personal fasting times, health readings or real invite codes. After two weeks, count how many participants with 14 days of follow-up returned during days 8–14 to review History or adjust a plan. Ask each pair whether they would do another week together and why.

## Maintainer notes

The web source is in `web/c/`. The public GitHub Pages branch is `gh-pages`, with the same files under `c/`. Run `node --test tests/invite.test.mjs` before publishing. After the reviewed files are on `gh-pages`, verify a synthetic invitation on the live site. The **Challenge invite page** workflow can check the deployed site when run manually.

The public release build still generates branded links. Before a Play rollout, connect the branded site's invite route and publish Android's association file using the Play signing certificate. Keep the original branded links and pasted codes working for existing invitations. The signed-in GitHub staging download on the invite page expires and should be updated when the next tester build is distributed.
