# Fasting Shift privacy policy (draft)

> Release owner: add the verified publisher name, contact details and effective date before publication. The app opens https://fastingshift.com/privacy from Settings and from the Health Connect permission screen. Verify that the public page displays the approved policy.

Effective date: [date of publication]  
Publisher: [legal name shown on the Google Play listing]  
Privacy contact: [monitored email address or contact page]

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

Fasting Shift has no advertising or health-analytics service. We do not sell your health or religious-observance records or send them to the billing provider. If you enable reminders or add a widget, information such as fasting times can appear in notifications or on your home screen.

## Files you choose to save

The app excludes its private data from Android's automatic cloud backup and device transfer. You can save copies yourself through the Android file picker.

- **Export fasting history** writes a CSV of finished fasts, including their start, planned end, actual end, protocol and fast type. A fast type can reveal a religious observance.
- **Back up to a file** writes a full JSON backup. **Export** writes a ZIP containing that JSON and spreadsheet files. The full backup includes available notes, screening answers, health logs, religious records and backed-up settings.
- These files are readable by anyone with access to them. The app does not encrypt them. If you choose a cloud drive, its provider handles the file under its own terms. Choose a destination you trust.
- Importing a history CSV reads the file you select and shows a preview before adding records. Restoring a full backup replaces the app's database records and applies the preferences included in the file.

## Health Connect

You can enable sync in Settings and grant Android's Health Connect permissions. When you grant access, the app reads recent weight entries from Health Connect into your weight log. When you log weight or water with sync enabled, it writes the measurement and its time to Health Connect. Weight entries can also appear in your personal trends. Other apps can access Health Connect records according to the permissions you give them.

If a write fails, the measurement stays in the encrypted database. The app saves the local row number and a fingerprint in private preferences so you can retry from the Log screen. These references are excluded from the app's export and backup files. A successful retry or deletion of the local entry clears the corresponding pending reference. Turning sync off stops further attempts. The app does not request Health Connect access to glucose, ketones or religious records.

To stop future access, turn off sync and revoke permissions in Health Connect. To remove existing copies, delete the local entry in Fasting Shift and manage the Health Connect entry separately. Other apps you allowed to read that entry may also hold a copy. [Manage Health Connect data](https://support.google.com/android/answer/12201232)

Our use of Health Connect information is limited to the features you enable and follows Google's Health Connect limited-use requirements. [Android health permissions](https://support.google.com/googleplay/android-developer/answer/12991134)

## Purchases and subscriptions

Google Play processes Shift+ purchases and code redemptions. In builds with billing enabled, the RevenueCat billing service starts when you open the app. It uses a generated app user ID to check access, including before you buy anything. It processes available purchase history, product and subscription status, and billing-related app and device information to manage access, restore purchases and report subscription activity. Its customer profile can use the store country or infer a country from the network address. The app does not send RevenueCat your screening answers, fasting records, prayer settings or measurements. [RevenueCat privacy policy](https://www.revenuecat.com/privacy) · [Google privacy policy](https://policies.google.com/privacy)

## Retention, deletion and support

Your app records stay on your device until you delete them or clear the app's storage. To remove all local records, including settings and pending sync references, clear Fasting Shift's storage in Android Settings or uninstall it. Delete any exported files at the destination where you saved them. Copies already held by Health Connect, another app or a file provider have their own deletion controls.

Manage or cancel subscriptions in Google Play. Uninstalling Fasting Shift does not cancel a subscription. For questions or a request about billing information held on our behalf, contact [monitored privacy contact]. Google Play manages its own transaction records.

Fasting Shift is intended for adults aged 18 and over. The safety screen restricts access to fasting plans for users under 18.
