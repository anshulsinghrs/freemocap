package com.physioar.engine.exercise

import com.physioar.engine.exercises.kneeextension.SeatedKneeExtension
import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.BodySide

/**
 * The initial PhysioAR exercise library.
 *
 * Only seated knee extension has an analyzer so far (Phase 3). The other five have
 * exercise-specific definitions so the UI, prescriptions and documentation can be built
 * against them; each will get its OWN analyzer and validation rules in Phase 4. They are
 * marked [AnalyzerStatus.PLANNED] and the app must not offer live evaluation for them.
 */
object ExerciseCatalog {

    val all: List<ExerciseDefinition> by lazy {
        listOf(
            SeatedKneeExtension.definition,
            shoulderFlexion,
            sitToStand,
            heelRaises,
            hipAbduction,
            wallPushUps,
        )
    }

    fun byId(id: String): ExerciseDefinition? = all.firstOrNull { it.id == id }

    private val shoulderFlexion = ExerciseDefinition(
        id = "shoulder_flexion",
        name = "Shoulder flexion",
        bodyRegion = BodyRegion.SHOULDER,
        cameraSetup = CameraSetup(
            CameraView.SAGITTAL_SIDE,
            "Side view, exercised arm nearest the camera, whole trunk and arm in frame.",
            "Adequate for sagittal elevation angle. Cannot separate true flexion from scaption " +
                "(arm drifting out to the side) in a pure side view; a frontal check view is planned.",
        ),
        requiredLandmarks = { s: BodySide -> setOf(s.hip(), s.shoulder(), s.elbow(), s.wrist()) },
        startingPosition = "Standing or sitting tall, arm by the side, thumb pointing forward.",
        instructions = listOf("Raise the straight arm forward and up as far as prescribed.", "Lower slowly."),
        phases = standardPhases("Arm raising forward", "Arm lowering"),
        measuredAngles = listOf(
            AngleSpec("shoulder_flexion", "shoulder", "Angle hip–shoulder–elbow (trunk vs upper arm).", "Primary ROM."),
            AngleSpec("elbow", "elbow", "Angle shoulder–elbow–wrist.", "Bent-elbow error."),
            AngleSpec("trunk", "hip", "Hip→shoulder inclination from vertical.", "Back-arching compensation."),
        ),
        repetitionCriteria = "Shoulder angle rises from rest to the prescribed elevation and returns.",
        commonErrors = listOf(
            CommonError(null, "Arching the lower back to gain height.", "Trunk extension from baseline.", false),
            CommonError(null, "Shrugging the shoulder.", "Shoulder–ear distance shrinking.", false),
            CommonError(null, "Bending the elbow.", "Elbow angle below threshold.", false),
        ),
        safetyConditions = listOf("Stop if pain increases.", "Post-operative shoulder limits must come from the surgeon/physiotherapist."),
        analyzerStatus = AnalyzerStatus.PLANNED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )

    private val sitToStand = ExerciseDefinition(
        id = "sit_to_stand",
        name = "Sit-to-stand",
        bodyRegion = BodyRegion.LOWER_LIMB_FUNCTIONAL,
        cameraSetup = CameraSetup(
            CameraView.SAGITTAL_SIDE,
            "Side view of chair and patient, from head to feet, with space above the head for standing.",
            "Adequate for hip/knee angles and trunk lean timing. Knee valgus requires a frontal view.",
        ),
        requiredLandmarks = { s: BodySide -> setOf(s.shoulder(), s.hip(), s.knee(), s.ankle()) },
        startingPosition = "Seated at the front of a firm chair, feet flat, arms crossed (unless prescribed otherwise).",
        instructions = listOf("Lean forward and stand up fully.", "Sit back down with control."),
        phases = listOf(
            PhaseSpec(MovementPhase.REST, "Seated."),
            PhaseSpec(MovementPhase.MOVING_TO_TARGET, "Trunk lean forward and rising."),
            PhaseSpec(MovementPhase.HOLDING, "Standing upright (hip and knee extended)."),
            PhaseSpec(MovementPhase.RETURNING, "Controlled descent to the chair."),
        ),
        measuredAngles = listOf(
            AngleSpec("knee", "knee", "Hip–knee–ankle.", "Seated vs standing."),
            AngleSpec("hip", "hip", "Shoulder–hip–knee.", "Full hip extension when standing."),
            AngleSpec("hip_height", "hip", "Vertical hip position normalised by thigh length.", "Phase detection."),
        ),
        repetitionCriteria = "Hip and knee reach standing extension, then hip returns to seated height.",
        commonErrors = listOf(
            CommonError(null, "Not standing fully upright.", "Hip/knee extension below threshold.", false),
            CommonError(null, "Dropping into the chair.", "Descent faster than prescribed.", false),
            CommonError(null, "Using hands to push up (when not allowed).", "Wrist near knee/chair during rise.", false),
        ),
        safetyConditions = listOf("Chair against a wall.", "Supervision for patients at fall risk.", "Stop on dizziness."),
        analyzerStatus = AnalyzerStatus.PLANNED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )

