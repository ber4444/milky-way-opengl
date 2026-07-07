package de.hanno_rein.mw

data class Vec3(val x: Float, val y: Float, val z: Float)

sealed interface Wonder {
    val name: String
    val caption: String
}

data class PointWonder(
    override val name: String,
    val pos: Vec3,
    override val caption: String,
) : Wonder

data class ShellWonder(
    override val name: String,
    val radiusKpc: Float,
    override val caption: String,
) : Wonder

data class LobeWonder(
    override val name: String,
    val halfHeightKpc: Float,
    override val caption: String,
) : Wonder

object GalacticWonders {
    val all: List<Wonder> = listOf(
        PointWonder(
            "Sagittarius A*",
            Vec3(0f, 0f, 0f),
            "Sagittarius A*: a 4.3-million-solar-mass black hole at the galactic center — the mass everything else orbits."
        ),
        PointWonder(
            "Orion Nebula",
            Vec3(8.9f, -1.2f, -0.4f),
            "Orion Nebula (M42): a stellar nursery ~1.3 kpc away where stars are forming now — visible to the naked eye."
        ),
        PointWonder(
            "Omega Centauri",
            Vec3(3.6f, -4.6f, -1.4f),
            "Omega Centauri: ~10 million stars up to ~12 billion years old — likely the stripped core of a swallowed galaxy."
        ),
        ShellWonder(
            "Dark Matter Halo",
            radiusKpc = 40f,
            "Dark matter halo: an unseen sphere holding ~85% of the galaxy's mass — composition unknown."
        ),
        LobeWonder(
            "Fermi Bubbles",
            halfHeightKpc = 8f,
            "Fermi Bubbles: twin gamma-ray lobes ~8 kpc tall above and below the core — origin still debated."
        ),
    )

    fun names(): List<String> = all.map { it.name }

    fun find(name: String): Wonder? = all.firstOrNull { it.name == name }
}

class WonderSelection {
    var selectedWonderName: String? = null
        private set

    val selectedWonder: Wonder?
        get() = selectedWonderName?.let { GalacticWonders.find(it) }

    val selectedCaption: String?
        get() = selectedWonder?.caption

    fun toggle(name: String) {
        selectedWonderName = if (selectedWonderName == name) null else GalacticWonders.find(name)?.name
    }
}
