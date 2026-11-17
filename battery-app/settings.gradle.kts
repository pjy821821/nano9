pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "battery-app"

// core: 순수 Kotlin(JVM). 어디서든 빌드·테스트 가능.
include(":core")

// app: Android. Android SDK가 있는 환경(ANDROID_HOME 또는 local.properties의 sdk.dir)에서만 포함한다.
// SDK 없는 CI나 헤드리스 환경에서는 `gradle :core:test`만 돌아간다.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (file("local.properties").exists() && file("local.properties").readText().contains("sdk.dir"))
if (hasAndroidSdk || providers.gradleProperty("withApp").isPresent) {
    include(":app")
} else {
    logger.lifecycle("battery-app: Android SDK not found, skipping :app (core only).")
}
