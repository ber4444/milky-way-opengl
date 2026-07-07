package de.hanno_rein.mw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GalacticWondersTest {

    @Test
    fun catalogMatchesRequestedWonders() {
        val wonders = GalacticWonders.all

        assertEquals(
            listOf("Sagittarius A*", "Orion Nebula", "Omega Centauri", "Dark Matter Halo", "Fermi Bubbles"),
            wonders.map { it.name }
        )

        val sagittarius = assertIs<PointWonder>(wonders[0])
        assertEquals(Vec3(0f, 0f, 0f), sagittarius.pos)
        assertEquals(
            "Sagittarius A*: a 4.3-million-solar-mass black hole at the galactic center — the mass everything else orbits.",
            sagittarius.caption
        )

        val orion = assertIs<PointWonder>(wonders[1])
        assertEquals(Vec3(8.9f, -1.2f, -0.4f), orion.pos)

        val omega = assertIs<PointWonder>(wonders[2])
        assertEquals(Vec3(3.6f, -4.6f, -1.4f), omega.pos)

        val halo = assertIs<ShellWonder>(wonders[3])
        assertEquals(40f, halo.radiusKpc)

        val bubbles = assertIs<LobeWonder>(wonders[4])
        assertEquals(8f, bubbles.halfHeightKpc)
    }

    @Test
    fun selectionTogglesByWonderName() {
        val selection = WonderSelection()

        selection.toggle("Sagittarius A*")
        assertEquals("Sagittarius A*", selection.selectedWonderName)

        selection.toggle("Orion Nebula")
        assertEquals("Orion Nebula", selection.selectedWonderName)
        assertEquals(GalacticWonders.all[1].caption, selection.selectedCaption)

        selection.toggle("Orion Nebula")
        assertNull(selection.selectedWonderName)
        assertNull(selection.selectedCaption)
    }
}
