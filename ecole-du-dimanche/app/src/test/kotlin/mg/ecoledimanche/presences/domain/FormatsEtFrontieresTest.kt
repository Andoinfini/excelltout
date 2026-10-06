package mg.ecoledimanche.presences.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsEtFrontieresTest {
    private val madagascar = ZoneId.of("Indian/Antananarivo")
    private fun instant(date: String, h: Int, m: Int = 0, s: Int = 0): Instant =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.of(h, m, s)).atZone(madagascar).toInstant()

    @Test
    fun dateLongue_exempleDuCahierDesCharges() {
        assertEquals("DIMANCHE 11 OCTOBRE 2026", FormatsFr.dateLongueMajuscules(LocalDate.parse("2026-10-11")))
        assertEquals("dimanche 4 octobre 2026", FormatsFr.dateLongue(LocalDate.parse("2026-10-04")))
    }

    @Test
    fun dateCourte_jjMmAaaa() {
        assertEquals("15/03/2017", FormatsFr.dateCourte(LocalDate.parse("2017-03-15")))
        assertEquals("06/10/2026", FormatsFr.dateCourte(LocalDate.parse("2026-10-06")))
    }

    @Test
    fun heure_sansMinutesInutiles() {
        assertEquals("10 h", FormatsFr.heure(LocalTime.of(10, 0)))
        assertEquals("9 h 05", FormatsFr.heure(LocalTime.of(9, 5)))
        assertEquals("10 h 30", FormatsFr.heure(LocalTime.of(10, 30)))
    }

    @Test
    fun prochaineCloture_avant10hLeDimanche_estAujourdhui_sinonLeDimancheSuivant() {
        assertEquals(instant("2026-10-11", 10), RegleSeance.prochaineCloture(instant("2026-10-11", 9, 59, 59), madagascar))
        assertEquals(instant("2026-10-18", 10), RegleSeance.prochaineCloture(instant("2026-10-11", 10, 0), madagascar))
        assertEquals(instant("2026-10-18", 10), RegleSeance.prochaineCloture(instant("2026-10-11", 15), madagascar))
        assertEquals(instant("2026-10-11", 10), RegleSeance.prochaineCloture(instant("2026-10-06", 14), madagascar))
        assertEquals(instant("2026-10-11", 10), RegleSeance.prochaineCloture(instant("2026-10-10", 23, 59), madagascar))
    }

    @Test
    fun frontiere_laCloture_estAtteinteAvantLaReactualisation() {
        // À 09:59:30 le dimanche : réveil juste après 10:00:00 (30 s + marge), pas 15 minutes plus tard.
        val maintenant = instant("2026-10-11", 9, 59, 30)
        val delai = Frontieres.delaiAvantProchaine(maintenant, madagascar)
        assertEquals(Duration.ofSeconds(30).plusMillis(250), delai)
        assertTrue(!maintenant.plus(delai).isBefore(instant("2026-10-11", 10)))
    }

    @Test
    fun frontiere_neDortJamaisPlusDeQuinzeMinutes_etSurveilleMinuit() {
        assertEquals(Duration.ofMinutes(15).plusMillis(250), Frontieres.delaiAvantProchaine(instant("2026-10-06", 14), madagascar))
        assertEquals(Duration.ofMinutes(5).plusMillis(250), Frontieres.delaiAvantProchaine(instant("2026-10-06", 23, 55), madagascar))
    }
}
