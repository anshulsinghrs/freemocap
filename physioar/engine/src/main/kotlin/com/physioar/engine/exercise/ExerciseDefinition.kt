package com.physioar.engine.exercise

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.BodySide

/**
 * Static, human-readable description of an exercise. Every exercise has its own
 * definition and its own analyzer; there is intentionally no generic "one threshold
 * fits all" validator (see docs/03-integration-decisions.md, ADR-006).
 */
data class ExerciseDefinition(
    val id: String,
    val name: String,
    val bodyRegion: BodyRegion,
    val cameraSetup: CameraSetup,
    /** Landmarks the analyzer needs, per side, before any measurement is trusted. */
    val requiredLandmarks: (BodySide) -> Set<BodyLandmark>,
    val startingPosition: String,
    val instructions: List<String>,
    val phases: List<PhaseSpec>,
    val measuredAngles: List<AngleSpec>,
    val repetitionCriteria: String,
    val commonErrors: List<CommonError>,
    val safetyConditions: List<String>,
    /** Whether an analyzer exists. Definitions may be published before their analyzer. */
    val analyzerStatus: AnalyzerStatus,
    /**
     * Whether the default movement parameters have been reviewed by a licensed
     * physiotherapist. PhysioAR ships unreviewed placeholders and says so in the UI.
     */
    val clinicalReview: ClinicalReviewStatus,
)

enum class BodyRegion { KNEE, SHOULDER, HIP, ANKLE, UPPER_LIMB, LOWER_LIMB_FUNCTIONAL }

enum class CameraView { SAGITTAL_SIDE, FRONTAL, SAGITTAL_OR_FRONTAL }

data class CameraSetup(
    val view: CameraView,
    val description: String,
    /** Whether a single RGB camera is believed sufficient; see the audit for reasoning. */
    val singleRgbCameraAdequacy: String,
)

data class PhaseSpec(val phase: MovementPhase, val description: String)

data class AngleSpec(
    val name: String,
    val vertex: String,
    val definition: String,
    val purpose: String,
)

data class CommonError(
    /** Feedback code emitted when detected; null for errors the engine cannot observe. */
    val code: FeedbackCode?,
    val description: String,
    val detection: String,
    /** Whether the engine currently detects this error, or it is documentation only. */
    val detectedByEngine: Boolean,
)

enum class AnalyzerStatus { IMPLEMENTED, PLANNED }

enum class ClinicalReviewStatus {
    /** Default parameters are engineering placeholders. Not for clinical use. */
    NOT_REVIEWED,
    REVIEWED,
}
