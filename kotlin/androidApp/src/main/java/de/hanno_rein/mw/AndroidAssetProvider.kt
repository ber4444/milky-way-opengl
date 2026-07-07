package de.hanno_rein.mw

import android.content.res.AssetManager

/**
 * Android [AssetProvider] over [AssetManager]. Reads the shared assets bundled
 * under `assets/` — the KMP resources are merged into the Android assets by
 * Gradle's source-set layout.
 */
class AndroidAssetProvider(private val assets: AssetManager) : AssetProvider {
    override fun loadBytes(name: String): ByteArray = assets.open(name).use { it.readBytes() }
}
