package com.physioar.engine.exercises.kneeextension

import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.FrameAnalysis
import com.physioar.engine.exercise.MovementPhase
import com.physioar.engine.exercise.MovementVerdict
import com.physioar.engine.exercise.SessionSummary
import com.physioar.engine.pose.BodySide
import com.physioar.engine.pose.PoseFrame
import com.physioar.engine.testing.BodyState
import com.physioar.engine.testing.SyntheticSeatedPerson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests on SYNTHETIC landmark sequences. They verify the state machine and
 * classification logic, not clinical accuracy; that requires recorded, physiotherapist-
 * annotated video (docs/05-validation-plan.md).
 */
class SeatedKneeExtensionAnalyzerTest {

    private val rest = BodyState(90.0)
    private val params = KneeExtensionParameters(side = BodySide.RIGHT)

    private fun run(frames: List<PoseFrame>, p: KneeExtensionParameters = params): Pair<SessionSummary, List<FrameAnalysis>> {
        val analyzer = SeatedKneeExtensionAnalyzer(p)
        val out = frames.map { analyzer.process(it) }
        return analyzer.summary() to out
    }

    private fun person(noise: Double = 0.0, world: Boolean = false) =
        SyntheticSeatedPerson(kneeNoiseSdDeg = noise, withWorldLandmarks = world).hold(rest, 1500)

    @Test fun `three good repetitions are all correct`() {
        val (summary, out) = run(person().repetition().repetition().repetition().frames())
        assertEquals(3, summary.totalReps)
        assertEquals(3, summary.validReps)
        assertTrue(summary.repetitions.all { it.verdict == MovementVerdict.CORRECT }, summary.repetitions.toString())
        assertTrue(summary.repetitions.all { it.peakAngleDeg in 165.0..171.0 }, summary.repetitions.toString())
        assertTrue(summary.repetitions.all { it.holdDurationMs >= 2000 })
        assertEquals(MovementPhase.REST, out.last().phase)
        assertEquals(1.0, summary.trackedFrameFraction, 1e-9)
    }

    @Test fun `setup phase captures baseline before counting`() {
        val (_, out) = run(person().frames())
        assertEquals(MovementPhase.SETUP, out.first().phase)
        assertEquals(MovementPhase.REST, out.last().phase)
        assertTrue(out.flatMap { it.feedback }.any { it.code == FeedbackCode.READY })
    }

