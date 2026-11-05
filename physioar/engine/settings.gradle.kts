// Standalone Gradle build for the PhysioAR exercise-intelligence engine.
// Pure Kotlin/JVM: no Android dependency, so it builds and tests on any JDK 17+.
// The Android app consumes it as an included build (see ../settings.gradle.kts).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "engine"
