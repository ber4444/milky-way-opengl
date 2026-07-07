package de.hanno_rein.mw

import android.graphics.BitmapFactory

/** Android actual: PNG decode via android.graphics.BitmapFactory. */
actual fun decodePng(data: ByteArray): PngResult {
    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: error("PNG decode failed")
    val w = bmp.width; val h = bmp.height
    // Determine native channel count from the bitmap config.
    val n = when (bmp.config) {
        android.graphics.Bitmap.Config.ALPHA_8 -> 1
        android.graphics.Bitmap.Config.RGB_565 -> 3
        android.graphics.Bitmap.Config.ARGB_8888 -> 4
        else -> 4
    }
    val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val rgba = ByteArray(w * h * 4)
    for (i in pixels.indices) {
        val c = pixels[i]
        rgba[i*4]   = (c shr 16 and 0xFF).toByte() // R
        rgba[i*4+1] = (c shr 8  and 0xFF).toByte() // G
        rgba[i*4+2] = (c        and 0xFF).toByte() // B
        rgba[i*4+3] = (c ushr 24       ).toByte()  // A
    }
    bmp.recycle()
    return PngResult(rgba, w, h, n)
}
