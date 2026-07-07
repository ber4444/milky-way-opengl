package de.hanno_rein.mw

/**
 * The single source of truth for every cross-file constant — a 1:1 port of
 * `core/src/MilkyWayConventions.h`. Asset names, catalog stride, render sizes,
 * camera tuning, decay constants, point-sizing, gesture phases. Nothing in the
 * renderer repeats these literals; everything pulls from here.
 */
object MilkyWayConventions {
    // ---- Star catalog layout (milkyway0.binary): 10 floats per star ----
    const val FLOATS_PER_STAR = 10
    const val STAR_STRIDE_BYTES = FLOATS_PER_STAR * 4
    const val STAR_OFF_POSITION = 0   // x, y, z
    const val STAR_OFF_POINT_SIZE = 3 // 1 float
    const val STAR_OFF_COLOR = 4      // r, g, b, a
    const val STAR_OFF_TEXCOORD = 8   // u, v

    // ---- Render constants ----
    const val DUST_FBO_SIZE = 256
    const val QUAD_VERTS = 4
    const val QUAD_FLOATS_PER_VERT = 5
    const val MILKY_WAY_SCALE_KPC = 45.0f

    // ---- Camera ----
    const val FOV_Y_RAD = 1.57079632679489661923f           // π/2 (90°)
    const val NEAR_FACTOR = 0.2f
    const val FAR_FACTOR = 4.0f
    const val INITIAL_USER_SCALE = 30.0f
    const val RADIANS_PER_VIEWPORT_WIDTH = 3.14159265358979323846f // π

    // ---- Momentum decay (ported verbatim from the 2012 app) ----
    const val PAN_DECAY_SECONDS = 1.0f
    const val PINCH_DECAY_SECONDS = 1.0f
    const val ROLL_DECAY_SECONDS = 1.0f
    const val PAN_SLOWDOWN_POWER = 2
    const val PINCH_SLOWDOWN_POWER = 4
    const val ROLL_SLOWDOWN_POWER = 2
    const val PINCH_VELOCITY_CLAMP = 7.0f
    const val PINCH_REDUCTION_FLOOR = 0.5f
    const val PINCH_REDUCTION_TIME_SCALE = 30.0f
    const val MAX_FRAME_DELTA_SECONDS = 0.1f

    // ---- Point sizing (factors live here, not in platform glue) ----
    const val POINT_SIZE_PREMULTIPLY_DENSITY = 2.0f
    const val POINT_SIZE_PREMULTIPLY_TABLET = 2.0f

    // ---- Galaxy astronomy (coordinate audit + Gaia warp + Sun marker) ----
    const val KPC_PER_MODEL_UNIT = 1.0
    val BAR_ANGLE_RAD = degToRad(140.0)
    const val WARP_START_RADIUS_KPC = 9.0
    const val WARP_END_RADIUS_KPC = 15.0
    const val WARP_MAX_AMPLITUDE_KPC = 1.0
    val WARP_LINE_OF_NODES_RAD = BAR_ANGLE_RAD
    const val SUN_GALACTOCENTRIC_RADIUS_KPC = 8.2
    val SUN_AZIMUTH_OFFSET_FROM_BAR_RAD = degToRad(30.0)
    const val HAB_ZONE_INNER_KPC = 6.5f
    const val HAB_ZONE_OUTER_KPC = 9.8f
    const val BAR_HALF_LENGTH_KPC = 5.0f

    // ---- Gesture phases (the only thing that crosses to platform glue) ----
    const val PHASE_BEGAN = 0
    const val PHASE_CHANGED = 1
    const val PHASE_ENDED = 2

    private fun degToRad(d: Double): Float = (d * 3.14159265358979323846 / 180.0).toFloat()
}
