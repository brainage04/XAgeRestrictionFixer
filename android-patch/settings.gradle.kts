rootProject.name = "x-age-restriction-fixer-patches"

pluginManagement {
    repositories {
        // The Morphe Gradle plugin and patcher are published only to GitHub Packages, which needs
        // a token even for public packages. scripts/install-morphe-tooling.sh builds both from
        // source into Maven local, so no token is needed; Maven local is searched first.
        mavenLocal()
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    // Keep in step with scripts/install-morphe-tooling.sh, which reads this line.
    id("app.morphe.patches") version "2.1.0"
}
