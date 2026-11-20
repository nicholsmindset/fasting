pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "fasting-shift"

// The domain module is pure Kotlin/JVM and always builds. The Android modules need an
// Android SDK; when none is present (for example in a sandbox without SDK access) they are
// skipped so the domain logic can still be built and tested on its own.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

include(":core:domain")
if (hasAndroidSdk) {
    include(":core:data")
    include(":app")
} else {
    logger.warn("No Android SDK found: building :core:domain only.")
}
