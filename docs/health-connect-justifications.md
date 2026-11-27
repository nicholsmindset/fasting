# Health Connect permission justifications (draft for Play review)

Health Connect is optional and off by default. Weight and hydration permissions are requested when the user turns on **Settings → Health connections → Sync with Health Connect**. The others are requested in context, only when the user uses the feature that needs them: glucose from the Log screen's "Glucose & ketones" section (with sync on), read-only steps from **History → This week → Connect step count**, and sleep and resting heart rate from **Shift+ → Insights → Body signals**.

| Permission | User-facing reason |
| --- | --- |
| `android.permission.health.WRITE_WEIGHT` | Writes a manually logged weight and its time to Health Connect so the user can access it in compatible apps they authorize. |
| `android.permission.health.READ_WEIGHT` | Imports recent weights recorded by other apps into the user's local weight log and trends when they enable sync. The import checks for duplicates. |
| `android.permission.health.WRITE_HYDRATION` | Writes user-logged water intake to Health Connect so the user can access it in compatible apps they authorize. |
| `android.permission.health.WRITE_BLOOD_GLUCOSE` | Writes a glucose reading the user typed in from their own meter as a manual entry, so apps they choose can see it. Deleting the reading in Fasting Shift removes the copy it wrote. The app does not read glucose. |
| `android.permission.health.READ_STEPS` | Shows the aggregate step count for the current local week in the free weekly recap. Access is separate from log sync and requires the user’s permission. The app reads only while the recap is open. Counts stay out of storage, exports, partners, ads and analytics. |
| `android.permission.health.READ_SLEEP` | Read-only. Shows average sleep on nights after a fasting day next to other nights (at least 5 nights each side), labelled "correlation, not cause". Not stored. |
| `android.permission.health.READ_RESTING_HEART_RATE` | Read-only. Shows weekly average resting heart rate in fasting weeks next to other weeks (at least 3 weeks each side), labelled "correlation, not cause". Not stored. |

The implementation imports weights from the last 30 days on permission grant. It requests no background-read or extended-history permission. Screening answers, ketones (Health Connect has no ketone type), and Ramadan or qada data stay outside Health Connect.

A failed write leaves the measurement in the encrypted database. App-private preferences keep the row number and a fingerprint for a manual retry from Log. Switching off sync stops retries. Deleting a local entry also removes its pending retry. Users can manage existing Health Connect copies and permissions directly in Health Connect. An imported local weight may be included when a user saves a full JSON backup or ZIP export.

`PermissionsRationaleActivity` opens the policy URL configured in the app. It handles the Health Connect rationale action and the Android 14+ permission-usage action through an alias. Before submission, verify the policy at https://fastingshift.com/privacy opens from both entry points and explains these uses. Confirm each permission and the status of the Health apps declaration in Play Console. [Android health permissions guidance](https://support.google.com/googleplay/android-developer/answer/12991134)
