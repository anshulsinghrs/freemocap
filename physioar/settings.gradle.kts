// PhysioAR Android build.
//  - :app            Android application (Kotlin, Jetpack Compose, CameraX, MediaPipe Tasks)
//  - engine/         Included build: pure-Kotlin exercise intelligence engine, no Android
//                    dependency. Build/test it on its own with `cd engine && ./gradlew test`.
// The AR module (Unity) is a separate project under ar/ and is not part of this Gradle build.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhysioAR"

includeBuild("engine")
include(":app")
