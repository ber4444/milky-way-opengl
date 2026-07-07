package de.hanno_rein.mw

/**
 * The only seam between the renderer and the platform's resource system —
 * a Kotlin port of `core/src/AssetProvider.h`. The platform implements this
 * (Android: AAssetManager; iOS: NSBundle via Kotlin/Native) and hands it to
 * [MilkyWayRenderer.init]. "Name → bytes"; nothing live crosses the boundary.
 */
interface AssetProvider {
    fun loadBytes(name: String): ByteArray
    fun loadText(name: String): String = loadBytes(name).decodeToString()
}
