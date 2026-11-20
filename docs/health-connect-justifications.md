# Health Connect permission justifications (for Play review)

Health Connect is optional. The app works fully with it off. Permissions are requested only when the user turns on **Settings → Sync with Health Connect**.

| Permission | Feature | Justification |
| --- | --- | --- |
| `android.permission.health.WRITE_WEIGHT` | Weight log (F6) | When the user logs their weight in Fasting Shift, the entry is written to Health Connect so other health apps the user chooses can see it. |
| `android.permission.health.READ_WEIGHT` | Weight log and Shift+ insights (F6, F10) | Shows weights the user recorded in other apps (e.g. a smart scale) in their weight log and the "weight vs fasting hours" insight, so they don't have to enter them twice. |
| `android.permission.health.WRITE_HYDRATION` | Water log (F6) | When the user logs water during a fast, the entry is written to Health Connect. Hydration is part of the app's extended-fast safety guidance. |

No other data types are requested. Data read from Health Connect is stored only on the device and never transmitted.

**Privacy policy rationale:** `PermissionsRationaleActivity` handles `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` and, through an activity alias, `android.intent.action.VIEW_PERMISSION_USAGE` (Android 14+). It opens the privacy policy at https://fastingshift.com/privacy, which has a Health Connect section.
