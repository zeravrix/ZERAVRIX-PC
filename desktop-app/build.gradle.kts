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
            )
            packageName = "ZERAVRIX"
            packageVersion = "1.4.0"
            description = "ZERAVRIX — Native Windows Manga, Manhua & Manhwa Reader"
            vendor = "Zeravrix Studio"
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.xml",
                "java.datatransfer",
                "jdk.crypto.ec",
                "java.instrument",
                "java.net.http",
                "java.sql",
                "jdk.jfr",
                "jdk.jsobject",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.xml.dom",
                "java.management",
                "java.naming",
                "java.security.jgss",
                "java.scripting"
            )
            windows {
                iconFile.set(project.file("src/main/resources/logo.ico"))
                menuGroup = "ZERAVRIX"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
