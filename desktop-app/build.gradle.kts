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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.zeravrix.zeravrix.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
            )
            packageName = "ZERAVRIX"
            packageVersion = "1.6.0"
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
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "java.management",
                "java.naming",
                "java.security.jgss",
                "java.scripting"
            )
            windows {
                iconFile.set(project.file("src/main/resources/logo.ico"))
                menuGroup = "ZERAVRIX Windows"
                upgradeUuid = "a29d5873-51b8-4e3a-9762-cb07469a9b1a"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
