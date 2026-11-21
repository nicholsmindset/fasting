package com.fastingshift.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import java.util.Locale

/**
 * App language (per-app language, Android 13+): opens the system's language page for this app,
 * which lists the locales in res/xml/locales_config.xml. Older Android has no per-app language
 * without AppCompat, so it shows where to change the phone's language instead.
 */
@Composable
fun LanguageGroup() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Re-read on return from the system page so the value updates.
        var refresh by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
        val current = remember(refresh) { AppLanguage.current(context) }
        InsetGroup(header = stringResource(R.string.settings_language), footer = stringResource(R.string.settings_language_footer)) {
            GroupRow(
                stringResource(R.string.settings_app_language),
                value = current?.let(AppLanguage::displayName) ?: stringResource(R.string.settings_language_system),
                showChevron = true,
                onClick = { AppLanguage.openSystemSettings(context) },
            )
        }
    } else {
        InsetGroup(header = stringResource(R.string.settings_language)) {
            GroupRow(stringResource(R.string.settings_app_language), subtitle = stringResource(R.string.settings_language_note))
        }
    }
}

object AppLanguage {
    /** The app's own language on Android 13+, or null when it follows the system. */
    fun current(context: Context): Locale? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        return if (locales.isEmpty) null else locales[0]
    }

    /** "Bahasa Melayu", "中文 (中国)": each language named in itself, as language pickers do. */
    fun displayName(locale: Locale): String = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

    fun openSystemSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
        // Some builds lack the page: fall back to this app's system settings.
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
        }
    }
}
