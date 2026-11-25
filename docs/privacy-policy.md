# Fasting Shift privacy policy (draft)

> Release owner: publish this version with the Android release that adds free-tier ads. The app opens https://fastingshift.com/privacy from Settings and the Health Connect permission screen. Verify both links before release.

Effective upon publication. Fasting Shift is published on Google Play by theonngroup. For privacy or support, email [onnifyworks@gmail.com](mailto:onnifyworks@gmail.com).

## Your records

You can use Fasting Shift without creating an account. The app keeps your health and fasting records on your device for the features you choose.

| Records | How the app uses them |
| --- | --- |
| Fast times, goals, protocols, fast types, notes and mood reflections | Timer, History, consistency summaries and insights |
| Screening answers, including age, pregnancy or breastfeeding, eating-disorder history, diabetes, medications, height and weight | Decide which fasting options to offer and show safety guidance |
| Symptoms and optional weight, water, electrolyte, glucose or ketone entries | Personal logs, check-ins and trends |
| Optional Ramadan, Sunnah and qada records | Display fasting times and reminders, and track days owed by Hijri year and the dates you mark as made up |
| Selected city and prayer method, schedules, weekly plan choices, guided-program progress and challenge records | Personalize the calendar, reminders and planning features |

The health and fasting database is encrypted. Android Keystore protects the key used to unlock it. The app keeps preferences, such as the city you select and reminder choices, in private app storage. It uses an offline list of cities and bundled prayer times or local calculations. Choosing a city does not request your device location or send your selections to a prayer-time provider.

Fasting Shift never sells your health or religious-observance records. We do not use them for advertising or share them with advertising or billing services. If you enable reminders or add a widget, information such as fasting times can appear in notifications or on your home screen.

## Optional partner weeks

Ordinary challenge links share plan settings and keep progress on each phone. If you start a partner week, preview an invitation or join one, the app connects to our partner service. You can use the timer and ordinary challenges without it.

The service processes a chosen nickname, participant or supporter role, time zone, an app-generated device credential, invitation details and week dates. When you choose to check in or send a preset reply, your partner can see the check-in day or reply. Sharing never includes fasting times, fasting duration, notes, health measurements, screening or religious records. Partner activity is separate from advertising and billing. Anyone you send an unused invitation to can preview the inviter’s nickname and may claim the invitation.

Leave the partner week while online to remove the shared week for both people. If you leave offline, reconnect and allow the app to sync before uninstalling or clearing its data. Uninstalling alone clears the local copy. An unaccepted invite expires in seven days. A completed week expires 30 days after it ends for both people. Expired invitations and weeks become inaccessible immediately. An hourly cleanup deletes their records, including both partners’ check-ins and encouragement. Starting another week together replaces the previous week.

## Files you choose to save

The app excludes its private data from Android's automatic cloud backup and device transfer. You can save copies yourself through the Android file picker.

- **Export fasting history** writes a CSV of finished fasts, including their start, planned end, actual end, protocol and fast type. A fast type can reveal a religious observance.
- **Back up to a file** writes a full JSON backup. **Export** writes a ZIP containing that JSON and spreadsheet files. The full backup includes available notes, screening answers, health logs, religious records and backed-up settings.
- These files are readable by anyone with access to them. The app does not encrypt them. If you choose a cloud drive, its provider handles the file under its own terms. Choose a destination you trust.
- Importing a history CSV reads the file you select and shows a preview before adding records. Restoring a full backup replaces the app's database records and applies the preferences included in the file.

## Health Connect

To enable sync, open **Settings → Privacy and data → Sync with Health Connect** and grant Android’s Health Connect permissions. When you grant access, the app reads recent weight entries from Health Connect into your weight log. When you log weight or water with sync enabled, it writes the measurement and its time to Health Connect. Weight entries can also appear in your personal trends.

With sync enabled, you can also allow the app to write glucose readings you log, so other apps you choose can see them; deleting a reading in the app removes the copy it wrote. If you open Body signals in Shift+ Insights and allow it, the app reads your sleep sessions and resting heart rate to compare fasting and non-fasting days. These are read while that screen is open and are not stored. Other apps can access Health Connect records according to the permissions you give them.

If a write fails, the measurement stays in the encrypted database. The app saves the local row number and a fingerprint in private preferences so you can retry from the Log screen. These references are excluded from the app's export and backup files. A successful retry or deletion of the local entry clears the corresponding pending reference. Turning sync off stops further attempts. The app does not read glucose from Health Connect, and never writes ketones (Health Connect has no ketone type) or religious records there.

To stop future access, turn off sync and revoke permissions in Health Connect. To remove existing copies, delete the local entry in Fasting Shift and manage the Health Connect entry separately. Other apps you allowed to read that entry may also hold a copy. [Manage Health Connect data](https://support.google.com/android/answer/12201232)

Our use of Health Connect information is limited to the features you enable and follows Google's Health Connect limited-use requirements. [Android health permissions](https://support.google.com/googleplay/android-developer/answer/12991134)

## Ads on the free plan

The free plan can show a Google AdMob banner in Settings. Shift+ is ad-free. Fasting Shift requests non-personalized ads and does not use your fasts, screening answers, selected city, religious practices, or Health Connect records to select or measure ads.

Google can use your IP address to estimate your general location. Its advertising and consent services can process device or app identifiers, privacy choices, app and ad interactions, and diagnostics for ad delivery, measurement, and fraud prevention. Data sent by the Google Mobile Ads SDK is encrypted in transit. The app does not request access to the Android advertising ID.

Where privacy choices are available, open **Settings → Privacy and data → Ad privacy choices** to review or change them. Google describes its use of advertising data and retention periods in [How Google uses information from apps](https://policies.google.com/technologies/partner-sites) and its [data retention policy](https://policies.google.com/technologies/retention).

## Purchases and subscriptions

Google Play processes Shift+ purchases and code redemptions. In builds with billing enabled, the RevenueCat billing service starts when you open the app. It uses a generated app user ID to check access, including before you buy anything. It processes available purchase history, product and subscription status, and billing-related app and device information to manage access, restore purchases and report subscription activity. Its customer profile can use the store country or infer a country from the network address. The app does not send RevenueCat your screening answers, fasting records, prayer settings or measurements. [RevenueCat privacy policy](https://www.revenuecat.com/privacy) · [Google privacy policy](https://policies.google.com/privacy)

## Retention, deletion and support

Your app records stay on your device until you delete them or clear the app's storage. To remove all local records, including settings and pending sync references, use **Settings → Privacy and data → How your data is protected → Delete all data** in the app (it asks you to confirm first), clear Fasting Shift's storage in Android Settings, or uninstall it. Delete any exported files at the destination where you saved them. Copies already held by Health Connect, another app or a file provider have their own deletion controls.

Manage or cancel subscriptions in Google Play. Uninstalling Fasting Shift does not cancel a subscription. For questions or a request about billing information held on our behalf, email [onnifyworks@gmail.com](mailto:onnifyworks@gmail.com). If you contact us, we use your email address and the information you choose to send to handle your request. Google Play manages its own transaction records.

Fasting Shift is intended for adults aged 18 and over. The safety screen restricts access to fasting plans for users under 18.
