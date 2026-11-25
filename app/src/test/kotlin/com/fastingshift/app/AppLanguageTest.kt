package com.fastingshift.app

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.settings.AppLanguage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AppLanguageTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun systemPickerOffersExactlyEnglishMalayAndIndonesian() {
        val tags = mutableListOf<String>()
        app.resources.getXml(R.xml.locales_config).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "locale") {
                    tags.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                }
            }
        }
        assertEquals(listOf("en", "ms", "id"), tags)
    }

    @Test fun upgradeDropsAllChineseVariantsAndPreservesOtherPreferencesInOrder() {
        val manager = app.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("zh-Hant-TW,ms-MY,zh-CN,id-ID")
        AppLanguage.migrateRetiredLanguage(app)
        assertEquals("ms-MY,id-ID", manager.applicationLocales.toLanguageTags())
        AppLanguage.migrateRetiredLanguage(app)
        assertEquals("ms-MY,id-ID", manager.applicationLocales.toLanguageTags())
    }

    @Test fun formerChineseOnlyPreferenceReturnsToSystem() {
        val manager = app.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("zh-Hans-CN")
        AppLanguage.migrateRetiredLanguage(app)
        assertTrue(manager.applicationLocales.isEmpty)
    }
}
