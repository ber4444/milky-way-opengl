package de.hanno_rein.mw

/**
 * PNG decode result — RGBA bytes + native channel count. The premultiply logic
 * (the lesson from the C++ port) lives in [MilkyWayRenderer.uploadPng] in
 * commonMain; this just returns decoded bytes. Decode itself is platform-native
 * (Battle-tested Android Bitmap / iOS CGImage) behind an expect/actual — see
 * [decodePng]. This replaces stb_image; the renderer never touches a PNG library
 * directly.
 */
class PngResult(
    val rgba: ByteArray, val w: Int, val h: Int, val n: Int,
    /** True when the decoder already multiplied RGB by alpha (iOS CGBitmapContext
     *  only offers premultiplied output). uploadPng must not premultiply again —
     *  doing so squares the alpha factor and visibly darkens translucent pixels. */
    val premultiplied: Boolean = false,
)

/** Platform-native PNG decode → RGBA. expect: androidMain/iosMain provide actuals. */
expect fun decodePng(data: ByteArray): PngResult
