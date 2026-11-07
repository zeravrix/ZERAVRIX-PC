import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(project(":source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.zeravrix.zeravrix.jvmhost.MainKt")
}

evaluationDependsOn(":source-api:example-extension")
val exampleExtensionJar = project(":source-api:example-extension").tasks.named<Jar>("jar")

tasks.test {
    useJUnitPlatform()
    dependsOn(exampleExtensionJar)
    systemProperty("example.extension.jar", exampleExtensionJar.get().archiveFile.get().asFile.absolutePath)
}
