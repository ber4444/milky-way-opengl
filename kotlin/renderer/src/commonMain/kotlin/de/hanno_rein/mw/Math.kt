package de.hanno_rein.mw

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Minimal mat4 + quaternion math — a Kotlin port of `core/third_party/linmath.h`.
 * Column-major 4×4 matrices stored as FloatArray(16); quaternions as FloatArray(4)
 * = (x, y, z, w). Conventions match GLKit so the camera's behavior is identical
 * to the C++ core. No GL dependency; host-unit-testable.
 */
object Math {
    fun mat4Identity(r: FloatArray) {
        r.fill(0f); r[0] = 1f; r[5] = 1f; r[10] = 1f; r[15] = 1f
    }

    fun mat4Multiply(r: FloatArray, a: FloatArray, b: FloatArray) {
        val t = FloatArray(16)
        for (c in 0..3) for (row in 0..3) {
            t[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] +
                a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3]
        }
        t.copyInto(r)
    }

    fun mat4Scale(r: FloatArray, sx: Float, sy: Float, sz: Float) {
        mat4Identity(r); r[0] = sx; r[5] = sy; r[10] = sz
    }

    fun mat4Translation(r: FloatArray, tx: Float, ty: Float, tz: Float) {
        mat4Identity(r); r[12] = tx; r[13] = ty; r[14] = tz
    }

    /** Post-multiply: r = r × R(angle, axis). Matches GLKMatrix4Rotate. */
    fun mat4Rotate(r: FloatArray, angle: Float, ax: Float, ay: Float, az: Float) {
        val n = sqrt(ax * ax + ay * ay + az * az)
        if (n < 1e-7f) return
        val inv = 1f / n
        val x = ax * inv; val y = ay * inv; val z = az * inv
        val c = cos(angle); val s = sin(angle); val t = 1f - c
        val rot = FloatArray(16)
        rot[0] = t*x*x + c;     rot[4] = t*x*y - s*z;  rot[8]  = t*x*z + s*y;  rot[12] = 0f
        rot[1] = t*x*y + s*z;   rot[5] = t*y*y + c;    rot[9]  = t*y*z - s*x;  rot[13] = 0f
        rot[2] = t*x*z - s*y;   rot[6] = t*y*z + s*x;  rot[10] = t*z*z + c;    rot[14] = 0f
        rot[3] = 0f;            rot[7] = 0f;           rot[11] = 0f;           rot[15] = 1f
        val tmp = FloatArray(16); mat4Multiply(tmp, r, rot); tmp.copyInto(r)
    }

    fun mat4Perspective(r: FloatArray, fovy: Float, aspect: Float, nearZ: Float, farZ: Float) {
        val f = 1f / tan(fovy * 0.5f)
        r[0] = f / aspect; r[1] = 0f; r[2] = 0f; r[3] = 0f
        r[4] = 0f; r[5] = f; r[6] = 0f; r[7] = 0f
        r[8] = 0f; r[9] = 0f
        r[10] = (farZ + nearZ) / (nearZ - farZ); r[11] = -1f
        r[12] = 0f; r[13] = 0f
        r[14] = 2f * farZ * nearZ / (nearZ - farZ); r[15] = 0f
    }

    /** Gauss-Jordan inverse (column-major). false if singular. */
    fun mat4Invert(out: FloatArray, m: FloatArray): Boolean {
        val a = Array(4) { FloatArray(8) }
        for (row in 0..3) {
            for (c in 0..3) a[row][c] = m[c * 4 + row]
            for (c in 4..7) a[row][c] = if (c - 4 == row) 1f else 0f
        }
        for (col in 0..3) {
            var pivot = col; var best = kotlin.math.abs(a[col][col])
            for (row in (col + 1)..3) {
                val v = kotlin.math.abs(a[row][col]); if (v > best) { best = v; pivot = row }
            }
            if (best < 1e-12f) return false
            if (pivot != col) { val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp }
            val inv = 1f / a[col][col]
            for (k in 0..7) a[col][k] *= inv
            for (row in 0..3) {
                if (row == col) continue
                val f = a[row][col]; if (f == 0f) continue
                for (k in 0..7) a[row][k] -= f * a[col][k]
            }
        }
        for (r in 0..3) for (c in 0..3) out[c * 4 + r] = a[r][c + 4]
        return true
    }

    // ---- quaternion (x, y, z, w) ----
    fun quatIdentity(q: FloatArray) { q[0] = 0f; q[1] = 0f; q[2] = 0f; q[3] = 1f }

    fun quatFromAngleAxis(q: FloatArray, angle: Float, ax: Float, ay: Float, az: Float) {
        val n = sqrt(ax * ax + ay * ay + az * az)
        if (n < 1e-7f) { quatIdentity(q); return }
        val inv = 1f / n; val half = angle * 0.5f; val s = sin(half)
        q[0] = ax * inv * s; q[1] = ay * inv * s; q[2] = az * inv * s; q[3] = cos(half)
    }

    fun quatAxis(axis: FloatArray, q: FloatArray) {
        val s = 1f - q[3] * q[3]
        if (s < 1e-7f) { axis[0] = 1f; axis[1] = 0f; axis[2] = 0f; return }
        val inv = 1f / sqrt(s); axis[0] = q[0] * inv; axis[1] = q[1] * inv; axis[2] = q[2] * inv
    }

    fun quatAngle(q: FloatArray): Float {
        var w = q[3]; if (w > 1f) w = 1f else if (w < -1f) w = -1f
        return 2f * acos(w.toDouble()).toFloat()
    }

    /** r = a × b (Hamilton). Normalizes in place. */
    fun quatMultiply(r: FloatArray, a: FloatArray, b: FloatArray) {
        val ax = a[0]; val ay = a[1]; val az = a[2]; val aw = a[3]
        val bx = b[0]; val by = b[1]; val bz = b[2]; val bw = b[3]
        r[0] = aw * bx + ax * bw + ay * bz - az * by
        r[1] = aw * by - ax * bz + ay * bw + az * bx
        r[2] = aw * bz + ax * by - ay * bx + az * bw
        r[3] = aw * bw - ax * bx - ay * by - az * bz
        val n = sqrt(r[0]*r[0] + r[1]*r[1] + r[2]*r[2] + r[3]*r[3])
        if (n > 1e-12f) { val inv = 1f / n; r[0]*=inv; r[1]*=inv; r[2]*=inv; r[3]*=inv }
    }

    fun quatInvert(r: FloatArray, q: FloatArray) {
        r[0] = -q[0]; r[1] = -q[1]; r[2] = -q[2]; r[3] = q[3]
    }

    fun quatRotateVector(r: FloatArray, q: FloatArray, v: FloatArray) {
        val qx = q[0]; val qy = q[1]; val qz = q[2]; val qw = q[3]
        val tx = 2f * (qy * v[2] - qz * v[1])
        val ty = 2f * (qz * v[0] - qx * v[2])
        val tz = 2f * (qx * v[1] - qy * v[0])
        r[0] = v[0] + qw * tx + (qy * tz - qz * ty)
        r[1] = v[1] + qw * ty + (qz * tx - qx * tz)
        r[2] = v[2] + qw * tz + (qx * ty - qy * tx)
    }

    /** Apply q's rotation to mat4 r (post-multiply: r = r × R(q)). */
    fun mat4RotateWithQuaternion(r: FloatArray, q: FloatArray) {
        val axis = FloatArray(3); quatAxis(axis, q); val angle = quatAngle(q)
        if (angle != 0f) mat4Rotate(r, angle, axis[0], axis[1], axis[2])
    }

    /** r = M × v (directional, w=1). */
    fun mat4MultiplyVector3(r: FloatArray, m: FloatArray, v: FloatArray) {
        val x = v[0]; val y = v[1]; val z = v[2]
        r[0] = m[0]*x + m[4]*y + m[8]*z + m[12]
        r[1] = m[1]*x + m[5]*y + m[9]*z + m[13]
        r[2] = m[2]*x + m[6]*y + m[10]*z + m[14]
    }

    /** r = M × v with perspective divide (GLKMatrix4MultiplyAndProjectVector3). */
    fun mat4MultiplyAndProjectVector3(r: FloatArray, m: FloatArray, v: FloatArray) {
        val x = v[0]; val y = v[1]; val z = v[2]
        val rx = m[0]*x + m[4]*y + m[8]*z + m[12]
        val ry = m[1]*x + m[5]*y + m[9]*z + m[13]
        val rz = m[2]*x + m[6]*y + m[10]*z + m[14]
        val rw = m[3]*x + m[7]*y + m[11]*z + m[15]
        if (kotlin.math.abs(rw) < 1e-12f) { r[0] = rx; r[1] = ry; r[2] = rz; return }
        val inv = 1f / rw; r[0] = rx * inv; r[1] = ry * inv; r[2] = rz * inv
    }
}
