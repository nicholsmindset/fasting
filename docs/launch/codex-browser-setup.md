# Fasting Shift — Codex browser setup runbook

Give these prompts to Codex (or any agent that drives your logged-in browser) **one phase at a
time, in order**. Each phase depends on the ones before it. Paste the **Ground rules** block at
the start of every session.

You stay in charge of: payments, legal declarations, anything published to the public, and every
password, key and card number. Codex prepares; you confirm and click the final button.

---

## Ground rules (paste first, every session)

```text
You are setting up launch accounts for an Android app called Fasting Shift, using my browser,
where I am already signed in. The app's code is in the GitHub repo nicholsmindset/fasting
(branch claude/friendly-cray-p3fgxg). Read files from that repo when a step says so.

Rules you must follow:
1. STOP and ask me before: paying or entering payment details, accepting any legal agreement or
   policy declaration, publishing anything to production or to the public, sending an app for
   review, deleting anything, or any step you can't undo.
2. Never type, paste, read aloud or save passwords, API keys, private keys, service-account JSON,
   keystore files or card numbers into chat, files, commits or notes. When a secret is needed,
   tell me where to paste it and wait.
3. Never commit secrets to the repo.
4. Use only facts from the repo files I name. If a form asks for something you can't confirm,
   leave it blank and add it to a "Needs Robert" list.
5. After each step, take a screenshot and append one line to docs/launch/setup-log.md:
   date, service, step, result, anything left for me. (Commit only that log file.)
6. Health-app wording: wellness only. No disease claims, no "lose X kg", no before/after,
   no medical advice. Copy listing text exactly from docs/store-listing.md.
7. If a page looks different from these instructions, describe what you see and ask me.
```

Facts Codex will need (all from the repo):

| Item | Value | Source |
| --- | --- | --- |
| Package name | `com.fastingshift.app` | `app/build.gradle.kts` |
| App name | Fasting Shift | `docs/store-listing.md` |
| Privacy policy URL | `https://fastingshift.com/privacy` | `app/src/main/res/values/strings_features.xml` |
| Challenge links | `https://fastingshift.com/c/<code>` | `domain/challenge/Challenges.kt` |
| Subscription entitlement | `shift_plus` | `app/.../billing/BillingManager.kt` |
| Product ids (must contain `monthly` / `annual`) | `shift_plus_monthly`, `shift_plus_annual` | `app/.../billing/PlanMapper.kt` |
| Listing, data safety, Health Connect text | `docs/store-listing.md`, `docs/data-safety.md`, `docs/health-connect-justifications.md` | |
| Screenshots | `docs/screenshots/*.png` (1233×2466) | |
| Languages | English (`en`), Malay (`ms`), Indonesian (`id`) | `res/xml/locales_config.xml` |

**Supabase is not needed.** The app is offline-first and has no server; don't create a project.

---

## Phase 1 — Website on Vercel (fastingshift.com)

Play needs the privacy policy live before review, and the app opens challenge links on
`fastingshift.com/c/` (see `AndroidManifest.xml`). The reviewed invite page already exists in
`web/c/` and is published to GitHub Pages (`gh-pages`, see `docs/friends-beta.md`). This phase
serves the same `web/` folder at fastingshift.com and adds the home and privacy pages.

```text
Phase 1: Vercel website for fastingshift.com, from the repo's web/ folder.

1. Read docs/friends-beta.md. Do NOT change anything in web/c/ (it is reviewed and has tests in
   tests/invite.test.mjs). Run `node --test tests/invite.test.mjs` and stop if it fails.
2. Add, as plain HTML + CSS (no framework, no analytics, cookies or trackers), reusing the look of
   web/c/challenge.css:
   - web/index.html: a one-page landing using the title, short description and the
     free/safety/privacy points from docs/store-listing.md, a link to /privacy and a Google Play
     link placeholder (https://play.google.com/store/apps/details?id=com.fastingshift.app).
   - web/privacy/index.html is prepared for the ad-supported release. Review the policy and
     support address before deployment. Serve web/app-ads.txt at the root of the developer
     website listed on Play. If a file already exists there, merge the Google publisher line.
   - web/.well-known/assetlinks.json containing [] for now (Phase 2 fills it).
   Commit on branch claude/friendly-cray-p3fgxg and push.
3. In Vercel: create a project from the GitHub repo with root directory `web`, framework
   "Other", no build command, no environment variables. Deploy and show me the preview URL.
   Check /, /privacy, /app-ads.txt and /c/ on the preview.
4. Ask me before adding the domain. When I confirm, add fastingshift.com and
   www.fastingshift.com and show me the DNS records Vercel asks for; I set them at my registrar.
5. Once DNS works, check https://fastingshift.com/privacy and https://fastingshift.com/c/ load.
   Log the result. Keep GitHub Pages running until I say otherwise (beta builds link there).
```

