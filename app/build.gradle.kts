plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** Reads a Gradle property, falling back to an environment variable (for CI). */
fun setting(property: String, env: String): String? =
    (project.findProperty(property) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

// Play upload key. Never committed: set these in ~/.gradle/gradle.properties or as CI secrets.
val uploadStoreFile = setting("fastingshift.upload.storeFile", "FS_UPLOAD_STORE_FILE")
val uploadStorePassword = setting("fastingshift.upload.storePassword", "FS_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = setting("fastingshift.upload.keyAlias", "FS_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = setting("fastingshift.upload.keyPassword", "FS_UPLOAD_KEY_PASSWORD")
val hasUploadKey = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { it != null }

val partnerEndpoint = "https://hombnfjwqhheyyvwemlk.supabase.co/functions/v1/fasting-shift-partners"
val testPartnerEndpoint = providers.gradleProperty("fastingshift.partnerEndpoint").orNull ?: partnerEndpoint
require(testPartnerEndpoint.matches(Regex("""https?://[A-Za-z0-9.:/_-]+"""))) { "Invalid partner test endpoint" }

val testAdsAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val liveAdsEnabled = setting("fastingshift.adsEnabled", "FS_ADS_ENABLED") == "true"

android {
    namespace = "com.fastingshift.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fastingshift.app"
        minSdk = 26
        targetSdk = 36
        // Bump for each Play upload: -Pfastingshift.versionCode=2 or FS_VERSION_CODE.
        versionCode = setting("fastingshift.versionCode", "FS_VERSION_CODE")?.toInt() ?: 1
        versionName = setting("fastingshift.versionName", "FS_VERSION_NAME") ?: "0.1.0"

        // RevenueCat public SDK key. Set `revenuecat.apiKey` in ~/.gradle/gradle.properties or
        // with -P. When empty, billing is disabled and every Shift+ entry point says so.
        val revenueCatKey = (project.findProperty("revenuecat.apiKey") as String?).orEmpty()
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
        buildConfigField("boolean", "USE_PILOT_INVITES", "false")
        buildConfigField("boolean", "INTERNAL_REVIEW_INSIGHTS", "false")
        buildConfigField("String", "PARTNER_ENDPOINT", "\"$partnerEndpoint\"")
        manifestPlaceholders["admobAppId"] = testAdsAppId
        buildConfigField("String", "SETTINGS_BANNER_ID", "\"$testBannerId\"")
        buildConfigField("boolean", "ADS_ENABLED", "true")
    }

    androidResources {
        // Android uses "in" for Indonesian resource qualifiers. Filter dependency translations too.
        localeFilters += listOf("en", "ms", "in")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "PARTNER_ENDPOINT", "\"$testPartnerEndpoint\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6742867363191087~8408799900"
            buildConfigField("String", "SETTINGS_BANNER_ID", "\"ca-app-pub-6742867363191087/8081496844\"")
            buildConfigField("boolean", "ADS_ENABLED", liveAdsEnabled.toString())
            // Signed with the upload key when it is configured; otherwise the build is unsigned.
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Same Play package as release, with personal Insights open to internal testers.
        create("playInternalReview") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "INTERNAL_REVIEW_INSIGHTS", "true")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
            buildConfigField("boolean", "ADS_ENABLED", "false")
        }
        // Installable test build: release optimisations, signed with the debug key, separate
        // app id and name so it can sit next to the Play build. Never upload this to Play.
        create("staging") {
            initWith(getByName("release"))
            buildConfigField("String", "PARTNER_ENDPOINT", "\"$testPartnerEndpoint\"")
            manifestPlaceholders["admobAppId"] = testAdsAppId
            buildConfigField("String", "SETTINGS_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "USE_PILOT_INVITES", "true")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("release")
        }
        // Signed after the build with the internal-testing certificate.
        create("internalBeta") {
            initWith(getByName("release"))
            manifestPlaceholders["admobAppId"] = testAdsAppId
            buildConfigField("String", "SETTINGS_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "USE_PILOT_INVITES", "true")
            signingConfig = null
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Store screenshots are only (re)recorded on request: ./gradlew :app:testDebugUnitTest -Pscreenshots
            if (project.hasProperty("screenshots")) {
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("fastingshift.screenshotDir", rootProject.file("docs/screenshots").absolutePath)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation("com.google.zxing:core:3.5.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.health.connect)
    implementation(libs.revenuecat.purchases)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