    @Test fun `partial range is incomplete`() {
        val (summary, _) = run(person().repetition(peak = 140.0).frames())
        assertEquals(1, summary.totalReps)
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.INCOMPLETE, r.verdict)
        assertTrue(FeedbackCode.STRAIGHTEN_KNEE_MORE in r.issues)
        assertEquals(0, summary.validReps)
    }

    @Test fun `short hold is incomplete`() {
        val (summary, _) = run(person().repetition(holdMs = 300).frames())
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.INCOMPLETE, r.verdict)
        assertEquals(listOf(FeedbackCode.HOLD_TOO_SHORT), r.issues)
    }

    @Test fun `dropping the leg is incorrect`() {
        val (summary, _) = run(person().repetition(returnMs = 200).frames())
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.INCORRECT, r.verdict)
        assertTrue(FeedbackCode.LOWER_SLOWLY in r.issues, r.issues.toString())
    }

    @Test fun `kicking the leg up is incorrect`() {
        val (summary, _) = run(person().repetition(extendMs = 150).frames())
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.INCORRECT, r.verdict)
        assertTrue(FeedbackCode.AVOID_KICKING in r.issues, r.issues.toString())
    }

    @Test fun `leaning back to lift the leg is compensatory`() {
        val p = person()
            .moveTo(BodyState(170.0, trunkLeanDeg = 25.0), 1500).hold(3000)
            .moveTo(rest, 2000).hold(1000)
        val (summary, out) = run(p.frames())
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.COMPENSATORY, r.verdict)
        assertTrue(FeedbackCode.KEEP_BACK_STILL in r.issues)
        assertTrue(out.flatMap { it.feedback }.any { it.code == FeedbackCode.KEEP_BACK_STILL }, "live warning expected")
    }

    @Test fun `lifting the hip is compensatory`() {
        val p = person()
            .moveTo(BodyState(170.0, hipLiftPx = 60.0), 1500).hold(3000)
            .moveTo(rest, 2000).hold(1000)
        val r = run(p.frames()).first.repetitions.single()
        assertEquals(MovementVerdict.COMPENSATORY, r.verdict)
        assertTrue(FeedbackCode.KEEP_HIPS_ON_CHAIR in r.issues)
    }

    @Test fun `lost tracking during a repetition makes it uncertain`() {
        val p = person()
            .moveTo(BodyState(170.0), 1500)
            .hold(BodyState(170.0, visibility = 0.1f), 2000)
            .hold(BodyState(170.0), 1000)
            .moveTo(rest, 2000).hold(1000)
        val (summary, out) = run(p.frames())
        val r = summary.repetitions.single()
        assertEquals(MovementVerdict.UNCERTAIN_TRACKING, r.verdict)
        assertTrue(r.untrackedFraction > 0.2)
        assertTrue(out.any { it.frameVerdict == MovementVerdict.UNABLE_TO_EVALUATE })
        assertEquals(0, summary.validReps)
    }

    @Test fun `frontal view cannot be evaluated and produces no repetitions`() {
        val p = SyntheticSeatedPerson().hold(BodyState(90.0, frontal = true), 1500)
            .moveTo(BodyState(170.0, frontal = true), 1500).hold(3000).moveTo(BodyState(90.0, frontal = true), 2000)
        val (summary, out) = run(p.frames())
        assertEquals(0, summary.totalReps)
        assertTrue(out.all { it.frameVerdict == MovementVerdict.UNABLE_TO_EVALUATE })
        assertTrue(out.flatMap { it.feedback }.any { it.code == FeedbackCode.TURN_SIDE_TO_CAMERA })
    }

    @Test fun `wrong side facing camera asks patient to turn`() {
        val (_, out) = run(person().frames(), params.copy(side = BodySide.LEFT))
        assertTrue(out.all { it.frameVerdict == MovementVerdict.UNABLE_TO_EVALUATE })
        assertEquals(FeedbackCode.TURN_SIDE_TO_CAMERA, out.first().feedback.single().code)
    }

    @Test fun `no person detected`() {
        val a = SeatedKneeExtensionAnalyzer(params).process(null, 0)
        assertEquals(MovementVerdict.UNABLE_TO_EVALUATE, a.frameVerdict)
        assertEquals(FeedbackCode.STEP_INTO_VIEW, a.feedback.single().code)
    }

    @Test fun `noise at rest does not create phantom repetitions`() {
        val frames = SyntheticSeatedPerson(kneeNoiseSdDeg = 3.0, pixelNoiseSd = 2.0).hold(rest, 15_000).frames()
        assertEquals(0, run(frames).first.totalReps)
    }

    @Test fun `noisy correct repetitions are still counted once each`() {
        val frames = SyntheticSeatedPerson(kneeNoiseSdDeg = 2.0, pixelNoiseSd = 1.5, seed = 7)
            .hold(rest, 1500).repetition().repetition().repetition().frames()
        val summary = run(frames).first
        assertEquals(3, summary.totalReps, summary.repetitions.toString())
        assertEquals(3, summary.validReps, summary.repetitions.toString())
    }

    @Test fun `small fidget below minimum excursion is ignored`() {
        val (summary, _) = run(person().repetition(peak = 125.0, holdMs = 500).frames())
        assertEquals(0, summary.totalReps)
    }

    @Test fun `thresholds come from the prescription not the code`() {
        val frames = person().repetition(peak = 150.0).frames()
        val lenient = run(frames, params.copy(targetKneeAngleDeg = 145.0)).first.repetitions.single()
        val strict = run(frames, params.copy(targetKneeAngleDeg = 165.0)).first.repetitions.single()
        assertEquals(MovementVerdict.CORRECT, lenient.verdict)
        assertEquals(MovementVerdict.INCOMPLETE, strict.verdict)
    }

    @Test fun `hold requirement can be disabled by prescription`() {
        val r = run(person().repetition(holdMs = 0).frames(), params.copy(holdDurationMs = 0)).first.repetitions.single()
        assertEquals(MovementVerdict.CORRECT, r.verdict)
    }

    @Test fun `set stops at prescribed maximum repetitions`() {
        val (summary, out) = run(person().repetition().repetition().repetition().frames(), params.copy(maxRepsPerSet = 2))
        assertEquals(2, summary.totalReps)
        assertEquals(FeedbackCode.SET_COMPLETE, summary.stoppedReason)
        assertEquals(MovementPhase.STOPPED, out.last().phase)
    }

    @Test fun `stopping mid repetition discards the unfinished repetition`() {
        val analyzer = SeatedKneeExtensionAnalyzer(params)
        val frames = person().repetition().moveTo(BodyState(170.0), 1500).frames()
        frames.forEach { analyzer.process(it) }
        analyzer.stop()
        val s = analyzer.summary()
        assertEquals(1, s.totalReps)
        assertEquals(FeedbackCode.STOPPED_BY_USER, s.stoppedReason)
    }

    @Test fun `consistent 3D world landmarks are accepted`() {
        val summary = run(person(world = true).repetition().frames()).first
        assertEquals(MovementVerdict.CORRECT, summary.repetitions.single().verdict)
    }

    @Test fun `rep reports range of motion from rest to peak`() {
        val r = run(person().repetition().frames()).first.repetitions.single()
        assertTrue(r.startAngleDeg in 88.0..93.0, "start=${r.startAngleDeg}")
        assertTrue(r.rangeOfMotionDeg in 72.0..83.0, "rom=${r.rangeOfMotionDeg}")
    }

    @Test fun `progress and hold countdown are reported`() {
        val out = run(person().repetition().frames()).second
        assertTrue(out.any { (it.progressToTarget ?: 0.0) >= 1.0 })
        assertTrue(out.any { it.phase == MovementPhase.HOLDING && it.holdRemainingMs != null })
    }

    @Test fun `feedback is throttled`() {
        val out = run(SyntheticSeatedPerson().hold(BodyState(90.0, frontal = true), 3000).frames()).second
        val turns = out.flatMap { it.feedback }.count { it.code == FeedbackCode.TURN_SIDE_TO_CAMERA }
        assertEquals(2, turns) // t = 0 and t >= 2500 ms
    }

    @Test fun `invalid prescription is rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            KneeExtensionParameters(BodySide.LEFT, targetKneeAngleDeg = 120.0, restMaxKneeAngleDeg = 115.0)
        }
    }
}
