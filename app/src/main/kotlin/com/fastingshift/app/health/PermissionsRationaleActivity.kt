package com.fastingshift.app.health

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fastingshift.app.R

/**
 * Required by Health Connect: shown when the user asks why the app wants health permissions.
 * Opens the privacy policy, which explains the Health Connect data use (Play requirement).
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))) }
        finish()
    }
}
