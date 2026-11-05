package com.physioar.engine.exercises.kneeextension

import com.physioar.engine.exercise.ExerciseAnalyzer
import com.physioar.engine.exercise.ExerciseDefinition
import com.physioar.engine.exercise.Feedback
import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.FeedbackThrottle
import com.physioar.engine.exercise.FrameAnalysis
import com.physioar.engine.exercise.MovementPhase
import com.physioar.engine.exercise.MovementVerdict
import com.physioar.engine.exercise.RepetitionResult
import com.physioar.engine.exercise.SessionSummary
import com.physioar.engine.exercise.Severity
import com.physioar.engine.geometry.Vec2
import com.physioar.engine.geometry.inclinationFromVerticalDeg
import com.physioar.engine.geometry.interiorAngleDeg
import com.physioar.engine.geometry.pixel
import com.physioar.engine.geometry.worldPoint
import com.physioar.engine.pose.BodySide
import com.physioar.engine.pose.PoseFrame
import com.physioar.engine.quality.TrackingQualityGate
import com.physioar.engine.quality.TrackingStatus
import com.physioar.engine.signal.OneEuroFilter
import com.physioar.engine.signal.WindowedVelocity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Movement analysis for seated knee extension (side view).
 *
 * Per frame:
 *  1. Tracking gate on the exercised side's shoulder, hip, knee and ankle.
 *  2. Side-view check (shoulder width / torso length) and optional 2D-vs-3D cross-check.
 *  3. Knee angle (hip–knee–ankle) and trunk inclination in isotropic pixel space.
 *  4. One Euro smoothing.
 *  5. Finite-state machine SETUP → REST → MOVING_TO_TARGET ⇄ HOLDING → RETURNING → REST.
 *  6. Per-repetition accumulation of peak, hold time, velocity, return time,
 *     compensation and untracked time.
 *  7. Classification of the finished repetition (see [classify]).
 *
 * All thresholds come from [KneeExtensionParameters] (the patient's prescription).
 */
class SeatedKneeExtensionAnalyzer(
    val params: KneeExtensionParameters,
    private val feedbackThrottle: FeedbackThrottle = FeedbackThrottle(),
) : ExerciseAnalyzer {

    override val definition: ExerciseDefinition = SeatedKneeExtension.definition

    private val side: BodySide = params.side
    private val required = SeatedKneeExtension.requiredLandmarks(side)
    private val oppositeRequired = SeatedKneeExtension.requiredLandmarks(side.opposite)

    // Smoothing. Tuned on synthetic data only; re-tune on recordings.
    private val kneeFilter = OneEuroFilter(minCutoffHz = 1.5, beta = 0.02, derivativeCutoffHz = 1.0)
    private val trunkFilter = OneEuroFilter(minCutoffHz = 1.0, beta = 0.01, derivativeCutoffHz = 1.0)
    private val hipYFilter = OneEuroFilter(minCutoffHz = 1.0, beta = 0.01, derivativeCutoffHz = 1.0)

    private var phase = MovementPhase.SETUP
    private var lastTimestampMs: Long? = null
    private var lastGoodTimestampMs: Long? = null
    private val kneeVelocity = WindowedVelocity(windowMs = 200)

    // Calibration / baselines.
    private var setupStableSinceMs: Long? = null
    private var baselineKnee: Double? = null
    private var baselineTrunk: Double? = null
    private var baselineHipY: Double? = null
    private var baselineThighLength: Double? = null
    private var restMinKnee: Double = Double.MAX_VALUE

    // Session bookkeeping.
    private var sessionStartMs: Long? = null
    private var sessionEndMs: Long? = null
    private var processedFrames = 0
    private var goodFrames = 0
    private var stoppedReason: FeedbackCode? = null
    private val reps = mutableListOf<RepetitionResult>()

    private var rep: RepAccumulator? = null

    private class RepAccumulator(val startMs: Long, val startAngle: Double) {
        var peak = startAngle
        var targetReached = false
        var holdMs = 0L
        var holdCompleteAnnounced = false
        var maxExtensionVelocity = 0.0
        var maxReturnVelocity = 0.0
        var maxTrunkDeviation = 0.0
        var maxHipLift = 0.0
        var untrackedMs = 0L
        val issues = linkedSetOf<FeedbackCode>()
    }

    override fun process(frame: PoseFrame?, timestampMs: Long): FrameAnalysis {
        val now = frame?.timestampMs ?: timestampMs
        val dtMs = lastTimestampMs?.let { (now - it).coerceAtLeast(0) } ?: 0L
        lastTimestampMs = now
        if (sessionStartMs == null) sessionStartMs = now
        processedFrames++

        if (phase == MovementPhase.STOPPED) {
            return output(now, TrackingStatus.GOOD, null, emptyMap(), emptyList(), null)
        }

        // 1. Tracking gate.
        val assessment = TrackingQualityGate.assess(frame, required, params.tracking)
        if (!assessment.isGood || frame == null) {
            val fb = mutableListOf<Feedback>()
            if (frame == null) {
                fb += Feedback(FeedbackCode.STEP_INTO_VIEW, "Step into the camera view.", Severity.GUIDANCE)
            } else if (TrackingQualityGate.assess(frame, oppositeRequired, params.tracking).isGood) {
                fb += turnSideFeedback()
            } else if (assessment.outOfFrame.isNotEmpty()) {
                fb += Feedback(FeedbackCode.MOVE_FULL_BODY_INTO_FRAME,
                    "Make sure your shoulder, hip, knee and ankle are all in view.", Severity.GUIDANCE)
            } else {
                fb += Feedback(FeedbackCode.TRACKING_LOST, "Tracking is unclear. Check lighting and camera position.",
                    Severity.GUIDANCE)
            }
            return untrackedFrame(now, dtMs, assessment.status, fb)
        }

        // 2. Side-view check.
        val sh = frame.pixel(side.shoulder())
        val hip = frame.pixel(side.hip())
        val knee = frame.pixel(side.knee())
        val ankle = frame.pixel(side.ankle())
        val shOpp = frame.pixel(side.opposite.shoulder())
        val hipOpp = frame.pixel(side.opposite.hip())
        val torsoLength = Vec2.midpoint(sh, shOpp).distanceTo(Vec2.midpoint(hip, hipOpp))
        if (torsoLength <= 1.0 || sh.distanceTo(shOpp) / torsoLength > params.maxFrontalViewRatio) {
            return untrackedFrame(now, dtMs, TrackingStatus.LOW_CONFIDENCE, listOf(turnSideFeedback()))
        }

        // 3. Raw measurements.
        val rawKnee = interiorAngleDeg(hip, knee, ankle)
        val rawTrunk = inclinationFromVerticalDeg(hip, sh)
        if (rawKnee == null || rawTrunk == null) {
            return untrackedFrame(now, dtMs, TrackingStatus.LOW_CONFIDENCE,
                listOf(Feedback(FeedbackCode.TRACKING_LOST, "Tracking is unclear.", Severity.GUIDANCE)))
        }
        val knee3d = worldKneeAngle(frame)
        val disagreement = params.maxImageWorldAngleDisagreementDeg
        if (knee3d != null && disagreement != null && abs(knee3d - rawKnee) > disagreement) {
            return untrackedFrame(now, dtMs, TrackingStatus.LOW_CONFIDENCE, listOf(turnSideFeedback()),
                extraAngles = mapOf("knee3d" to knee3d))
        }

        // 4. Smoothing (reset after a tracking gap so stale state does not leak in).
        val lastGood = lastGoodTimestampMs
        if (lastGood != null && now - lastGood > params.filterResetGapMs) {
            kneeFilter.reset(); trunkFilter.reset(); hipYFilter.reset(); kneeVelocity.reset()
        }
        lastGoodTimestampMs = now
        goodFrames++
        val kneeAngle = kneeFilter.filter(rawKnee, now)
        val trunk = trunkFilter.filter(rawTrunk, now)
        val hipY = hipYFilter.filter(hip.y, now)
        val thigh = hip.distanceTo(knee)

        val velocity = kneeVelocity.update(kneeAngle, now)

        val angles = buildMap {
            put("knee", kneeAngle)
            put("trunk", trunk)
            if (knee3d != null) put("knee3d", knee3d)
        }

        // 5–7. State machine.
        val feedback = mutableListOf<Feedback>()
        var completed: RepetitionResult? = null

        when (phase) {
            MovementPhase.SETUP -> {
                if (kneeAngle <= params.restMaxKneeAngleDeg) {
                    val since = setupStableSinceMs ?: now.also { setupStableSinceMs = it }
                    if (now - since >= params.calibrationHoldMs) {
                        baselineKnee = kneeAngle
                        baselineTrunk = trunk
                        baselineHipY = hipY
                        baselineThighLength = thigh
                        restMinKnee = kneeAngle
                        phase = MovementPhase.REST
                        feedback += Feedback(FeedbackCode.READY, "Ready. Slowly straighten your knee.", Severity.INFO)
                    } else {
                        feedback += Feedback(FeedbackCode.HOLD_STARTING_POSITION,
                            "Hold still in the starting position.", Severity.GUIDANCE)
                    }
                } else {
                    setupStableSinceMs = null
                    feedback += Feedback(FeedbackCode.HOLD_STARTING_POSITION,
                        "Sit with your knee bent and foot on the floor.", Severity.GUIDANCE)
                }
            }

            MovementPhase.REST -> {
                restMinKnee = min(restMinKnee, kneeAngle)
                // Slowly re-baseline posture between repetitions (patients shift in the chair).
                baselineTrunk = ema(baselineTrunk, trunk, 0.05)
                baselineHipY = ema(baselineHipY, hipY, 0.05)
                baselineThighLength = ema(baselineThighLength, thigh, 0.05)
                if (kneeAngle > params.restMaxKneeAngleDeg + params.startHysteresisDeg) {
                    rep = RepAccumulator(now, if (restMinKnee == Double.MAX_VALUE) kneeAngle else restMinKnee)
                    phase = MovementPhase.MOVING_TO_TARGET
                }
            }

            MovementPhase.MOVING_TO_TARGET -> {
                val r = rep!!
                accumulate(r, kneeAngle, trunk, hipY, feedback)
                r.maxExtensionVelocity = max(r.maxExtensionVelocity, velocity)
                if (velocity > params.maxExtensionVelocityDegPerSec && r.issues.add(FeedbackCode.AVOID_KICKING)) {
                    feedback += Feedback(FeedbackCode.AVOID_KICKING, "Lift smoothly, don't kick.", Severity.WARNING)
                }
                when {
                    kneeAngle >= params.targetKneeAngleDeg -> {
                        r.targetReached = true
                        phase = MovementPhase.HOLDING
                        if (params.holdDurationMs > 0) {
                            feedback += Feedback(FeedbackCode.HOLD_POSITION, "Hold it there.", Severity.INFO)
                        }
                    }
                    kneeAngle <= params.restMaxKneeAngleDeg -> completed = finishRep(now, feedback)
                    kneeAngle < r.peak - params.reversalDeg -> {
                        phase = MovementPhase.RETURNING
                        feedback += Feedback(FeedbackCode.STRAIGHTEN_KNEE_MORE,
                            "Try to straighten your knee a little more.", Severity.GUIDANCE)
                    }
                }
            }

            MovementPhase.HOLDING -> {
                val r = rep!!
                accumulate(r, kneeAngle, trunk, hipY, feedback)
                if (kneeAngle >= params.targetKneeAngleDeg - params.holdToleranceDeg) {
                    r.holdMs += dtMs
                    if (!r.holdCompleteAnnounced && r.holdMs >= params.holdDurationMs) {
                        r.holdCompleteAnnounced = true
                        feedback += Feedback(FeedbackCode.HOLD_COMPLETE, "Good. Now lower slowly.", Severity.INFO)
                    }
                } else {
                    phase = MovementPhase.RETURNING
                }
            }

            MovementPhase.RETURNING -> {
                val r = rep!!
                accumulate(r, kneeAngle, trunk, hipY, feedback)
                r.maxReturnVelocity = max(r.maxReturnVelocity, -velocity)
                if (r.targetReached && -velocity > params.maxReturnVelocityDegPerSec &&
                    r.issues.add(FeedbackCode.LOWER_SLOWLY)
                ) {
                    feedback += Feedback(FeedbackCode.LOWER_SLOWLY, "Lower your leg slowly.", Severity.WARNING)
                }
                when {
                    kneeAngle >= params.targetKneeAngleDeg -> {
                        // Re-extended: resume holding.
                        r.targetReached = true
                        phase = MovementPhase.HOLDING
                    }
                    kneeAngle <= params.restMaxKneeAngleDeg -> completed = finishRep(now, feedback)
                }
            }

            MovementPhase.STOPPED -> Unit
        }

        return output(now, TrackingStatus.GOOD, null, angles, feedback, completed)
    }

    private fun accumulate(
        r: RepAccumulator, kneeAngle: Double, trunk: Double, hipY: Double,
        feedback: MutableList<Feedback>,
    ) {
        r.peak = max(r.peak, kneeAngle)
        baselineTrunk?.let { base ->
            val dev = abs(trunk - base)
            r.maxTrunkDeviation = max(r.maxTrunkDeviation, dev)
            if (dev > params.trunkLeanToleranceDeg && r.issues.add(FeedbackCode.KEEP_BACK_STILL)) {
                feedback += Feedback(FeedbackCode.KEEP_BACK_STILL, "Keep your back still against the chair.",
                    Severity.WARNING)
            }
        }
        val baseY = baselineHipY
        val thigh = baselineThighLength
        if (baseY != null && thigh != null && thigh > 1.0) {
            val lift = (baseY - hipY) / thigh // image y grows downwards
            r.maxHipLift = max(r.maxHipLift, lift)
            if (lift > params.hipLiftToleranceThighFraction && r.issues.add(FeedbackCode.KEEP_HIPS_ON_CHAIR)) {
                feedback += Feedback(FeedbackCode.KEEP_HIPS_ON_CHAIR, "Keep your hips on the chair.", Severity.WARNING)
            }
        }
        if (phase == MovementPhase.HOLDING && params.holdDurationMs > 0 && !r.holdCompleteAnnounced) {
            // Throttled countdown cue.
            feedback += Feedback(FeedbackCode.HOLD_POSITION, "Hold…", Severity.INFO)
        }
    }

    private fun finishRep(now: Long, feedback: MutableList<Feedback>): RepetitionResult? {
        val r = rep ?: return null
        rep = null
        phase = MovementPhase.REST
        restMinKnee = Double.MAX_VALUE
        if (r.peak - params.restMaxKneeAngleDeg < params.minExcursionDeg) {
            return null // Too small to be an attempt; treat as noise / fidgeting.
        }
        if (!r.targetReached) r.issues += FeedbackCode.STRAIGHTEN_KNEE_MORE
        if (r.targetReached && r.holdMs < params.holdDurationMs) r.issues += FeedbackCode.HOLD_TOO_SHORT

        val duration = (now - r.startMs).coerceAtLeast(1)
        val untrackedFraction = r.untrackedMs.toDouble() / duration
        val verdict = classify(r, untrackedFraction)
        val result = RepetitionResult(
            index = reps.size + 1,
            verdict = verdict,
            startMs = r.startMs,
            endMs = now,
            peakAngleDeg = r.peak,
            startAngleDeg = r.startAngle,
            holdDurationMs = r.holdMs,
            issues = r.issues.toList(),
            untrackedFraction = untrackedFraction,
        )
        reps += result
        feedback += repFeedback(result)

        if (reps.size >= params.maxRepsPerSet) {
            stop(FeedbackCode.SET_COMPLETE)
            feedback += Feedback(FeedbackCode.SET_COMPLETE, "Set complete. Rest now.", Severity.INFO)
        }
        return result
    }

    /**
     * Verdict precedence: untrusted tracking first (we cannot judge what we could not see),
     * then compensation, then incorrect execution, then incomplete range/hold, else correct.
     */
    private fun classify(r: RepAccumulator, untrackedFraction: Double): MovementVerdict = when {
        untrackedFraction > params.maxUntrackedFraction -> MovementVerdict.UNCERTAIN_TRACKING
        FeedbackCode.KEEP_BACK_STILL in r.issues || FeedbackCode.KEEP_HIPS_ON_CHAIR in r.issues ->
            MovementVerdict.COMPENSATORY
        FeedbackCode.AVOID_KICKING in r.issues || FeedbackCode.LOWER_SLOWLY in r.issues -> MovementVerdict.INCORRECT
        FeedbackCode.STRAIGHTEN_KNEE_MORE in r.issues || FeedbackCode.HOLD_TOO_SHORT in r.issues ->
            MovementVerdict.INCOMPLETE
        else -> MovementVerdict.CORRECT
    }

    private fun repFeedback(r: RepetitionResult): Feedback = when (r.verdict) {
        MovementVerdict.CORRECT -> Feedback(FeedbackCode.GOOD_REPETITION, "Good repetition.", Severity.INFO)
        MovementVerdict.UNCERTAIN_TRACKING -> Feedback(FeedbackCode.TRACKING_LOST,
            "That repetition could not be tracked reliably and was not scored.", Severity.GUIDANCE)
        MovementVerdict.COMPENSATORY -> if (FeedbackCode.KEEP_BACK_STILL in r.issues) {
            Feedback(FeedbackCode.KEEP_BACK_STILL, "Keep your back still against the chair.", Severity.WARNING)
        } else {
            Feedback(FeedbackCode.KEEP_HIPS_ON_CHAIR, "Keep your hips on the chair.", Severity.WARNING)
        }
        MovementVerdict.INCORRECT -> if (FeedbackCode.LOWER_SLOWLY in r.issues) {
            Feedback(FeedbackCode.LOWER_SLOWLY, "Lower your leg slowly.", Severity.WARNING)
        } else {
            Feedback(FeedbackCode.AVOID_KICKING, "Lift smoothly, don't kick.", Severity.WARNING)
        }
        MovementVerdict.INCOMPLETE -> if (FeedbackCode.STRAIGHTEN_KNEE_MORE in r.issues) {
            Feedback(FeedbackCode.STRAIGHTEN_KNEE_MORE, "Try to straighten your knee a little more.", Severity.GUIDANCE)
        } else {
            Feedback(FeedbackCode.HOLD_TOO_SHORT, "Hold the straight position a little longer.", Severity.GUIDANCE)
        }
        MovementVerdict.UNABLE_TO_EVALUATE -> Feedback(FeedbackCode.TRACKING_LOST, "Unable to evaluate.", Severity.GUIDANCE)
    }

    private fun untrackedFrame(
        now: Long, dtMs: Long, status: TrackingStatus, feedback: List<Feedback>,
        extraAngles: Map<String, Double> = emptyMap(),
    ): FrameAnalysis {
        rep?.let { it.untrackedMs += dtMs }
        if (phase == MovementPhase.SETUP) setupStableSinceMs = null
        return output(now, status, MovementVerdict.UNABLE_TO_EVALUATE, extraAngles, feedback, null)
    }

    private fun output(
        now: Long, status: TrackingStatus, frameVerdict: MovementVerdict?, angles: Map<String, Double>,
        feedback: List<Feedback>, completed: RepetitionResult?,
    ): FrameAnalysis {
        val knee = angles["knee"]
        val base = baselineKnee
        val progress = if (knee != null && base != null) {
            ((knee - base) / (params.targetKneeAngleDeg - base)).coerceIn(0.0, 1.0)
        } else null
        val holdRemaining = rep?.takeIf { phase == MovementPhase.HOLDING && params.holdDurationMs > 0 }
            ?.let { (params.holdDurationMs - it.holdMs).coerceAtLeast(0) }
        return FrameAnalysis(
            timestampMs = now,
            phase = phase,
            tracking = status,
            frameVerdict = frameVerdict,
            angles = angles,
            repCount = reps.size,
            validRepCount = reps.count { it.verdict == MovementVerdict.CORRECT },
            progressToTarget = progress,
            holdRemainingMs = holdRemaining,
            feedback = feedback.distinctBy { it.code }.filter { feedbackThrottle.admit(it, now) },
            completedRepetition = completed,
        )
    }

    private fun turnSideFeedback(): Feedback {
        val s = if (side == BodySide.LEFT) "left" else "right"
        return Feedback(FeedbackCode.TURN_SIDE_TO_CAMERA, "Turn so your $s side faces the camera.", Severity.GUIDANCE)
    }

    private fun worldKneeAngle(frame: PoseFrame): Double? {
        val h = frame.worldPoint(side.hip()) ?: return null
        val k = frame.worldPoint(side.knee()) ?: return null
        val a = frame.worldPoint(side.ankle()) ?: return null
        return interiorAngleDeg(h, k, a)
    }

    private fun ema(prev: Double?, value: Double, alpha: Double) = prev?.let { it + alpha * (value - it) } ?: value

    override fun stop(reason: FeedbackCode) {
        if (phase == MovementPhase.STOPPED) return
        rep = null // An unfinished repetition is discarded, not scored.
        phase = MovementPhase.STOPPED
        stoppedReason = reason
        sessionEndMs = lastTimestampMs
    }

    override fun summary(): SessionSummary = SessionSummary(
        exerciseId = definition.id,
        startedAtMs = sessionStartMs,
        endedAtMs = sessionEndMs ?: lastTimestampMs,
        repetitions = reps.toList(),
        trackedFrameFraction = if (processedFrames == 0) 0.0 else goodFrames.toDouble() / processedFrames,
        stoppedReason = stoppedReason,
    )
}
