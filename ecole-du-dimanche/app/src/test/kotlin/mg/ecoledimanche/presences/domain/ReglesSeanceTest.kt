package mg.ecoledimanche.presences.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglesSeanceTest {
    private val madagascar = ZoneId.of("Indian/Antananarivo")

    private fun instant(date: String, heure: Int, minute: Int, seconde: Int = 0, nano: Int = 0, zone: ZoneId = madagascar): Instant =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(heure, minute, seconde, nano)).atZone(zone).toInstant()

    private val dimanche = LocalDate.parse("2026-10-11")

    // --- Calendrier ---------------------------------------------------------------------------

    @Test
    fun le11Octobre2026EstUnDimanche_exempleDuCahierDesCharges() {
        assertEquals(DayOfWeek.SUNDAY, dimanche.dayOfWeek)
        assertTrue(Dimanches.estDimanche(dimanche))
        assertFalse(Dimanches.estDimanche(LocalDate.parse("2026-10-06")))
    }

    @Test
    fun dimancheParDefaut_dimancheDuJourMemeApresCloture_sinonLeProchain() {
        assertEquals(dimanche, Dimanches.parDefaut(dimanche))
        for (jour in 5..10) assertEquals(dimanche, Dimanches.parDefaut(LocalDate.of(2026, 10, jour)))
        assertEquals(LocalDate.parse("2026-10-18"), Dimanches.parDefaut(LocalDate.parse("2026-10-12")))
    }

    @Test
    fun dimanchesEntre_inclutLesBornesDimanche_etIgnoreLesAutresJours() {
        val liste = Dimanches.entre(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-25"))
        assertEquals(listOf("2026-10-04", "2026-10-11", "2026-10-18", "2026-10-25").map(LocalDate::parse), liste)
        assertEquals(listOf(dimanche), Dimanches.entre(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-14")))
        assertTrue(Dimanches.entre(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10")).isEmpty())
        assertTrue(Dimanches.entre(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-04")).isEmpty())
    }

    // --- Frontière 09 h 59 / 10 h 00 ----------------------------------------------------------

    @Test
    fun heureDeClotureParDefaut_10h00() {
        assertEquals(java.time.LocalTime.of(10, 0), ConfigurationSeance.HEURE_CLOTURE)
    }

    @Test
    fun frontiereExacte_09h59_estEnCours_10h00_estCloturee() {
        assertEquals(PhaseSeance.EN_COURS, RegleSeance.phase(dimanche, null, instant("2026-10-11", 9, 59, 59, 999_000_000), madagascar))
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, null, instant("2026-10-11", 10, 0), madagascar))
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, null, instant("2026-10-11", 10, 0, 0, 1), madagascar))
    }

    @Test
    fun dimancheFutur_estAVenir_meme_laVeilleAu23h59() {
        assertEquals(PhaseSeance.A_VENIR, RegleSeance.phase(dimanche, null, instant("2026-10-06", 12, 0), madagascar))
        assertEquals(PhaseSeance.A_VENIR, RegleSeance.phase(dimanche, null, instant("2026-10-10", 23, 59, 59), madagascar))
        assertEquals(PhaseSeance.EN_COURS, RegleSeance.phase(dimanche, null, instant("2026-10-11", 0, 0), madagascar))
    }

    @Test
    fun dimanchePasse_estCloture() {
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, null, instant("2026-10-14", 8, 0), madagascar))
    }

    @Test
    fun uneSeanceDejaCloturee_nEstJamaisRouverte_parUnRetourEnArriereDeLHorloge() {
        val seance = EtatSeance(cloturee = true, cloturePrevueAt = instant("2026-10-11", 10, 0))
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, seance, instant("2026-10-11", 8, 0), madagascar))
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, seance, instant("2026-10-05", 8, 0), madagascar))
    }

    @Test
    fun leFuseauFigeALaCreation_prevautSurLeFuseauActuel() {
        // Séance créée à Madagascar (UTC+3) : clôture prévue 07:00 UTC.
        val seance = EtatSeance(cloturee = false, cloturePrevueAt = instant("2026-10-11", 10, 0, zone = madagascar))
        val paris = ZoneId.of("Europe/Paris")
        // 08:30 UTC = 10:30 à Paris : sans fuseau figé, ce serait « clôturé » ; ici c'est après 07:00 UTC, donc clôturé.
        assertEquals(PhaseSeance.CLOTUREE, RegleSeance.phase(dimanche, seance, Instant.parse("2026-10-11T08:30:00Z"), paris))
        // 06:30 UTC = 08:30 à Paris, 09:30 à Madagascar : toujours en cours.
        assertEquals(PhaseSeance.EN_COURS, RegleSeance.phase(dimanche, seance, Instant.parse("2026-10-11T06:30:00Z"), paris))
    }

    @Test
    fun instantCloture_estDixHeuresDansLeFuseauDonne() {
        assertEquals(Instant.parse("2026-10-11T07:00:00Z"), RegleSeance.instantCloture(dimanche, madagascar))
        assertEquals(Instant.parse("2026-10-11T08:00:00Z"), RegleSeance.instantCloture(dimanche, ZoneId.of("Europe/Paris")))
    }

    // --- Début de suivi (section 10) ------------------------------------------------------------

    private fun debut(date: String, h: Int, m: Int, s: Int = 0) =
        RegleSuivi.dateDebutSuivi(instant(date, h, m, s), madagascar)

    @Test
    fun ajoutEnSemaine_debuteLeDimancheSuivant_exempleDuMardi() {
        assertEquals(dimanche, debut("2026-10-06", 14, 30)) // mardi 06/10/2026 -> dimanche 11/10/2026
        assertEquals(dimanche, debut("2026-10-05", 0, 0)) // lundi
        assertEquals(dimanche, debut("2026-10-10", 23, 59, 59)) // samedi
    }

    @Test
    fun ajoutLeDimancheAvant10h_debuteCeDimanche() {
        assertEquals(dimanche, debut("2026-10-11", 0, 0))
        assertEquals(dimanche, debut("2026-10-11", 9, 59, 59))
    }

    @Test
    fun ajoutLeDimancheAPartirDe10h_debuteLeDimancheSuivant() {
        assertEquals(LocalDate.parse("2026-10-18"), debut("2026-10-11", 10, 0))
        assertEquals(LocalDate.parse("2026-10-18"), debut("2026-10-11", 23, 59))
    }

    @Test
    fun ajoutUnDimancheOuLaSeanceEstDejaCloturee_debuteLeDimancheSuivant_memeSiLHorlogeRecule() {
        assertEquals(LocalDate.parse("2026-10-18"), RegleSuivi.dateDebutSuivi(dimanche, PhaseSeance.CLOTUREE))
        assertEquals(dimanche, RegleSuivi.dateDebutSuivi(dimanche, PhaseSeance.EN_COURS))
    }

    @Test
    fun admissibilite_bornesDeDebutEtDeFinIncluses() {
        val debut = LocalDate.parse("2026-10-11")
        val fin = LocalDate.parse("2026-10-25")
        assertFalse(RegleSuivi.estAdmissible(LocalDate.parse("2026-10-04"), debut, null))
        assertTrue(RegleSuivi.estAdmissible(debut, debut, null))
        assertTrue(RegleSuivi.estAdmissible(LocalDate.parse("2030-01-06"), debut, null))
        assertTrue(RegleSuivi.estAdmissible(LocalDate.parse("2026-10-25"), debut, fin))
        assertFalse(RegleSuivi.estAdmissible(LocalDate.parse("2026-11-01"), debut, fin))
    }

    @Test
    fun archivageAvantLePremierDimanche_neDonneAucunDimancheAdmissible() {
        // Ajouté le mardi 06/10 (début 11/10), archivé le jeudi 08/10 (fin 08/10).
        val debut = LocalDate.parse("2026-10-11")
        val fin = LocalDate.parse("2026-10-08")
        for (d in Dimanches.entre(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-31"))) {
            assertFalse("$d ne doit pas être admissible", RegleSuivi.estAdmissible(d, debut, fin))
        }
    }

    @Test
    fun archivageLeDimanche_conserveCeDimanche_puisExclutLesSuivants() {
        val debut = LocalDate.parse("2026-10-04")
        val fin = LocalDate.parse("2026-10-11") // archivé le dimanche 11/10
        assertTrue(RegleSuivi.estAdmissible(LocalDate.parse("2026-10-11"), debut, fin))
        assertFalse(RegleSuivi.estAdmissible(LocalDate.parse("2026-10-18"), debut, fin))
    }
}
