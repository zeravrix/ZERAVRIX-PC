plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jsoup:jsoup:1.21.1")
}


dependencies {
    testImplementation(kotlin("test"))
}

kotlin { sourceSets { test { languageSettings { optIn("kotlin.ExperimentalStdlibApi") } } } }