---

## Phase 2 — Google Play Console

You need a built, signed release bundle first. On your machine, from the repo:

```bash
FS_VERSION_CODE=1 ./gradlew :app:bundleRelease
```

The upload key comes from `FS_UPLOAD_*` environment variables or `fastingshift.upload.*` Gradle
properties (see README). Keep the keystore file and passwords out of the repo and out of chat.

```text
Phase 2: Google Play Console for com.fastingshift.app.

1. Create the app: name "Fasting Shift", default language English (US), App (not game), Free.
   STOP before accepting the declarations — show them to me.
2. Play App Signing: choose to let Google manage the app signing key. Then go to
   Setup → App signing and copy the SHA-256 fingerprint of the APP SIGNING key (it's public,
   not a secret). Put it in web/.well-known/assetlinks.json as an Android app link entry for
   package com.fastingshift.app with relation "delegate_permission/common.handle_all_urls",
   commit, push, and confirm Vercel redeployed.
3. Internal testing (see docs/play-internal-testing.md): register the upload certificate,
   create the tester list I give you and copy its opt-in link. For the FIRST release, tell me
   when to upload app-release.aab (I'll pick the file); later releases go through the GitHub
   workflow. Don't roll out until I confirm.
4. Main store listing: copy title, short description and full description exactly from
   docs/store-listing.md. Use the 512px Nichols Mindset N from the final brand kit for the store icon. Ask Robert for the feature graphic.
   Phone screenshots (max 8): upload docs/screenshots/1-timer-light.png, 1b-timer-left-light.png,
   2-history-light.png, 2b-calendar-light.png, 3-log-light.png, 5-muslim-hub.png,
   6-challenge.png, 7-trust-center.png. Category: Health & Fitness. Contact email: ask me.
   Privacy policy: https://fastingshift.com/privacy.
   Add translations for ms and id only if I give you reviewed text; otherwise skip.
5. App content (Policy → App content). Fill from the repo; STOP before submitting each one:
   - Privacy policy: https://fastingshift.com/privacy
   - Ads: select “Yes, my app contains ads” for the release that includes the free-tier banner.
   - App access: all functionality available without special access.
   - Content rating questionnaire: Health/fitness reference app, no violence, no user-generated
     content shared with others, no gambling. Show me the answers before submitting.
   - Target audience: 18 and over only.
   - Data safety: fill exactly from docs/data-safety.md. Show me the summary before saving.
   - Health apps declaration: health & fitness, not a medical device; use docs/PRD.md wording.
   - Health Connect: declare exactly the permissions and justifications in
     docs/health-connect-justifications.md. Nothing more.
   - Government / financial / news: No.
6. Monetize → Subscriptions: create `shift_plus_monthly` and `shift_plus_annual`, each with one
   base plan and a free-trial offer for new customers. Ask me for prices, trial length and
   countries — don't guess. Keep them inactive until I confirm.
7. Setup → License testing: add the tester emails I give you.
8. List everything still needed from me (icon, feature graphic, contact email, prices,
   declarations to confirm, production access requirements such as the closed-testing period
   for new personal accounts).
```

---

## Phase 3 — RevenueCat (Shift+ subscriptions)

```text
Phase 3: RevenueCat for Fasting Shift.

1. Create a project "Fasting Shift" and add a Google Play app with package com.fastingshift.app.
2. Service account: follow RevenueCat's Google Play service-credentials guide in Google Cloud
   and Play Console (create service account, grant Play Console financial and order access).
   When a JSON key is created, STOP: I will download it and upload it to RevenueCat myself.
   Never open, read or copy its contents.
3. Products: import shift_plus_monthly and shift_plus_annual.
4. Entitlement: create identifier `shift_plus` (exactly this) and attach both products.
5. Offering: create `default`, make it current, with packages $rc_monthly → shift_plus_monthly and
   $rc_annual → shift_plus_annual.
6. API keys: tell me where the Google Play PUBLIC SDK key is shown. Don't copy it anywhere.
   I'll add it as the play-internal variable REVENUECAT_ANDROID_PUBLIC_KEY (Phase 4) and as
   `revenuecat.apiKey` in my ~/.gradle/gradle.properties for local builds.
```

