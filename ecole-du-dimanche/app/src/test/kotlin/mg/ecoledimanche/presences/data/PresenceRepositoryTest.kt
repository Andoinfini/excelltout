package mg.ecoledimanche.presences.data

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.data.repository.MotifRefus
import mg.ecoledimanche.presences.data.repository.ResultatPointage
import mg.ecoledimanche.presences.domain.CompteursAssiduite
import mg.ecoledimanche.presences.domain.FormatTaux
import mg.ecoledimanche.presences.domain.PhaseSeance
import mg.ecoledimanche.presences.domain.StatutPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceRepositoryTest {
    private val dimanche = LocalDate.parse("2026-10-11")

    private fun enregistre(statut: StatutPresence) = ResultatPointage.Enregistre(statut)

    /** Trois enfants inscrits le mardi 06/10/2026 : leur suivi commence le dimanche 11/10/2026. */
    private suspend fun troisEnfants(env: Environnement): Triple<Long, Long, Long> {
        env.allerA("2026-10-06", 14, 0)
        return Triple(env.inscrire("Rakoto", "Sarah"), env.inscrire("Rakoto", "David"), env.inscrire("Rakoto", "Nathan"))
    }

    private suspend fun statutAffiche(env: Environnement, id: Long): StatutPresence =
        env.presences.observerDimanche(dimanche).first().elements.single { it.enfant.id == id }.statut

    private suspend fun compteurs(env: Environnement, id: Long, archives: Boolean = false): CompteursAssiduite =
        env.presences.observerAssiduite(archives).first().single { it.enfant.id == id }.compteurs

    // ----------------------------------------------------------------------------------------
    // Scénario principal du cahier des charges (section 24)
    // ----------------------------------------------------------------------------------------

    @Test
    fun scenarioPrincipal_Sarah_David_Nathan() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, nathan) = troisEnfants(env)

        // Dimanche 11/10, avant 10 h : pointage de Sarah et David, rien pour Nathan.
        env.allerA("2026-10-11", 8, 30)
        env.presences.rattraper()
        assertEquals(enregistre(StatutPresence.PRESENT), env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT))
        assertEquals(enregistre(StatutPresence.EN_RETARD), env.presences.pointer(david, dimanche, StatutPresence.EN_RETARD))

        // 09 h 59 : Nathan est toujours NON_ENREGISTRE, la séance est encore ouverte.
        env.allerA("2026-10-11", 9, 59, 59)
        assertEquals(0, env.presences.rattraper())
        val avant = env.presences.observerDimanche(dimanche).first()
        assertEquals(PhaseSeance.EN_COURS, avant.phase)
        assertEquals(StatutPresence.PRESENT, statutAffiche(env, sarah))
        assertEquals(StatutPresence.EN_RETARD, statutAffiche(env, david))
        assertEquals(StatutPresence.NON_ENREGISTRE, statutAffiche(env, nathan))
        // Séance ouverte : exclue des statistiques.
        assertEquals(CompteursAssiduite.VIDE, compteurs(env, sarah))
        assertNull(compteurs(env, sarah).tauxDixiemes)

        // 10 h 00 : clôture.
        env.allerA("2026-10-11", 10, 0)
        assertEquals(1, env.presences.rattraper())
        assertEquals(PhaseSeance.CLOTUREE, env.presences.observerDimanche(dimanche).first().phase)
        assertEquals(StatutPresence.PRESENT, statutAffiche(env, sarah))
        assertEquals(StatutPresence.EN_RETARD, statutAffiche(env, david))
        assertEquals(StatutPresence.ABSENT, statutAffiche(env, nathan))

        // Statistiques de cette seule séance clôturée.
        assertEquals(CompteursAssiduite(1, 0, 0), compteurs(env, sarah))
        assertEquals("100 %", FormatTaux.format(compteurs(env, sarah).tauxDixiemes))
        assertEquals(CompteursAssiduite(0, 1, 0), compteurs(env, david))
        assertEquals("100 %", FormatTaux.format(compteurs(env, david).tauxDixiemes))
        assertEquals(CompteursAssiduite(0, 0, 1), compteurs(env, nathan))
        assertEquals("0 %", FormatTaux.format(compteurs(env, nathan).tauxDixiemes))
    }

    @Test
    fun uneSeanceOuverteNeComptePasDansLesStatistiques_memeAvecDesPresencesPointees() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        assertEquals(CompteursAssiduite.VIDE, compteurs(env, sarah))
        assertEquals("—", FormatTaux.format(compteurs(env, sarah).tauxDixiemes))
    }

    // ----------------------------------------------------------------------------------------
    // Frontière, séance à venir, ajout
    // ----------------------------------------------------------------------------------------

    @Test
    fun avant10h_rattrapageNeCloturePasLeDimancheDuJour_etNeCreeAucuneAbsence() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        troisEnfants(env)
        env.allerA("2026-10-11", 9, 59, 59)
        assertEquals(0, env.presences.rattraper())
        assertTrue(env.magasin.presences.isEmpty())
        assertTrue(env.magasin.seances.isEmpty())
    }

    @Test
    fun uneSeanceAVenir_refuseLePointage_etNeCreeRien() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-07", 9, 0) // mercredi : le dimanche 11/10 est à venir
        assertEquals(PhaseSeance.A_VENIR, env.presences.observerDimanche(dimanche).first().phase)
        assertEquals(3, env.presences.observerDimanche(dimanche).first().elements.size) // enfants prévus visibles
        assertEquals(ResultatPointage.Refuse(MotifRefus.SEANCE_A_VENIR), env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT))
        assertEquals(0, env.presences.rattraper())
        assertTrue(env.magasin.presences.isEmpty())
        assertTrue(env.magasin.seances.isEmpty())
    }

    @Test
    fun ajoutEnSemaine_leMardi_debutLeDimancheSuivant_etAucuneAbsenceAvant() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        assertEquals(dimanche, env.magasin.enfants.getValue(id).dateDebutSuivi)

        env.allerA("2026-10-14", 9, 0) // le dimanche 04/10 et ceux d'avant ne doivent pas exister
        env.presences.rattraper()
        assertEquals(setOf(dimanche), env.magasin.seances.keys)
        assertNull(env.magasin.presences[id to LocalDate.parse("2026-10-04")])
    }

    @Test
    fun ajoutLeDimancheAvant10h_apparaitDansLaGrilleEnCours_NON_ENREGISTRE() = runTest {
        val env = Environnement(instantLocal("2026-10-11", 8, 0))
        val id = env.inscrire("Rakoto", "Sarah")
        assertEquals(dimanche, env.magasin.enfants.getValue(id).dateDebutSuivi)
        val etat = env.presences.observerDimanche(dimanche).first()
        assertEquals(PhaseSeance.EN_COURS, etat.phase)
        assertEquals(StatutPresence.NON_ENREGISTRE, etat.elements.single().statut)
    }

    @Test
    fun ajoutLeDimancheAPartirDe10h_debuteLeDimancheSuivant_etNEstPasDansLaGrilleDuJour() = runTest {
        val env = Environnement(instantLocal("2026-10-11", 10, 0))
        val id = env.inscrire("Rakoto", "Sarah")
        assertEquals(LocalDate.parse("2026-10-18"), env.magasin.enfants.getValue(id).dateDebutSuivi)
        assertTrue(env.presences.observerDimanche(dimanche).first().elements.isEmpty())
        assertEquals(1, env.presences.observerDimanche(LocalDate.parse("2026-10-18")).first().elements.size)
        env.presences.rattraper()
        assertTrue(env.magasin.presences.isEmpty()) // aucune absence antidatée
    }

    @Test
    fun ajoutUnDimancheOuLaSeanceEstDejaCloturee_debuteLeSuivant_memeSiLHorlogeRecule() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        troisEnfants(env)
        env.allerA("2026-10-11", 10, 30)
        env.presences.rattraper() // dimanche 11/10 clôturé
        env.allerA("2026-10-11", 8, 0) // l'horloge recule avant 10 h
        val id = env.inscrire("Nouvel", "Enfant")
        assertEquals(LocalDate.parse("2026-10-18"), env.magasin.enfants.getValue(id).dateDebutSuivi)
    }

    // ----------------------------------------------------------------------------------------
    // Rattrapage de plusieurs dimanches
    // ----------------------------------------------------------------------------------------

    @Test
    fun rattrapageDeTroisDimanches_sansOuvertureDeLApplication_etSansSeanceExistante() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, nathan) = troisEnfants(env)

        // L'application n'est pas ouverte pendant trois semaines : aucune séance n'existe en base.
        assertTrue(env.magasin.seances.isEmpty())
        env.allerA("2026-11-01", 15, 0) // dimanche 01/11 après-midi : 11/10, 18/10, 25/10 et 01/11 sont échus

        assertEquals(4, env.presences.rattraper())
        val dimanches = listOf("2026-10-11", "2026-10-18", "2026-10-25", "2026-11-01").map(LocalDate::parse)
        assertEquals(dimanches, env.magasin.seances.keys.sorted())
        assertTrue(env.magasin.seances.values.all { it.cloturee && it.clotureeAt != null })
        for (id in listOf(sarah, david, nathan)) {
            assertEquals(CompteursAssiduite(0, 0, 4), compteurs(env, id))
        }
        assertEquals(12, env.magasin.presences.size)
        assertTrue(env.magasin.presences.values.all { it.statut == StatutPresence.ABSENT })
    }

    @Test
    fun rattrapage_statutsEtAbsencesSurPlusieursDimanches() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, nathan) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        env.presences.pointer(david, dimanche, StatutPresence.EN_RETARD)
        env.presences.pointer(nathan, dimanche, StatutPresence.PRESENT)
        env.presences.pointer(nathan, dimanche, StatutPresence.NON_ENREGISTRE) // annulation avant clôture

        env.allerA("2026-10-19", 8, 0) // rouverte le lundi : 11/10 et 18/10 échus
        assertEquals(2, env.presences.rattraper())
        assertEquals(CompteursAssiduite(1, 0, 1), compteurs(env, sarah))
        assertEquals(CompteursAssiduite(0, 1, 1), compteurs(env, david))
        assertEquals(CompteursAssiduite(0, 0, 2), compteurs(env, nathan))
    }

    @Test
    fun rattrapageRepete_estIdempotent_sansDoublonNiEcrasement() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        env.presences.pointer(david, dimanche, StatutPresence.EN_RETARD)
        env.allerA("2026-10-20", 8, 0) // mardi : les dimanches 11/10 et 18/10 sont échus

        assertEquals(2, env.presences.rattraper())
        val instantane = env.magasin.presences.toMap()
        val seances = env.magasin.seances.toMap()
        repeat(5) { assertEquals(0, env.presences.rattraper()) }
        assertEquals(instantane, env.magasin.presences.toMap()) // y compris updatedAt : rien n'a été retouché
        assertEquals(seances, env.magasin.seances.toMap())
        assertEquals(6, env.magasin.presences.size) // 2 dimanches x 3 enfants, sans doublon
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(sarah to dimanche).statut)
        assertEquals(StatutPresence.EN_RETARD, env.magasin.presences.getValue(david to dimanche).statut)
    }

    @Test
    fun uneCorrectionApresCloture_estConserveeParLesRattrapagesSuivants() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, nathan) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        env.allerA("2026-10-11", 11, 0)
        env.presences.rattraper()
        assertEquals(StatutPresence.ABSENT, statutAffiche(env, nathan))

        // Correction explicite après clôture : Nathan était en réalité présent, Sarah en retard.
        assertEquals(enregistre(StatutPresence.PRESENT), env.presences.pointer(nathan, dimanche, StatutPresence.PRESENT))
        assertEquals(enregistre(StatutPresence.EN_RETARD), env.presences.pointer(sarah, dimanche, StatutPresence.EN_RETARD))
        assertTrue(env.magasin.seances.getValue(dimanche).cloturee) // la séance reste clôturée

        env.allerA("2026-12-20", 9, 0)
        env.presences.rattraper()
        env.presences.rattraper()
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(nathan to dimanche).statut)
        assertEquals(StatutPresence.EN_RETARD, env.magasin.presences.getValue(sarah to dimanche).statut)
        // Historique et statistiques recalculés.
        assertEquals(1, compteurs(env, nathan).presences)
        assertEquals(1, compteurs(env, sarah).retards)
        assertEquals(0, compteurs(env, sarah).presences)
    }

    @Test
    fun horlogeQuiRecule_neRouvrePasUneSeanceCloturee() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (_, _, nathan) = troisEnfants(env)
        env.allerA("2026-10-11", 10, 5)
        env.presences.rattraper()
        env.allerA("2026-10-11", 6, 0) // l'horloge recule avant 10 h
        assertEquals(PhaseSeance.CLOTUREE, env.presences.observerDimanche(dimanche).first().phase)
        assertEquals(StatutPresence.ABSENT, statutAffiche(env, nathan))
        assertEquals(0, env.presences.rattraper())
        env.allerA("2026-10-01", 6, 0) // et même avant le dimanche
        assertEquals(PhaseSeance.CLOTUREE, env.presences.observerDimanche(dimanche).first().phase)
    }

    @Test
    fun changementDeFuseau_neDeplaceNiNeRouvreUneSeanceExistante() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT) // séance créée à Madagascar : clôture 07:00 UTC
        val echeance = env.magasin.seances.getValue(dimanche).cloturePrevueAt
        assertEquals(instantLocal("2026-10-11", 10, 0), echeance)
        assertEquals("Indian/Antananarivo", env.magasin.seances.getValue(dimanche).zoneId)

        // Le téléphone passe à l'heure de Paris : 06:30 UTC = 08:30 à Paris -> toujours ouverte.
        env.horloge.fuseau = java.time.ZoneId.of("Europe/Paris")
        env.horloge.maintenant = java.time.Instant.parse("2026-10-11T06:30:00Z")
        assertEquals(PhaseSeance.EN_COURS, env.presences.observerDimanche(dimanche).first().phase)
        assertEquals(0, env.presences.rattraper())
        // 07:00 UTC : échéance d'origine atteinte, même si c'est 09:00 à Paris.
        env.horloge.maintenant = java.time.Instant.parse("2026-10-11T07:00:00Z")
        assertEquals(1, env.presences.rattraper())
        assertEquals(echeance, env.magasin.seances.getValue(dimanche).cloturePrevueAt)
    }

    // ----------------------------------------------------------------------------------------
    // Pointage
    // ----------------------------------------------------------------------------------------

    @Test
    fun avantCloture_changementsEntrePresentEtEnRetard_etAnnulationDuPointage() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        assertEquals(StatutPresence.PRESENT, statutAffiche(env, sarah))
        env.presences.pointer(sarah, dimanche, StatutPresence.EN_RETARD)
        assertEquals(StatutPresence.EN_RETARD, statutAffiche(env, sarah))
        env.presences.pointer(sarah, dimanche, StatutPresence.NON_ENREGISTRE)
        assertEquals(StatutPresence.NON_ENREGISTRE, statutAffiche(env, sarah))
        assertEquals(1, env.magasin.presences.size)
    }

    @Test
    fun avantCloture_ABSENTEstRefuse() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        assertEquals(ResultatPointage.Refuse(MotifRefus.ABSENT_INTERDIT_AVANT_CLOTURE), env.presences.pointer(sarah, dimanche, StatutPresence.ABSENT))
        assertTrue(env.magasin.presences.isEmpty())
    }

    @Test
    fun apresCloture_correctionVersPresentEnRetardAbsent_maisJamaisNonEnregistre() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 10, 30)
        env.presences.rattraper()
        for (statut in listOf(StatutPresence.PRESENT, StatutPresence.EN_RETARD, StatutPresence.ABSENT)) {
            assertEquals(enregistre(statut), env.presences.pointer(sarah, dimanche, statut))
            assertEquals(statut, statutAffiche(env, sarah))
        }
        assertEquals(
            ResultatPointage.Refuse(MotifRefus.ANNULATION_INTERDITE_APRES_CLOTURE),
            env.presences.pointer(sarah, dimanche, StatutPresence.NON_ENREGISTRE),
        )
        assertEquals(StatutPresence.ABSENT, statutAffiche(env, sarah))
    }

    @Test
    fun cliqueApresLHeureDeCloture_surUnePanneauOuvertAvant10h_estUneCorrection_etCloture() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, nathan) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 59, 50)
        env.presences.pointer(david, dimanche, StatutPresence.PRESENT)
        env.allerA("2026-10-11", 10, 0, 5) // la clôture est passée avant l'enregistrement du clic
        assertEquals(enregistre(StatutPresence.EN_RETARD), env.presences.pointer(sarah, dimanche, StatutPresence.EN_RETARD))
        assertTrue(env.magasin.seances.getValue(dimanche).cloturee)
        assertEquals(StatutPresence.ABSENT, env.magasin.presences.getValue(nathan to dimanche).statut)
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(david to dimanche).statut)
        assertEquals(StatutPresence.EN_RETARD, env.magasin.presences.getValue(sarah to dimanche).statut)
    }

    @Test
    fun uneSeulePresenceParEnfantEtParDimanche_memeAvecDesClicsRapproches() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        val travaux = (1..50).map { i ->
            launch { env.presences.pointer(sarah, dimanche, if (i % 2 == 0) StatutPresence.PRESENT else StatutPresence.EN_RETARD) }
        }
        travaux.forEach { it.join() }
        assertEquals(1, env.magasin.presences.keys.count { it == sarah to dimanche })
        assertEquals(1, env.magasin.presences.size)
    }

    @Test
    fun pointage_dUnEnfantNonAdmissible_ouUnJourQuiNEstPasUnDimanche_estRefuse() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-18", 9, 0)
        // Le 04/10 est antérieur au début de suivi de Sarah.
        env.presences.rattraper()
        assertEquals(
            ResultatPointage.Refuse(MotifRefus.ENFANT_NON_ADMISSIBLE),
            env.presences.pointer(sarah, LocalDate.parse("2026-10-04"), StatutPresence.PRESENT),
        )
        assertEquals(
            ResultatPointage.Refuse(MotifRefus.PAS_UN_DIMANCHE),
            env.presences.pointer(sarah, LocalDate.parse("2026-10-14"), StatutPresence.PRESENT),
        )
        assertEquals(
            ResultatPointage.Refuse(MotifRefus.ENFANT_NON_ADMISSIBLE),
            env.presences.pointer(9999, LocalDate.parse("2026-10-18"), StatutPresence.PRESENT),
        )
        assertFalse(env.magasin.presences.keys.any { it.second == LocalDate.parse("2026-10-04") })
    }

    // ----------------------------------------------------------------------------------------
    // Atomicité de la clôture
    // ----------------------------------------------------------------------------------------

    @Test
    fun clotureAtomique_uneErreurEnCoursDeRouteNeLaisseAucuneEcritureEtLaReprendreFonctionne() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        troisEnfants(env)
        env.allerA("2026-10-11", 12, 0)

        env.magasin.echecSurMarquerCloturee = true
        try {
            env.presences.rattraper()
            throw AssertionError("l'échec simulé aurait dû remonter")
        } catch (e: IllegalStateException) {
            // attendu
        }
        // Rien n'est resté : ni séance, ni lignes ABSENT à moitié créées.
        assertTrue(env.magasin.seances.isEmpty())
        assertTrue(env.magasin.presences.isEmpty())

        env.magasin.echecSurMarquerCloturee = false
        assertEquals(1, env.presences.rattraper())
        assertEquals(3, env.magasin.presences.size)
    }

    // ----------------------------------------------------------------------------------------
    // Archivage
    // ----------------------------------------------------------------------------------------

    @Test
    fun archivage_conserveFicheEtHistorique_etExclutLesProchainsDimanches() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, david, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        env.presences.pointer(david, dimanche, StatutPresence.PRESENT)

        env.allerA("2026-10-13", 18, 0) // archivage le mardi 13/10
        env.presences.rattraper()
        assertTrue(env.enfants.archiver(sarah))
        val archivee = env.magasin.enfants.getValue(sarah)
        assertTrue(archivee.isArchived)
        assertEquals(LocalDate.parse("2026-10-13"), archivee.dateFinSuivi)
        assertNotNull(archivee.archivedAt)
        assertTrue(env.photos.definitives.contains(archivee.photoPath)) // la photo n'est pas supprimée
        assertFalse(env.enfants.archiver(sarah)) // déjà archivée

        env.allerA("2026-10-19", 8, 0) // le 18/10 est maintenant clôturé
        env.presences.rattraper()
        // Sarah : seulement le 11/10. David : 11/10 (présent) et 18/10 (absent).
        assertEquals(CompteursAssiduite(1, 0, 0), compteurs(env, sarah, archives = true))
        assertEquals(CompteursAssiduite(1, 0, 1), compteurs(env, david))
        assertNull(env.magasin.presences[sarah to LocalDate.parse("2026-10-18")])
        assertEquals(1, env.presences.observerDimanche(LocalDate.parse("2026-10-18")).first().elements.count { it.enfant.id == david })
        assertTrue(env.presences.observerDimanche(LocalDate.parse("2026-10-18")).first().elements.none { it.enfant.id == sarah })
        // Elle reste visible dans la séance à laquelle elle participait.
        assertTrue(env.presences.observerDimanche(dimanche).first().elements.any { it.enfant.id == sarah })
        // Ni les actifs ni les archives ne se mélangent.
        assertTrue(env.enfants.observerActifs().first().none { it.id == sarah })
        assertEquals(listOf(sarah), env.enfants.observerArchives().first().map { it.id })
        // Les présences passées n'ont pas été supprimées en cascade.
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(sarah to dimanche).statut)
    }

    @Test
    fun archivageLeDimanche_avant10h_conserveCeDimanche_puisExclutLeSuivant() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        assertTrue(env.enfants.archiver(sarah))
        assertEquals(dimanche, env.magasin.enfants.getValue(sarah).dateFinSuivi)
        assertTrue(env.presences.observerDimanche(dimanche).first().elements.any { it.enfant.id == sarah })
        env.allerA("2026-10-11", 10, 0)
        env.presences.rattraper()
        assertEquals(StatutPresence.ABSENT, env.magasin.presences.getValue(sarah to dimanche).statut) // dimanche conservé
        env.allerA("2026-10-19", 8, 0)
        env.presences.rattraper()
        assertNull(env.magasin.presences[sarah to LocalDate.parse("2026-10-18")])
    }

    @Test
    fun archivageAvantLePremierDimancheAdmissible_neCreeAucuneAbsence() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah") // début de suivi : 11/10
        env.allerA("2026-10-08", 9, 0)
        assertTrue(env.enfants.archiver(id))
        assertEquals(LocalDate.parse("2026-10-08"), env.magasin.enfants.getValue(id).dateFinSuivi)

        env.allerA("2026-11-30", 9, 0) // plusieurs semaines plus tard
        assertEquals(0, env.presences.rattraper())
        assertTrue(env.magasin.presences.isEmpty())
        assertTrue(env.magasin.seances.isEmpty()) // personne n'était admissible : aucune séance
        val historique = env.presences.observerHistorique(id).first()!!
        assertTrue(historique.elements.isEmpty())
        assertEquals(CompteursAssiduite.VIDE, historique.compteurs)
        assertEquals(CompteursAssiduite.VIDE, compteurs(env, id, archives = true))
    }

    // ----------------------------------------------------------------------------------------
    // Historique
    // ----------------------------------------------------------------------------------------

    @Test
    fun historique_dimanchesAdmissibles_duPlusRecentAuPlusAncien_sansDimancheFutur() = runTest {
        val env = Environnement(instantLocal("2026-10-01", 14)) // jeudi : début de suivi le 04/10
        val id = env.inscrire("Rakoto", "Sarah")
        assertEquals(LocalDate.parse("2026-10-04"), env.magasin.enfants.getValue(id).dateDebutSuivi)

        env.allerA("2026-10-04", 9, 0)
        env.presences.pointer(id, LocalDate.parse("2026-10-04"), StatutPresence.PRESENT)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(id, LocalDate.parse("2026-10-11"), StatutPresence.EN_RETARD)
        env.allerA("2026-10-18", 10, 5) // 18/10 clôturé sans pointage -> absent
        env.allerA("2026-10-25", 9, 0)
        env.presences.rattraper()
        // 25/10 est en cours, non pointé.
        val h = env.presences.observerHistorique(id).first()!!
        assertEquals(
            listOf("2026-10-25" to StatutPresence.NON_ENREGISTRE, "2026-10-18" to StatutPresence.ABSENT, "2026-10-11" to StatutPresence.EN_RETARD, "2026-10-04" to StatutPresence.PRESENT),
            h.elements.map { it.dimanche.toString() to it.statut },
        )
        // La séance en cours (25/10) ne compte pas dans les statistiques.
        assertEquals(CompteursAssiduite(1, 1, 1), h.compteurs)

        // Un mercredi : le dimanche suivant (01/11) n'est pas un élément d'historique.
        env.allerA("2026-10-28", 9, 0)
        env.presences.rattraper()
        val mercredi = env.presences.observerHistorique(id).first()!!
        assertEquals(4, mercredi.elements.size)
        assertEquals(StatutPresence.ABSENT, mercredi.elements.first().statut)
        assertEquals(CompteursAssiduite(1, 1, 2), mercredi.compteurs)
    }

    @Test
    fun historique_nIncluraJamaisUneSeanceOuverteDansLesCompteurs_memeAvecUneLignePointee() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (sarah, _, _) = troisEnfants(env)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(sarah, dimanche, StatutPresence.PRESENT) // une VRAIE ligne existe, séance ouverte
        assertEquals(1, env.magasin.presences.size)
        val ouvert = env.presences.observerHistorique(sarah).first()!!
        assertEquals(StatutPresence.PRESENT, ouvert.elements.single().statut) // visible dans la liste
        assertEquals(CompteursAssiduite.VIDE, ouvert.compteurs) // mais exclu des statistiques
        assertEquals(CompteursAssiduite.VIDE, compteurs(env, sarah))

        env.allerA("2026-10-11", 10, 0)
        env.presences.rattraper()
        assertEquals(CompteursAssiduite(1, 0, 0), env.presences.observerHistorique(sarah).first()!!.compteurs)
    }

    @Test
    fun historique_dunEnfantInexistant_estNul() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        assertNull(env.presences.observerHistorique(42).first())
    }

    @Test
    fun grilleDUnDimancheSansEnfantAdmissible_estVide() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        assertTrue(env.presences.observerDimanche(dimanche).first().elements.isEmpty())
        assertEquals(0, env.presences.rattraper())
    }

    @Test
    fun grilleDUneSeanceCloturee_neMontreJamaisNonEnregistre_memeAvantLEcriture() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val (_, _, nathan) = troisEnfants(env)
        env.allerA("2026-10-11", 10, 1) // clôture échue, pas encore matérialisée
        assertEquals(StatutPresence.ABSENT, statutAffiche(env, nathan))
    }

    @Test
    fun deuxEnfantsDeMemeNomEtPrenom_ontDesIdentifiantsDistincts() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val a = env.inscrire("Rakoto", "Sarah")
        val b = env.inscrire("Rakoto", "Sarah")
        assertTrue(a != b)
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(a, dimanche, StatutPresence.PRESENT)
        assertEquals(StatutPresence.NON_ENREGISTRE, statutAffiche(env, b))
    }
}
