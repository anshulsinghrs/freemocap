package com.physioar.engine.exercises.kneeextension

import com.physioar.engine.exercise.AnalyzerStatus
import com.physioar.engine.exercise.AngleSpec
import com.physioar.engine.exercise.BodyRegion
import com.physioar.engine.exercise.CameraSetup
import com.physioar.engine.exercise.CameraView
import com.physioar.engine.exercise.ClinicalReviewStatus
import com.physioar.engine.exercise.CommonError
import com.physioar.engine.exercise.ExerciseDefinition
import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.MovementPhase
import com.physioar.engine.exercise.PhaseSpec
import com.physioar.engine.pose.BodySide

/** Definition (metadata) of seated knee extension. Logic lives in [SeatedKneeExtensionAnalyzer]. */
object SeatedKneeExtension {
    const val ID = "seated_knee_extension"

    fun requiredLandmarks(side: BodySide) = setOf(side.shoulder(), side.hip(), side.knee(), side.ankle())

    val definition = ExerciseDefinition(
        id = ID,
        name = "Seated knee extension",
        bodyRegion = BodyRegion.KNEE,
        cameraSetup = CameraSetup(
            view = CameraView.SAGITTAL_SIDE,
            description = "Phone upright at roughly knee-to-hip height, 2–3 m away, " +
                "perpendicular to the exercised leg, so the exercised side faces the camera " +
                "and shoulder, hip, knee and ankle are all in frame.",
            singleRgbCameraAdequacy = "Adequate for a 2D sagittal-plane knee angle when the camera " +
                "is perpendicular to the leg. Accuracy degrades with camera obliquity; the analyzer " +
                "rejects clearly frontal views and cross-checks against 3D world landmarks.",
        ),
        requiredLandmarks = ::requiredLandmarks,
        startingPosition = "Sit upright on a firm chair, back supported, hips and knees at about 90°, " +
            "feet flat on the floor.",
        instructions = listOf(
            "Sit with the exercised side facing the camera.",
            "Slowly straighten the knee, lifting the foot until the leg is as straight as prescribed.",
            "Hold the position for the prescribed time.",
            "Slowly lower the foot back to the floor.",
        ),
        phases = listOf(
            PhaseSpec(MovementPhase.SETUP, "Seated start position held still to capture baseline."),
            PhaseSpec(MovementPhase.REST, "Knee bent below the prescribed rest limit."),
            PhaseSpec(MovementPhase.MOVING_TO_TARGET, "Knee extending towards the prescribed angle."),
            PhaseSpec(MovementPhase.HOLDING, "Knee at or beyond the prescribed angle."),
            PhaseSpec(MovementPhase.RETURNING, "Knee flexing back to the rest position."),
        ),
        measuredAngles = listOf(
            AngleSpec(
                name = "knee",
                vertex = "knee",
                definition = "Interior angle hip–knee–ankle in image pixel space (180° = straight).",
                purpose = "Primary measure: phase, range of motion, target reached.",
            ),
            AngleSpec(
                name = "knee3d",
                vertex = "knee",
                definition = "Same angle from MediaPipe world (3D) landmarks, when available.",
                purpose = "Cross-check only: large 2D/3D disagreement marks the frame untrusted.",
            ),
            AngleSpec(
                name = "trunk",
                vertex = "hip",
                definition = "Inclination of the hip→shoulder segment from image vertical.",
                purpose = "Detects leaning back to substitute for knee extension.",
            ),
        ),
        repetitionCriteria = "A repetition starts when the knee rises above the rest limit (+hysteresis) " +
            "and ends when it returns below the rest limit. It is CORRECT only if the target angle was " +
            "reached, held for the prescribed time, the return was controlled, no compensation was " +
            "observed and tracking was reliable throughout.",
        commonErrors = listOf(
            CommonError(FeedbackCode.STRAIGHTEN_KNEE_MORE, "Knee not extended to the prescribed angle.",
                "Peak knee angle below target.", detectedByEngine = true),
            CommonError(FeedbackCode.HOLD_TOO_SHORT, "Hold at target shorter than prescribed.",
                "Cumulative time at target below holdDurationMs.", detectedByEngine = true),
            CommonError(FeedbackCode.AVOID_KICKING, "Kicking the leg up using momentum.",
                "Smoothed extension velocity above limit.", detectedByEngine = true),
            CommonError(FeedbackCode.LOWER_SLOWLY, "Dropping the leg instead of lowering it.",
                "Smoothed lowering velocity above maxReturnVelocityDegPerSec.", detectedByEngine = true),
            CommonError(FeedbackCode.KEEP_BACK_STILL, "Leaning the trunk back to lift the leg.",
                "Trunk inclination change from baseline above tolerance.", detectedByEngine = true),
            CommonError(FeedbackCode.KEEP_HIPS_ON_CHAIR, "Lifting the hip/thigh off the chair.",
                "Upward hip displacement relative to thigh length above tolerance.", detectedByEngine = true),
            CommonError(null, "Hip rotation / foot turned out.",
                "Not observable reliably from a single side view; not detected.", detectedByEngine = false),
        ),
        safetyConditions = listOf(
            "Stop immediately and contact your physiotherapist if you feel sharp or increasing pain.",
            "Use a stable chair without wheels.",
            "The app only counts and gives cues; it does not diagnose. Follow your physiotherapist's advice.",
            "If tracking is lost, the app pauses evaluation rather than guessing.",
            "The set stops automatically at the prescribed maximum number of repetitions.",
        ),
        analyzerStatus = AnalyzerStatus.IMPLEMENTED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )
}