---

## Phase 4 — GitHub

The Play internal-testing workflow and its settings are described in
`docs/play-internal-testing.md`; follow that file, not this summary, if they differ.

```text
Phase 4: GitHub repo nicholsmindset/fasting.

1. Read docs/play-internal-testing.md and .github/workflows/*.yml and list every secret and
   variable they use. Today that is:
   - Repository secrets for "Internal beta APK": BETA_KEYSTORE_BASE64, BETA_KEY_ALIAS,
     BETA_STORE_PASSWORD, BETA_KEY_PASSWORD.
   - Environment "play-internal" (deployments only from the default branch): secrets
     PLAY_UPLOAD_KEYSTORE_BASE64, FS_UPLOAD_STORE_PASSWORD, FS_UPLOAD_KEY_ALIAS,
     FS_UPLOAD_KEY_PASSWORD; variables PLAY_UPLOAD_CERT_SHA256, PLAY_WORKLOAD_IDENTITY_PROVIDER,
     PLAY_SERVICE_ACCOUNT, and optional REVENUECAT_ANDROID_PUBLIC_KEY.
2. Report which exist (names only). For each missing one, open the right form and let me paste
   the value. Never type secret values yourself. Variables that are public (the upload
   certificate SHA-256 from Play Console, the RevenueCat PUBLIC key) you may fill after I confirm.
3. Google Cloud (for the Play workflow): follow steps 2–3 of docs/play-internal-testing.md —
   enable the Google Play Android Developer API, create a dedicated service account, set up
   Workload Identity Federation restricted to this repo, its default branch and the
   play-internal environment. Show me each IAM change before saving. No JSON keys.
4. Actions: confirm the latest "Android CI" run passed. Don't start the Play or beta workflows
   unless I ask. Don't merge pull requests or change branch protection.
```

---

## Phase 5 — Google Ads (app install campaign)

Only after the app is live on at least a testing track with an approved listing.

```text
Phase 5: Google Ads for Fasting Shift. Everything stays PAUSED until I approve.

1. Link accounts: in Google Ads, Tools → Data manager / Linked accounts → Google Play: link the
   Play developer account for com.fastingshift.app. In Play Console accept the link request
   only after I confirm.
2. Conversion: use the Google Play "first open"/install conversion that the link provides. No
   Firebase or SDK changes to the app.
3. Create an App campaign (Android, objective: app installs), name "FS – Installs – SEA",
   status PAUSED:
   - Locations: Singapore, Malaysia, Indonesia (ask me before adding others).
   - Languages: English, Malay, Indonesian.
   - Budget and bid: leave empty and ask me.
   - Headlines (≤30 chars) and descriptions (≤90), wellness only, from docs/store-listing.md and
     docs/moats.md, e.g. "Free fasting timer & widgets", "Sunnah & qada fast tracker",
     "No account required", "Safety check-ins built in", "Ramadan suhoor & iftar times".
     No weight-loss promises, no medical or disease claims, no before/after, no "detox".
   - Assets: use the docs/screenshots images; ask me for a logo and any video.
4. Before saving, show me the full campaign summary and a policy self-check against Google Ads
   healthcare and medicines rules. Leave it PAUSED.
```

---

## Phase 6 — Final checklist

```text
Phase 6: Read docs/launch/setup-log.md and write a final checklist at its end:
done / waiting on Robert / blocked (with the reason) for every step in phases 1–5. Then list the
README "Before launch" items that remain (device tests, clinician and legal review,
native-speaker review of translations).
```

---

## What stays with you

- All payments (Play developer fee, Ads budget, domain).
- Accepting agreements and declarations in Play Console and Google Ads.
- Keystore, upload key, RevenueCat service-account JSON and SDK key: you handle the files and
  paste values yourself.
- Production release and Ads activation.
- Prices, trial length and countries for Shift+.
