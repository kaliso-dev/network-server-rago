// Conventions shared by every Java module of the Rago network.
//
// The build runs on any JDK 17+, but everything is compiled with --release 8: the 1.8.8 servers
// run on Java 8, so neither the bytecode nor the JDK API we call may go past Java 8.

plugins {
    java
}

group = "gg.rago"
version = providers.gradleProperty("rago.version").get()

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
    // -options hides the "source value 8 is obsolete" notice printed by recent JDKs.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Xlint:-processing"))
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Same sources, same jar: no timestamps, stable file order.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
