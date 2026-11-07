plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(project(":core"))
    implementation(project(":source-api"))
    implementation(project(":reader"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.openjfx:javafx-base:21:win")
    implementation("org.openjfx:javafx-graphics:21:win")
    implementation("org.openjfx:javafx-controls:21:win")
    implementation("org.openjfx:javafx-swing:21:win")
    implementation("org.openjfx:javafx-web:21:win")
}

compose.desktop {
    application {
        mainClass = "com.zeravrix.zeravrix.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
            packageName = "ZERAVRIX"
            packageVersion = "1.4.0"
            description = "ZERAVRIX 1.4.0 — native manga reader with persistent cache, progress resume, and selectable artwork themes."
            vendor = "Zeravrix Studio"
            windows {
                menuGroup = "ZERAVRIX"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
