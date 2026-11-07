import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZERAVRIX"
include(":desktop-app", ":core", ":source-api", ":reader", ":jvm-host", ":source-api:example-extension")
project(":source-api:example-extension").projectDir = file("source-api/example-extension")
