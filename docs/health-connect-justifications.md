# Health Connect permission justifications (for Play review)

Health Connect is optional. The app works fully with it off. The core permissions (weight and hydration) are requested only when the user turns on **Settings → Sync with Health Connect**. The others are requested incrementally, in context, only when the user uses the feature that needs them: glucose from the Log screen's "Glucose & ketones" section (with sync on), and sleep and resting heart rate from **Shift+ → Insights → Body signals**.

| Permission | Feature | Justification |
| --- | --- | --- |
| `android.permission.health.WRITE_WEIGHT` | Weight log (F6) | When the user logs their weight in Fasting Shift, the entry is written to Health Connect so other health apps the user chooses can see it. |
| `android.permission.health.READ_WEIGHT` | Weight log and Shift+ insights (F6, F10) | Shows weights the user recorded in other apps (e.g. a smart scale) in their weight log and the "weight vs fasting hours" insight, so they don't have to enter them twice. |
| `android.permission.health.WRITE_HYDRATION` | Water log (F6) | When the user logs water during a fast, the entry is written to Health Connect. Hydration is part of the app's extended-fast safety guidance. |
| `android.permission.health.WRITE_BLOOD_GLUCOSE` | Glucose log | When the user types in a glucose reading from their own meter, it is written to Health Connect as a manual entry, so the other health apps they choose (and their clinician's app) can see it. Deleting the reading in Fasting Shift removes the copy it wrote. Ketone readings are never written (Health Connect has no ketone type) and stay on the device. The app does not read glucose. |
| `android.permission.health.READ_SLEEP` | Shift+ insights: Body signals | Read-only. Shows the user's average sleep on nights after a fasting day next to other nights, so they can notice whether fasting fits their sleep. Shown only with at least 5 nights on each side, and always labelled "correlation, not cause". |
| `android.permission.health.READ_RESTING_HEART_RATE` | Shift+ insights: Body signals | Read-only. Shows weekly average resting heart rate in fasting weeks next to other weeks. Shown only with at least 3 weeks on each side, labelled "correlation, not cause". |

No other data types are requested. Data read from Health Connect is used on the device only (sleep and heart rate are not even stored by the app; they are read when the insights screen is open) and never transmitted.

**Privacy policy rationale:** `PermissionsRationaleActivity` handles `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` and, through an activity alias, `android.intent.action.VIEW_PERMISSION_USAGE` (Android 14+). It opens the privacy policy at https://fastingshift.com/privacy, which has a Health Connect section.
