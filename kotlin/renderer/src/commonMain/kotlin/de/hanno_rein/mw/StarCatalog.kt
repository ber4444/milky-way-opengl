package de.hanno_rein.mw

import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parses `milkyway0.binary` (10 floats/star) into a FloatArray and applies the
 * Gaia galactic warp as a load-time transform — a Kotlin port of the C++ core's
 * catalog handling. Validates the catalog (whole-record stride, no NaN/outlier
 * size) and bends the outer disk (R > 9 kpc) into the S-shape Gaia measured.
 * Zero per-frame cost; the transform runs once at parse.
 */
object StarCatalog {
    /** Parse + validate + warp. Returns (stars, count). Throws on bad stride. */
    fun load(catalogBytes: ByteArray): Pair<FloatArray, Int> {
        require(catalogBytes.size % MilkyWayConventions.STAR_STRIDE_BYTES == 0) {
            "catalog size ${catalogBytes.size} not a multiple of ${MilkyWayConventions.STAR_STRIDE_BYTES}"
        }
        val n = catalogBytes.size / MilkyWayConventions.STAR_STRIDE_BYTES
        // Decode little-endian floats (data class doesn't depend on host endianness
        // on arm64/x86_64 which are both LE; the catalog is LE on disk).
        val stars = FloatArray(n * MilkyWayConventions.FLOATS_PER_STAR)
        var bi = 0
        for (i in stars.indices) {
            val b0 = catalogBytes[bi++].toInt() and 0xFF
            val b1 = catalogBytes[bi++].toInt() and 0xFF
            val b2 = catalogBytes[bi++].toInt() and 0xFF
            val b3 = catalogBytes[bi++].toInt()
            val bits = (b0) or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            stars[i] = Float.fromBits(bits)
        }
        applyWarp(stars, n)
        validate(stars, n)
        return stars to n
    }

    /** Gaia warp: z += smoothstep(R) · amp · sin(φ − lineOfNodes) for R > start. */
    private fun applyWarp(stars: FloatArray, n: Int) {
        val rStart = MilkyWayConventions.WARP_START_RADIUS_KPC.toFloat()
        val rEnd = MilkyWayConventions.WARP_END_RADIUS_KPC.toFloat()
        val ampMax = MilkyWayConventions.WARP_MAX_AMPLITUDE_KPC.toFloat()
        val lon = MilkyWayConventions.WARP_LINE_OF_NODES_RAD
        val stride = MilkyWayConventions.FLOATS_PER_STAR
        for (i in 0 until n) {
            val o = i * stride
            val x = stars[o]; val y = stars[o + 1]
            val r = sqrt(x * x + y * y)
            if (r <= rStart) continue
            var t = (r - rStart) / (rEnd - rStart)
            if (t > 1f) t = 1f
            t = t * t * (3f - 2f * t) // smoothstep
            val phi = atan2(y, x)
            stars[o + 2] += ampMax * t * sin(phi - lon)
        }
    }

    private fun validate(stars: FloatArray, n: Int) {
        val stride = MilkyWayConventions.FLOATS_PER_STAR
        var worst = 0f
        for (i in 0 until n) {
            val o = i * stride
            val x = stars[o]; val y = stars[o + 1]; val z = stars[o + 2]; val sz = stars[o + 3]
            require(!floatIsNaN(x) && !floatIsNaN(y) && !floatIsNaN(z) && !floatIsNaN(sz)) {
                "star #$i has NaN"
            }
            require(kotlin.math.abs(x) < 1e4f && kotlin.math.abs(y) < 1e4f && kotlin.math.abs(z) < 1e4f) {
                "star #$i has absurd position"
            }
            require(sz in 0f..100f) { "star #$i has absurd pointSize $sz" }
            if (sz > worst) worst = sz
        }
    }

    private fun floatIsNaN(v: Float): Boolean = v != v
}
