# Play Store listing (DRAFT)

Wellness language only. No disease claims (diabetes, PCOS, etc.), no "lose X kg in Y days", no autophagy promises. Content rating: adults.

**Title (30):** Fasting Shift: Fasting Tracker

**Short description (80):** Track fasts, see your weekly habits and use free home-screen widgets.

**Full description:**

Fasting Shift is a simple, honest fasting tracker for Android.

ONE TAP TO START
Start a fast in one tap and get on with your day. Your start time is saved on your phone so you can return to the same fast after a restart, app update or time-zone change.

FREE, FOR REAL
Choose a schedule such as 12:12 or 16:8, or set your own within your screening limits. The timer, widgets, unlimited history, export and safety features are free.

SAFETY BUILT IN
A short safety check before you start. A checklist before long fasts, regular check-ins during them, and guidance on breaking a long fast gently. Fasting isn't right for everyone, and the app says so.

HONEST ABOUT THE SCIENCE
The body timeline shows how much evidence supports each entry, including when findings come from animal studies.

YOUR RECORDS
Your fasting and health records are encrypted in the app database. With your permission, Health Connect can import weight and save weight, water and glucose readings you log. In Shift+ Body signals, you can also allow read access to sleep and resting heart rate to compare trends. You can export fasting history or save a full backup to a file you choose. No account is required. The free plan can show a Google ad in Settings. Shift+ is ad-free.

FAST WITH A FRIEND
Invite someone to a challenge with a link. Each of you tracks progress on your own phone and chooses when to share a progress card. Streaks count days you completed your plan.

SHIFT+ (OPTIONAL)
See your weekly consistency, average fast by weekday, and optional weight entries alongside your fasting history. View available plans, prices and trial terms before purchase. Manage subscriptions through Google Play. You can also read a free reflection in the app.

Fasting Shift is a wellness tracker. It is not a medical device and does not diagnose, treat, cure or prevent medical conditions. Talk to your doctor before fasting, especially if you take medication.

The [store screenshots](screenshots/) show the timer, History summary, recent fasts chart and calendar, Log and Settings in light and dark modes. The light-mode set also includes the [Muslim fasting hub](screenshots/5-muslim-hub.png), [challenge progress](screenshots/6-challenge.png) and [trust center](screenshots/7-trust-center.png).

To regenerate the images, run `./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*.StoreScreenshotTest' --tests '*MoatsCaptureTest'`. The moats captures are in `app/build/moats-captures/` under `en/` and `ms/`. The home-screen widget screenshot still needs a phone.
