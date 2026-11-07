plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("javazoom:jlayer:1.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}
