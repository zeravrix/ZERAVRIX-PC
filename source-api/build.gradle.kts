plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jsoup:jsoup:1.21.1")
    implementation("com.github.KotatsuApp:kotatsu-parsers:4d1e521aef") {
        exclude(group = "org.json", module = "json")
    }
    implementation("org.json:json:20240303")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.collection:collection:1.4.5")
}


dependencies {
    testImplementation(kotlin("test"))
}

kotlin { sourceSets { test { languageSettings { optIn("kotlin.ExperimentalStdlibApi") } } } }
