# Health Connect permission justifications (draft for Play review)

Health Connect is optional. Fasting Shift requests these permissions when a user enables sync in Settings. The default is off.

| Permission | User-facing reason |
| --- | --- |
| `android.permission.health.WRITE_WEIGHT` | Writes a manually logged weight and its time to Health Connect so the user can access it in compatible apps they authorize. |
| `android.permission.health.READ_WEIGHT` | Imports recent weights recorded by other apps into the user's local weight log and trends when they enable sync. The import checks for duplicates. |
| `android.permission.health.WRITE_HYDRATION` | Writes user-logged water intake to Health Connect so the user can access it in compatible apps they authorize. |

The request covers two record types, weight and hydration. The implementation imports weights from the last 30 days on permission grant. It requests no background-read or extended-history permission. Screening answers, glucose, ketones, and Ramadan or qada data stay outside Health Connect.

A failed write leaves the measurement in the encrypted database. App-private preferences keep the row number and a fingerprint for a manual retry from Log. Switching off sync stops retries. Deleting a local entry also removes its pending retry. Users can manage existing Health Connect copies and permissions directly in Health Connect. An imported local weight may be included when a user saves a full JSON backup or ZIP export.

`PermissionsRationaleActivity` opens the policy URL configured in the app. It handles the Health Connect rationale action and the Android 14+ permission-usage action through an alias. Before submission, verify the policy at https://fastingshift.com/privacy opens from both entry points and explains these uses. Confirm each permission and the status of the Health apps declaration in Play Console. [Android health permissions guidance](https://support.google.com/googleplay/android-developer/answer/12991134)
