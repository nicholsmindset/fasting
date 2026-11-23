# Google Play Data safety worksheet (draft)

This worksheet describes the Android implementation. The publisher must check the signed release build, the production RevenueCat project and all distributed variants before submitting the form. The form, Health apps declaration and Health Connect permission request are separate submissions. Their current Console status is unverified.

| Data or flow | Implementation and proposed treatment |
| --- | --- |
| Fast history, notes, mood, screening, symptoms, weight, water, electrolytes, glucose and ketones | Processed on the device. The app has no endpoint for uploading these records. Optional Health Connect access reads weight, sleep and resting heart rate (sleep and heart rate are not stored) and writes weight, hydration and glucose. |
| Religious observance and city | Ramadan and Sunnah choices, fast types and qada repayments can reveal religious practice. The city is selected manually. The calendar uses bundled data and local calculations. It does not request location access or send these records to a service. |
| User-directed files | A history CSV includes the fast type. Full JSON backups and ZIP exports also contain available health, screening and religious records. These are unencrypted files written to a destination the user selects. Assess this user-directed transfer separately from developer collection or sharing. |
| Failed Health Connect writes | Private Android preferences store a local row ID and SHA-256 fingerprint to identify the entry on retry. Measurements and timestamps are read from the encrypted database. The retry list stays on the device and is excluded from exports. |
| Purchase history | Collected when RevenueCat billing is configured. Records persist for purchase verification and subscription reporting. RevenueCat's form guidance specifies required collection for app functionality and analytics. The SDK is initialized at launch in billing-enabled builds. |
| Customer and device identifiers | RevenueCat generates an app user ID for billing. The app does not set a named user ID, advertising ID or attribution network ID. Compare actual production identifiers with Play's definitions of user IDs and device or other IDs. Review enabled RevenueCat integrations before selecting categories. |
| Location and diagnostics | The app does not request GPS. RevenueCat can determine country from a transaction or IP address. The app does not opt into RevenueCat SDK diagnostics or install a standalone crash reporter. Review every bundled SDK in the release artifact. |
| Other recipients | RevenueCat processes billing data on the publisher's behalf. Review enabled integrations, webhooks and contracts before applying the service-provider exception for sharing. Google Play handles payment. |

Google defines collection as transmission off the device and includes SDK transmissions. Transfers the user explicitly initiates and reasonably expects can be excluded from the sharing declaration. Confirm the signed build's network behavior for startup, purchase, restore, Health Connect and both export paths. [Play Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469) · [RevenueCat Play Data safety guidance](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety) · [RevenueCat customer profiles](https://www.revenuecat.com/docs/dashboard-and-metrics/customer-history/active-entitlements)

## Before submission

- Confirm the legal publisher, a monitored privacy contact, effective date, and retention and deletion practices for billing and support. Test that support can locate and delete a test RevenueCat customer. Mark the data deletion option according to the process actually available. Clearing app storage handles only local copies.
- Publish the final privacy policy at the URL linked by the app, verify it opens publicly without sign-in from Settings and Health Connect, and enter the same URL in Play Console.
- Confirm billing uses encrypted transport, review SDK disclosure defaults and production integrations, then complete Data safety, the Health apps declaration and the [Health Connect justifications](health-connect-justifications.md) for reading weight, sleep and resting heart rate and writing weight, hydration and blood glucose. Record the submitted settings and approval status.
- Confirm the adult audience and any security-review claims against the actual Play listing. [Google Play health content and services](https://support.google.com/googleplay/android-developer/answer/16679511)
