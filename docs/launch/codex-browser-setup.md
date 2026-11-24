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
| Languages | English, Malay (`ms`), Indonesian (`in`), Simplified Chinese (`zh-CN`) | `res/xml/locales_config.xml` |

**Supabase is not needed.** The app is offline-first and has no server; don't create a project.

---

## Phase 1 — Website on Vercel (fastingshift.com)

Play needs the privacy policy live before review, and challenge links need a page to land on.

```text
Phase 1: Vercel website for fastingshift.com.

1. In the repo, create a small static site in a new folder `site/` (plain HTML + CSS, no
   framework, no analytics, no cookies, no trackers):
   - `site/index.html`: one-page landing for Fasting Shift using the title, short description and
     the free/safety/privacy points from docs/store-listing.md. Include a "Get it on Google
     Play" link placeholder (https://play.google.com/store/apps/details?id=com.fastingshift.app)
     and a link to /privacy.
   - `site/privacy/index.html`: docs/privacy-policy.md converted to HTML, word for word.
     Keep the "[monitored privacy contact]" placeholder visible and add it to "Needs Robert".
   - `site/c/index.html` plus a `site/vercel.json` rewrite so /c/<anything> serves it: "You've been
     invited to a Fasting Shift challenge. Open this link on an Android phone with Fasting Shift
     installed, or get the app on Google Play." Do not parse or display the code.
   - `site/.well-known/assetlinks.json`: leave as an empty array `[]` for now (Phase 2 fills it).
   Commit with a clear message on branch claude/friendly-cray-p3fgxg and push.
2. In Vercel: create a project from the GitHub repo, root directory `site`, framework "Other",
   no build command. Deploy. Show me the preview URL.
3. Ask me before adding the domain. When I confirm, add fastingshift.com and www.fastingshift.com
   to the project and show me the DNS records Vercel asks for. I will set them at my registrar.
4. Check that https://fastingshift.com/privacy loads (it may take time for DNS). Log the result.
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
   not a secret). Put it in site/.well-known/assetlinks.json as an Android app link entry for
   package com.fastingshift.app with relation "delegate_permission/common.handle_all_urls",
   commit, push, and confirm Vercel redeployed.
3. Internal testing: create a release and tell me when to upload app-release.aab (I'll pick the
   file). Release name "0.1.0 (1)". Notes: "First internal test build." Add the testers email
   list I give you. Don't roll out until I confirm.
4. Main store listing: copy title, short description and full description exactly from
   docs/store-listing.md. App icon and feature graphic: add to "Needs Robert" (placeholder icon).
   Phone screenshots (max 8): upload docs/screenshots/1-timer-light.png, 1b-timer-left-light.png,
   2-history-light.png, 2b-calendar-light.png, 3-log-light.png, 5-muslim-hub.png,
   6-challenge.png, 7-trust-center.png. Category: Health & Fitness. Contact email: ask me.
   Privacy policy: https://fastingshift.com/privacy.
   Add translations for ms, id, zh-CN only if I give you reviewed text; otherwise skip.
5. App content (Policy → App content). Fill from the repo; STOP before submitting each one:
   - Privacy policy: https://fastingshift.com/privacy
   - Ads: No ads.
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
   I'll add it as `revenuecat.apiKey` in my ~/.gradle/gradle.properties before building the
   release bundle.
```

---

## Phase 4 — GitHub

```text
Phase 4: GitHub repo nicholsmindset/fasting.

1. Settings → Secrets and variables → Actions: list which of these exist (names only). Today
   only .github/workflows/internal-beta.yml uses secrets: BETA_KEYSTORE_BASE64, BETA_KEY_ALIAS,
   BETA_STORE_PASSWORD, BETA_KEY_PASSWORD. Re-check the workflow files for the exact names
   before reporting. For each missing one, open the "New secret" form and let me paste the
   value. Never type secret values yourself.
   The Play upload key (FS_UPLOAD_STORE_FILE, FS_UPLOAD_STORE_PASSWORD, FS_UPLOAD_KEY_ALIAS,
   FS_UPLOAD_KEY_PASSWORD) and the RevenueCat key (`revenuecat.apiKey`) are only used by local
   builds today; no workflow reads them, so don't create GitHub secrets for them unless I ask
   for a release workflow.
2. Actions: confirm the latest "Android CI" run on claude/friendly-cray-p3fgxg passed, and that
   the "Internal beta APK" workflow can be started manually. Don't start it unless I ask.
3. Do not merge pull requests or change branch protection unless I ask.
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
   - Languages: English, Malay, Indonesian, Chinese (simplified).
   - Budget and bid: leave empty and ask me.
   - Headlines (≤30 chars) and descriptions (≤90), wellness only, from docs/store-listing.md and
     docs/moats.md, e.g. "Free fasting timer & widgets", "Sunnah & qada fast tracker",
     "Private: no account, no ads", "Safety check-ins built in", "Ramadan suhoor & iftar times".
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
