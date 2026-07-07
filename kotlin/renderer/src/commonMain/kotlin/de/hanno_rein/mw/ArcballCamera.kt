package de.hanno_rein.mw

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quaternion arcball camera with momentum decay — a Kotlin port of the C++
 * `ArcballCamera`. Owns ALL choreography (item 6 of the original plan): the
 * three gesture feeds capture release velocity, and [update] advances the decay
 * each frame from a monotonic timestamp. dt is derived internally and clamped
 * to [MilkyWayConventions.MAX_FRAME_DELTA_SECONDS] so a multi-second post-resume
 * gap can't fling the galaxy.
 *
 * No GL dependency → runs under commonTest with no emulator and no GPU.
 */
class ArcballCamera {
    private var radiansPerPixel = 0f
    private val q = FloatArray(4).also { Math.quatIdentity(it) }
    private var userScale = MilkyWayConventions.INITIAL_USER_SCALE

    // Per-gesture momentum state. *Time == decay window length → inactive.
    private var panDx = 0f; private var panDy = 0f; private var panTime = 1f
    private var pinchVel = 0f; private var pinchTime = 1f
    private var rollVel = 0f; private var rollTime = 1f
    private var lastNowNanos = 0L

    fun setViewportWidthDp(widthDp: Float) {
        radiansPerPixel = MilkyWayConventions.RADIANS_PER_VIEWPORT_WIDTH / widthDp
    }
    fun userScale(): Float = userScale
    fun setUserScale(s: Float) { userScale = s }

    fun pan(dxDp: Float, dyDp: Float, phase: Int, velX: Float = 0f, velY: Float = 0f) {
        when (phase) {
            MilkyWayConventions.PHASE_BEGAN -> panTime = 1f
            MilkyWayConventions.PHASE_CHANGED -> rotateWithVector(dxDp, -dyDp)
            MilkyWayConventions.PHASE_ENDED -> { panDx = velX; panDy = velY; panTime = 0f }
        }
    }

    fun pinch(scaleStep: Float, phase: Int, velocity: Float = 0f) {
        when (phase) {
            MilkyWayConventions.PHASE_BEGAN -> pinchTime = 1f
            MilkyWayConventions.PHASE_CHANGED -> if (scaleStep != 0f) userScale /= scaleStep
            MilkyWayConventions.PHASE_ENDED -> {
                pinchVel = velocity.coerceIn(-MilkyWayConventions.PINCH_VELOCITY_CLAMP,
                    MilkyWayConventions.PINCH_VELOCITY_CLAMP)
                pinchTime = 0f
            }
        }
    }

    fun roll(radians: Float, phase: Int, velocityRadS: Float = 0f) {
        when (phase) {
            MilkyWayConventions.PHASE_BEGAN -> rollTime = 1f
            MilkyWayConventions.PHASE_CHANGED -> {
                val d = FloatArray(4)
                Math.quatFromAngleAxis(d, radians, 0f, 0f, -1f)
                Math.quatMultiply(q, d, q)
            }
            MilkyWayConventions.PHASE_ENDED -> { rollVel = velocityRadS; rollTime = 0f }
        }
    }

    /** Advance momentum from a monotonic timestamp (ns). */
    fun update(nowNanos: Long) {
        val dt: Float = if (lastNowNanos == 0L) 1f / 60f
        else {
            var d = (nowNanos - lastNowNanos) * 1e-9f
            if (d < 0f) 1f / 60f else d.coerceAtMost(MilkyWayConventions.MAX_FRAME_DELTA_SECONDS)
        }
        lastNowNanos = nowNanos
        if (panTime < MilkyWayConventions.PAN_DECAY_SECONDS) {
            panTime += dt
            val s = slowdown(panTime / MilkyWayConventions.PAN_DECAY_SECONDS, MilkyWayConventions.PAN_SLOWDOWN_POWER)
            rotateWithVector(panDx * dt * s, -panDy * dt * s)
        }
        if (pinchTime < MilkyWayConventions.PINCH_DECAY_SECONDS) {
            pinchTime += dt
            val s = slowdown(pinchTime / MilkyWayConventions.PINCH_DECAY_SECONDS, MilkyWayConventions.PINCH_SLOWDOWN_POWER)
            var reduction = 1f + pinchVel * dt * s
            if (reduction < MilkyWayConventions.PINCH_REDUCTION_FLOOR) reduction = MilkyWayConventions.PINCH_REDUCTION_FLOOR
            userScale /= reduction.pow(dt * MilkyWayConventions.PINCH_REDUCTION_TIME_SCALE)
        }
        if (rollTime < MilkyWayConventions.ROLL_DECAY_SECONDS) {
            rollTime += dt
            val s = slowdown(rollTime / MilkyWayConventions.ROLL_DECAY_SECONDS, MilkyWayConventions.ROLL_SLOWDOWN_POWER)
            val d = FloatArray(4)
            Math.quatFromAngleAxis(d, rollVel * dt * s, 0f, 0f, -1f)
            Math.quatMultiply(q, d, q)
        }
    }

    fun modelViewMatrix(out: FloatArray) {
        Math.mat4Identity(out)
        Math.mat4RotateWithQuaternion(out, q)
    }

    fun projectionMatrix(out: FloatArray, width: Float, height: Float) {
        val aspect = kotlin.math.abs(width / height)
        Math.mat4Perspective(out, MilkyWayConventions.FOV_Y_RAD, aspect,
            MilkyWayConventions.NEAR_FACTOR * userScale, MilkyWayConventions.FAR_FACTOR * userScale)
        val t = FloatArray(16); Math.mat4Translation(t, 0f, 0f, -userScale)
        val tmp = FloatArray(16); Math.mat4Multiply(tmp, out, t)
        tmp.copyInto(out)
    }

    private fun rotateWithVector(dxDp: Float, dyDp: Float) {
        val up = floatArrayOf(0f, 1f, 0f); val right = floatArrayOf(-1f, 0f, 0f)
        val inv = FloatArray(4); Math.quatInvert(inv, q)
        Math.quatRotateVector(up, inv, up); Math.quatRotateVector(right, inv, right)
        val d = FloatArray(4)
        Math.quatFromAngleAxis(d, dxDp * radiansPerPixel, up[0], up[1], up[2]); Math.quatMultiply(q, q, d)
        Math.quatFromAngleAxis(d, dyDp * radiansPerPixel, right[0], right[1], right[2]); Math.quatMultiply(q, q, d)
    }

    private fun slowdown(t: Float, power: Int): Float {
        var s = (1f - t).coerceAtLeast(0f)
        repeat(power - 1) { s *= s }
        return s
    }
}
