# Google Play Data safety form — proposed answers (DRAFT)

Matches SR-9 and the privacy policy. Review with counsel before submitting.

**Does your app collect or share any of the required user data types?** Yes (purchase history via RevenueCat).

Google's definition: "collected" means transmitted off the device. Health data processed only on the device is **not** collected.

| Data type | Collected? | Shared? | Notes |
| --- | --- | --- | --- |
| Health info (fasts, symptoms, screening) | No | No | Stored only on device, encrypted |
| Fitness info / body measurements (weight, water) | No | No | On device; optional Health Connect sync is on-device |
| Purchase history | Yes | No | RevenueCat (service provider) to confirm entitlements; required for Shift+ only |
| App info and performance (crash logs) | No (MVP) | No | Update if a crash reporter is added |
| Device or other IDs | Yes | No | RevenueCat anonymous app user ID |
| Personal info (name, email) | No | No | No accounts |
| Location | No | No | Emergency number uses device region setting only, offline |

- **Encrypted in transit:** Yes (RevenueCat uses HTTPS).
- **Users can request deletion:** Yes. Health data: uninstall / clear storage. Purchase records: via support email to request RevenueCat deletion.
- **Committed to Play Families policy:** Not applicable (adults only).
- **Independent security review:** No.
