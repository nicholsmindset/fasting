# Fasting with a friend

Staging and internal beta generate a link to the [beta invite page](https://nicholsmindset.github.io/fasting/c/). The part after `#` holds the challenge settings. The browser reads it locally. Joining requires confirmation in Fasting Shift. The sender sees no member list, fasting records or health readings.

## Try it on two Android phones

1. On the first phone, open **History → Challenges**, create a challenge and tap **Invite someone**. Send the link through your usual messaging app.
2. On the second phone, open the link before installing the app. Check the name, dates and schedule. Copy the code, install the staging APK and complete onboarding. Return to the message, choose **Fasting Shift (staging)** and tap **Open in Fasting Shift**. You can also paste the code under **History → Challenges → Have an invite link?**
3. Check that the intended plan appears and requires you to tap **Join**. Joining should keep both people’s progress private. Send a progress card only if you want to.
4. Open the same invitation a second time. Check that your existing challenge and history are intact. Test declining an invitation as well as joining.

Ask each tester what they expected the other person to see and where they got stuck. Record the step, phone model, Android version and app build. Avoid recording personal fasting times, health readings or real invite codes. At the end of the week, ask whether they would choose to do another week together and why.

## Maintainer notes

The web source is in `web/c/`. The public GitHub Pages branch is `gh-pages`, with the same files under `c/`. Run `node --test tests/invite.test.mjs` before publishing. After the reviewed files are on `gh-pages`, verify a synthetic invitation on the live site. The **Challenge invite page** workflow can check the deployed site when run manually.

The public release build still generates branded links. Before a Play rollout, connect the branded site's invite route and publish Android's association file using the Play signing certificate. Keep the original branded links and pasted codes working for existing invitations. The signed-in GitHub staging download on the invite page expires and should be updated when the next tester build is distributed.
