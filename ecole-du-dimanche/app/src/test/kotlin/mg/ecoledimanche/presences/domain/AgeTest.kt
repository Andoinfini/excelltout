package mg.ecoledimanche.presences.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTest {
    private fun age(naissance: String, jour: String) = Age.enAnnees(LocalDate.parse(naissance), LocalDate.parse(jour))

    @Test
    fun exempleDuCahierDesCharges_9AnsLe6Octobre2026() {
        assertEquals(9, age("2017-03-15", "2026-10-06"))
    }

    @Test
    fun veilleDeLAnniversaire_puisLeJour_puisLeLendemain() {
        assertEquals(8, age("2017-03-15", "2026-03-14"))
        assertEquals(9, age("2017-03-15", "2026-03-15"))
        assertEquals(9, age("2017-03-15", "2026-03-16"))
    }

    @Test
    fun changementDAnnee_31Decembre_puis_1erJanvier() {
        assertEquals(4, age("2020-01-01", "2024-12-31"))
        assertEquals(5, age("2020-01-01", "2025-01-01"))
        assertEquals(4, age("2020-12-31", "2025-12-30"))
        assertEquals(5, age("2020-12-31", "2025-12-31"))
    }

    @Test
    fun naissanceLe29Fevrier_anneeBissextile() {
        // Anniversaire réel les années bissextiles.
        assertEquals(3, age("2016-02-29", "2020-02-28"))
        assertEquals(4, age("2016-02-29", "2020-02-29"))
        assertEquals(8, age("2016-02-29", "2024-02-29"))
    }

    @Test
    fun naissanceLe29Fevrier_anneeNonBissextile_anniversaireLe1erMars() {
        // Comportement de java.time.Period : le 28 février l'âge n'est pas encore atteint.
        assertEquals(4, age("2016-02-29", "2021-02-28"))
        assertEquals(5, age("2016-02-29", "2021-03-01"))
    }

    @Test
    fun nourrissonDeMoinsDUnAn_et_jourDeNaissance() {
        assertEquals(0, age("2026-10-06", "2026-10-06"))
        assertEquals(0, age("2026-03-15", "2026-10-06"))
    }

    @Test
    fun naissanceFuture_neDonneJamaisUnAgeNegatif() {
        assertEquals(0, age("2030-01-01", "2026-10-06"))
    }
}
