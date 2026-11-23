// Проект: Voice Over Translation для ReVanced Manager v2.
// Патчы под ReVanced Patcher v22 (API из репозитория ReVanced/revanced-patcher).
rootProject.name = "revanced-vot-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}

plugins {
    id("app.revanced.patches") version "1.0.0-dev.11"
}
