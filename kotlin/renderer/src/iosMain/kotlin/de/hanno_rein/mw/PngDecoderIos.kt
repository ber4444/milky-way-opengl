@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.hanno_rein.mw

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

/** iOS actual: PNG decode via UIImage + CoreGraphics. */
actual fun decodePng(data: ByteArray): PngResult {
    val nsdata: NSData = data.usePinned { pinned ->
        NSData.create(pinned.addressOf(0) as kotlinx.cinterop.CPointer<ByteVar>?, data.size.toULong())!!
    }
    val image = UIImage(nsdata) ?: error("PNG decode failed")
    val cgImage = image.CGImage() ?: error("no CGImage")
    val w = CGImageGetWidth(cgImage).toInt()
    val h = CGImageGetHeight(cgImage).toInt()
    val rgba = ByteArray(w * h * 4)
    memScoped {
        val cs = CGColorSpaceCreateDeviceRGB()
        val ctx = rgba.usePinned {
            CGBitmapContextCreate(it.addressOf(0), w.toULong(), h.toULong(), 8u, (w * 4).toULong(), cs,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value)
        }
        CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)
        CGContextRelease(ctx); CGColorSpaceRelease(cs)
    }
    return PngResult(rgba, w, h, 4)
}
