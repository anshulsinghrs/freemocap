import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.physioar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.physioar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // MediaPipe memory-maps the model; it must not be compressed in the APK.
        noCompress += "task"
    }
}

dependencies {
    // Exercise intelligence engine (included build, see ../settings.gradle.kts).
    implementation("com.physioar:engine:0.1.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// Downloads the MediaPipe Pose Landmarker models into assets/ before the first build.
// Same model URLs as google-ai-edge/mediapipe-samples (Apache-2.0) download_tasks.gradle,
// re-implemented without the third-party download plugin.
val poseModels = mapOf(
    "pose_landmarker_lite.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
    "pose_landmarker_full.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task",
)
val downloadPoseModels by tasks.registering {
    val assetsDir = layout.projectDirectory.dir("src/main/assets")
    outputs.files(poseModels.keys.map { assetsDir.file(it) })
    doLast {
        assetsDir.asFile.mkdirs()
        poseModels.forEach { (name, url) ->
            val target = assetsDir.file(name).asFile
            if (!target.exists()) {
                logger.lifecycle("Downloading $name")
                URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadPoseModels) }
