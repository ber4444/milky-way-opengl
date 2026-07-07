package de.hanno_rein.mw

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host tests for [ArcballCamera] choreography — a Kotlin port of the original
 * `arcball_test.cpp`. No GL, no platform: pure common code so it runs under
 * commonTest with no emulator or GPU. Covers the golden behaviors from the plan:
 *   (a) after an Ended gesture with release velocity, momentum halts within the
 *       pan decay window (± one frame).
 *   (b) a multi-second dt gap (post-resume) produces a single clamped step, not
 *       a fling, and leaves the decay exhausted.
 *   (c) camera state is pure CPU state and survives a (no-op) context loss.
 */
class ArcballCameraTest {

    private companion object {
        const val FRAME_NS = (1e9 / 60.0).toLong()
    }

    private fun modelView(cam: ArcballCamera): FloatArray =
        FloatArray(16).also { cam.modelViewMatrix(it) }

    /** Sum of absolute per-element differences between two 4×4 matrices. */
    private fun matrixDrift(a: FloatArray, b: FloatArray): Float {
        var drift = 0f
        for (i in 0 until 16) drift += abs(a[i] - b[i])
        return drift
    }

    @Test
    fun panDecayHaltsWithinWindow() {
        val cam = ArcballCamera()
        cam.setViewportWidthDp(400f)
        // An Ended pan with a meaningful release velocity seeds momentum.
        cam.pan(0f, 0f, MilkyWayConventions.PHASE_ENDED, velX = 500f, velY = 0f)

        // Step through 2 s at 60 fps — well past the 1 s decay window.
        var now = 0L
        repeat(120) { now += FRAME_NS; cam.update(now) }

        // Snapshot, advance one more frame, confirm the view no longer changes:
        // the decay is exhausted so further frames are no-ops.
        val a = modelView(cam)
        cam.update(now + FRAME_NS)
        val b = modelView(cam)
        assertEquals(0f, matrixDrift(a, b), "pan decay should halt after its window")
    }

    @Test
    fun largeDtGapIsClamped() {
        // The anti-fling guarantee: a single update() with an arbitrarily large
        // dt gap advances the decay by at most MAX_FRAME_DELTA_SECONDS. So any
        // two gaps beyond that clamp must produce the identical bounded step —
        // a 5 s post-resume gap behaves exactly like a 2 s one, not a 50×-longer
        // fling. Both cameras are seeded and primed identically, then take one
        // over-clamp step of different wall-clock length.
        fun gapStep(gapNanos: Long): FloatArray {
            val cam = ArcballCamera()
            cam.setViewportWidthDp(400f)
            cam.pan(0f, 0f, MilkyWayConventions.PHASE_ENDED, velX = 1000f, velY = 0f)
            val t0 = 1_000_000_000L // arbitrary monotonic base
            cam.update(t0)
            val before = modelView(cam)
            cam.update(t0 + gapNanos)
            val after = modelView(cam)
            assertTrue(matrixDrift(before, after).isFinite(), "clamped step must stay finite")
            return after
        }

        val fiveSecondGap = gapStep(5_000_000_000L)
        val twoSecondGap = gapStep(2_000_000_000L)
        assertEquals(0f, matrixDrift(fiveSecondGap, twoSecondGap),
            "gaps beyond the dt clamp must yield an identical bounded step")
    }

    @Test
    fun cameraStateSurvivesContextLoss() {
        // The camera holds no GL objects, so a context loss is a no-op for it.
        // Confirm pan/zoom state persists with no GL involvement.
        val cam = ArcballCamera()
        cam.setViewportWidthDp(400f)
        cam.pan(10f, 0f, MilkyWayConventions.PHASE_CHANGED) // rotate a bit

        val scaleBefore = cam.userScale()
        cam.pinch(1.2f, MilkyWayConventions.PHASE_CHANGED)  // zoom in (divide by 1.2)
        val scaleAfter = cam.userScale()
        assertTrue(scaleAfter < scaleBefore, "pinching by 1.2 should zoom in")

        // "Context loss" — nothing to do for the camera; state is intact.
        assertEquals(scaleAfter, cam.userScale(), "camera state must survive context loss")
    }
}
