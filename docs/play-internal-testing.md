# Distribute Fasting Shift through Google Play internal testing

Use **Actions → Publish to Play internal testing** for Android package `com.fastingshift.app`. It signs the release bundle with the registered upload key and sends it to the phone app’s internal track. Google calls this track `qa` in the Publishing API. The staging and sideload beta packages have separate identities.

## One-time setup

1. Create or select Fasting Shift in Play Console, enable Play App Signing and register the upload certificate. Complete the first app upload in Play Console before using the Publishing API. To build it locally, use the release signing settings in the [Android README](../README.md#uploading-to-play-internal-testing). In **Testing → Internal testing → Testers**, select the tester list and copy its opt-in link. Complete any setup or review Play requests for this app.
2. Enable the Google Play Android Developer API in the Google Cloud project. Invite a dedicated Google service account to Play Console, grant it access to Fasting Shift only, and assign app-level access to view app information and release apps to testing tracks.
3. Configure Google Workload Identity Federation for GitHub Actions. Restrict the provider and service-account impersonation binding to this repository, the actual default branch and the `play-internal` environment. Grant the federated principal `roles/iam.workloadIdentityUser` on the dedicated service account. Keep the conditions in sync if the default branch changes.
4. Create the GitHub environment **play-internal** and allow deployments only from the default branch. Set the following values in that environment.

| Type | Names | Source |
| --- | --- | --- |
| Secret | `PLAY_UPLOAD_KEYSTORE_BASE64` | Base64 of the registered upload keystore. Keep a separate recovery copy. |
| Secrets | `FS_UPLOAD_STORE_PASSWORD`, `FS_UPLOAD_KEY_ALIAS`, `FS_UPLOAD_KEY_PASSWORD` | Entries for that keystore. |
| Variable | `PLAY_UPLOAD_CERT_SHA256` | SHA-256 of the **upload key certificate** in Play Console. Colons are accepted. |
| Variables | `PLAY_WORKLOAD_IDENTITY_PROVIDER`, `PLAY_SERVICE_ACCOUNT` | Full provider resource name and service account email. |
| Optional variable | `REVENUECAT_ANDROID_PUBLIC_KEY` | Public Android SDK key for the Play app. Leave it blank for a free-only build. |

## Send an update

Choose the tested default-branch commit and run the workflow from that branch. Enter an unused version code higher than all existing Play codes. Let the workflow finish before editing Play Console. On success, check the version in **Testing → Internal testing** and open the tester opt-in link on a registered Google account. Install an update over a previous Play version to check that existing data survives.

If a run stops around the commit step, check the internal release and run log before retrying. Rerunning the same Actions run uses the same commit and version. An identical release already on the internal track is treated as complete. Drafts and interrupted rollouts need to be resolved in Play Console. If Play reports the version code was already used elsewhere, dispatch the tested commit with a fresh, higher code from the default branch when it still points to that commit.

[Google Play testing](https://support.google.com/googleplay/android-developer/answer/9845334) · [Publishing API setup](https://developers.google.com/android-publisher/getting_started) · [Edits](https://developers.google.com/android-publisher/edits) · [Track names](https://developers.google.com/android-publisher/tracks) · [GitHub OIDC with Google](https://github.com/google-github-actions/auth)
