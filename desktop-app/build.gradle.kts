plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.linux_x64)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(project(":core"))
    implementation(project(":source-api"))
    implementation(project(":reader"))
    implementation("com.github.KotatsuApp:kotatsu-parsers:4d1e521aef") {
        exclude(group = "org.json", module = "json")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.zeravrix.zeravrix.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "zeravrix"
            packageVersion = "1.7.2"
            description = "ZERAVRIX — High-Performance Manga, Manhua & Manhwa Reader"
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
            linux {
                iconFile.set(project.file("src/main/resources/logo.png"))
                menuGroup = "Graphics"
                appCategory = "Utility;Graphics;Viewer;"
                rpmLicenseType = "GPL-3.0"
                shortcut = true
            }
        }
    }
}