    private val heelRaises = ExerciseDefinition(
        id = "heel_raises",
        name = "Heel raises",
        bodyRegion = BodyRegion.ANKLE,
        cameraSetup = CameraSetup(
            CameraView.SAGITTAL_SIDE,
            "Side view at ankle height, feet clearly visible, good lighting on the feet.",
            "UNCERTAIN: heel lift is a small displacement (few cm) and MediaPipe foot landmarks are the " +
                "least stable. Requires validation; may need the camera closer to the feet or a second view.",
        ),
        requiredLandmarks = { s: BodySide -> setOf(s.knee(), s.ankle(), s.heel(), s.footIndex()) },
        startingPosition = "Standing, feet hip-width apart, holding a stable support.",
        instructions = listOf("Rise up onto the balls of the feet.", "Lower slowly."),
        phases = standardPhases("Heels rising", "Heels lowering"),
        measuredAngles = listOf(
            AngleSpec("heel_lift", "heel", "Heel height above baseline, normalised by foot length (heel–toe).",
                "Primary measure. NOT the knee angle: knee angle does not measure plantarflexion."),
            AngleSpec("knee", "knee", "Hip–knee–ankle.", "Detect knee bending compensation."),
        ),
        repetitionCriteria = "Normalised heel lift exceeds prescribed height, then returns to baseline.",
        commonErrors = listOf(
            CommonError(null, "Bending the knees.", "Knee angle drop from baseline.", false),
            CommonError(null, "Leaning forward.", "Trunk inclination change.", false),
        ),
        safetyConditions = listOf("Always hold a stable support.", "Stop on calf cramp or pain."),
        analyzerStatus = AnalyzerStatus.PLANNED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )

    private val hipAbduction = ExerciseDefinition(
        id = "hip_abduction",
        name = "Standing hip abduction",
        bodyRegion = BodyRegion.HIP,
        cameraSetup = CameraSetup(
            CameraView.FRONTAL,
            "Frontal view (patient facing the camera), full body in frame.",
            "Adequate for frontal-plane abduction angle. Hip flexion substitution is hard to see frontally.",
        ),
        requiredLandmarks = { _: BodySide ->
            setOf(BodyLandmark.LEFT_HIP, BodyLandmark.RIGHT_HIP, BodyLandmark.LEFT_KNEE, BodyLandmark.RIGHT_KNEE,
                BodyLandmark.LEFT_ANKLE, BodyLandmark.RIGHT_ANKLE, BodyLandmark.LEFT_SHOULDER, BodyLandmark.RIGHT_SHOULDER)
        },
        startingPosition = "Standing tall holding a support, feet together.",
        instructions = listOf("Lift the leg out to the side, keeping the toes forward.", "Lower slowly."),
        phases = standardPhases("Leg moving out", "Leg returning"),
        measuredAngles = listOf(
            AngleSpec("abduction", "hip", "Angle between pelvis-perpendicular and hip→ankle of moving leg.", "Primary ROM."),
            AngleSpec("trunk_lateral", "pelvis", "Shoulder line tilt vs hip line.", "Trunk side-lean compensation."),
        ),
        repetitionCriteria = "Abduction angle exceeds prescribed value, then returns.",
        commonErrors = listOf(
            CommonError(null, "Leaning the trunk to the side.", "Lateral trunk tilt.", false),
            CommonError(null, "Hip hitching.", "Pelvic obliquity.", false),
        ),
        safetyConditions = listOf("Hold a stable support.", "Respect hip-precaution limits after hip replacement."),
        analyzerStatus = AnalyzerStatus.PLANNED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )

    private val wallPushUps = ExerciseDefinition(
        id = "wall_push_ups",
        name = "Wall push-ups",
        bodyRegion = BodyRegion.UPPER_LIMB,
        cameraSetup = CameraSetup(
            CameraView.SAGITTAL_SIDE,
            "Side view, wall, hands and heels all in frame.",
            "Adequate for elbow angle and body-line straightness in the sagittal plane.",
        ),
        requiredLandmarks = { s: BodySide -> setOf(s.shoulder(), s.elbow(), s.wrist(), s.hip(), s.ankle()) },
        startingPosition = "Standing an arm's length from the wall, palms on the wall at shoulder height.",
        instructions = listOf("Bend the elbows to bring the chest towards the wall.", "Push back to straight arms."),
        phases = standardPhases("Lowering towards wall", "Pushing back"),
        measuredAngles = listOf(
            AngleSpec("elbow", "elbow", "Shoulder–elbow–wrist.", "Primary depth."),
            AngleSpec("body_line", "hip", "Shoulder–hip–ankle.", "Sagging or piking."),
        ),
        repetitionCriteria = "Elbow angle reaches prescribed flexion, then returns to extension.",
        commonErrors = listOf(
            CommonError(null, "Sagging or piking at the hips.", "Body line deviation from 180°.", false),
            CommonError(null, "Not bending elbows enough.", "Elbow angle above target.", false),
        ),
        safetyConditions = listOf("Non-slip footing.", "Stop on shoulder or wrist pain."),
        analyzerStatus = AnalyzerStatus.PLANNED,
        clinicalReview = ClinicalReviewStatus.NOT_REVIEWED,
    )

    private fun standardPhases(towards: String, back: String) = listOf(
        PhaseSpec(MovementPhase.REST, "Starting position."),
        PhaseSpec(MovementPhase.MOVING_TO_TARGET, towards),
        PhaseSpec(MovementPhase.HOLDING, "At prescribed target."),
        PhaseSpec(MovementPhase.RETURNING, back),
    )
}
