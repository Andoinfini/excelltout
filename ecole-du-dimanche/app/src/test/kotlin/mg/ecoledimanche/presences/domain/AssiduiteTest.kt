package mg.ecoledimanche.presences.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssiduiteTest {
    private fun taux(p: Int, r: Int, a: Int) = FormatTaux.format(CompteursAssiduite(p, r, a).tauxDixiemes)

    @Test
    fun exemple_8Presences_1Retard_1Absence_90pourcent() {
        assertEquals("90 %", taux(8, 1, 1))
    }

    @Test
    fun exemple_32Presences_3Retards_5Absences_87virgule5pourcent() {
        assertEquals("87,5 %", taux(32, 3, 5))
    }

    @Test
    fun exemple_uniquementUnRetard_100pourcent() {
        assertEquals("100 %", taux(0, 1, 0))
    }

    @Test
    fun scenarioPrincipal_Sarah_David_Nathan() {
        assertEquals("100 %", taux(1, 0, 0)) // Sarah : présente
        assertEquals("100 %", taux(0, 1, 0)) // David : en retard
        assertEquals("0 %", taux(0, 0, 1)) // Nathan : absent
    }

    @Test
    fun sansSeanceCloturee_pasDeTauxEtPasDeDivisionParZero() {
        val vide = CompteursAssiduite(0, 0, 0)
        assertNull(vide.tauxDixiemes)
        assertEquals("—", FormatTaux.format(vide.tauxDixiemes))
        assertEquals(0, vide.total)
    }

    @Test
    fun uneSeuleDecimaleMaximum_etArrondiALaDemieSuperieure() {
        assertEquals("66,7 %", taux(2, 0, 1)) // 66,666…
        assertEquals("33,3 %", taux(1, 0, 2)) // 33,333…
        assertEquals("62,5 %", taux(5, 0, 3)) // 5/8 exact
        assertEquals("93,8 %", taux(15, 0, 1)) // 93,75 -> 93,8 (demi vers le haut)
        assertEquals("0 %", taux(0, 0, 7))
    }

    @Test
    fun pasDeDecimaleInutile() {
        assertEquals("50 %", taux(1, 0, 1))
        assertEquals("75 %", taux(2, 1, 1))
        assertEquals("100 %", taux(12, 3, 0))
    }

    @Test
    fun grandsNombres_sansDepassement() {
        assertEquals("99,9 %", taux(1_000_000, 0, 1_000))
    }
}
