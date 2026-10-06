package mg.ecoledimanche.presences.ui

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.data.Environnement
import mg.ecoledimanche.presences.data.instantLocal
import mg.ecoledimanche.presences.domain.PhaseSeance
import mg.ecoledimanche.presences.domain.StatutPresence
import mg.ecoledimanche.presences.ui.dimanche.DimancheViewModel
import mg.ecoledimanche.presences.ui.dimanche.ErreurPointage
import mg.ecoledimanche.presences.ui.dimanche.EtatEcranDimanche
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DimancheViewModelTest : TestAvecMain() {
    private val dimanche = LocalDate.parse("2026-10-11")

    /** Trois enfants inscrits le mardi 06/10 (suivi dès le 11/10), puis l'horloge est placée à l'instant voulu. */
    private suspend fun prepare(date: String, heure: Int, minute: Int = 0): Triple<Environnement, Long, Long> {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val sarah = env.inscrire("Rakoto", "Sarah")
        val david = env.inscrire("Rakoto", "David")
        env.inscrire("Rakoto", "Nathan")
        env.allerA(date, heure, minute)
        return Triple(env, sarah, david)
    }

    private fun TestScope.vm(env: Environnement): DimancheViewModel {
        val modele = DimancheViewModel(env.presences, env.horloge, flowOf(Unit))
        backgroundScope.launch { modele.etat.collect { } } // l'état n'est alimenté que tant qu'il est observé
        backgroundScope.launch { modele.panneau.collect { } }
        return modele
    }

    private fun DimancheViewModel.pret() = etat.value as EtatEcranDimanche.Pret

    @Test
    fun avant10h_laGrilleMontreLesEnfantsAdmissibles_enCours() = runTest(dispatcher) {
        val (env, _, _) = prepare("2026-10-11", 9, 59)
        val modele = vm(env)
        advanceUntilIdle()
        val pret = modele.pret()
        assertEquals(dimanche, pret.dimanche)
        assertEquals(PhaseSeance.EN_COURS, pret.phase)
        assertEquals(listOf("David", "Nathan", "Sarah"), pret.elements.map { it.enfant.prenom })
        assertTrue(pret.elements.all { it.statut == StatutPresence.NON_ENREGISTRE })
    }

    @Test
    fun duLundiAuSamedi_ledimancheParDefautEstLeProchain_enModeAVenir() = runTest(dispatcher) {
        val (env, _, _) = prepare("2026-10-07", 9)
        val modele = vm(env)
        advanceUntilIdle()
        assertEquals(dimanche, modele.pret().dimanche)
        assertEquals(PhaseSeance.A_VENIR, modele.pret().phase)
        assertTrue(modele.pret().estDimancheParDefaut)
    }

    @Test
    fun dimancheFutur_lePointageEstDesactive_etAucunePresenceNEstCreee() = runTest(dispatcher) {
        val (env, sarah, _) = prepare("2026-10-07", 9)
        val modele = vm(env)
        advanceUntilIdle()
        modele.ouvrirPanneau(sarah)
        assertNull(modele.panneau.value.enfantId) // le panneau ne s'ouvre pas
        modele.pointer(StatutPresence.PRESENT) // et un appel direct ne fait rien
        advanceUntilIdle()
        assertTrue(env.magasin.presences.isEmpty())
        assertTrue(env.magasin.seances.isEmpty())
    }

    @Test
    fun pointage_ferme_le_panneau_apresLEcritureReussie_et_metLaCarteAJour() = runTest(dispatcher) {
        val (env, sarah, _) = prepare("2026-10-11", 9, 0)
        val modele = vm(env)
        advanceUntilIdle()
        modele.ouvrirPanneau(sarah)
        assertEquals(sarah, modele.panneau.value.enfantId)
        modele.pointer(StatutPresence.PRESENT)
        assertTrue(modele.panneau.value.enregistrement) // en cours : les boutons seront désactivés
        advanceUntilIdle()
        assertNull(modele.panneau.value.enfantId)
        assertFalse(modele.panneau.value.enregistrement)
        assertEquals(StatutPresence.PRESENT, modele.pret().elements.single { it.enfant.id == sarah }.statut)
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(sarah to dimanche).statut)
    }

    @Test
    fun correctionApresCloture_estUneCorrection_etLaSeanceResteCloturee() = runTest(dispatcher) {
        val (env, sarah, _) = prepare("2026-10-11", 10, 30)
        val modele = vm(env)
        advanceUntilIdle()
        assertEquals(PhaseSeance.CLOTUREE, modele.pret().phase)
        assertEquals(StatutPresence.ABSENT, modele.pret().elements.single { it.enfant.id == sarah }.statut)
        modele.ouvrirPanneau(sarah)
        modele.pointer(StatutPresence.EN_RETARD)
        advanceUntilIdle()
        assertEquals(StatutPresence.EN_RETARD, modele.pret().elements.single { it.enfant.id == sarah }.statut)
        assertTrue(env.magasin.seances.getValue(dimanche).cloturee)
    }

    @Test
    fun refusMetier_garde_le_panneau_ouvert_avec_un_message() = runTest(dispatcher) {
        val (env, sarah, _) = prepare("2026-10-11", 9, 0)
        val modele = vm(env)
        advanceUntilIdle()
        modele.ouvrirPanneau(sarah)
        modele.pointer(StatutPresence.ABSENT) // interdit avant la clôture
        advanceUntilIdle()
        assertEquals(sarah, modele.panneau.value.enfantId)
        assertEquals(ErreurPointage.ABSENT_INTERDIT_AVANT_CLOTURE, modele.panneau.value.erreur)
        assertFalse(modele.panneau.value.enregistrement)
        assertTrue(env.magasin.presences.isEmpty())
    }

    @Test
    fun erreurDEcriture_estAffichee_et_aucunStatutNEstMontreCommeEnregistre() = runTest(dispatcher) {
        val (env, sarah, _) = prepare("2026-10-11", 10, 30)
        val modele = vm(env)
        advanceUntilIdle()
        env.magasin.echecSurMarquerCloturee = true // la clôture à matérialiser échoue au moment du clic
        env.magasin.seances.clear(); env.magasin.presences.clear()
        modele.ouvrirPanneau(sarah)
        modele.pointer(StatutPresence.PRESENT)
        advanceUntilIdle()
        assertEquals(ErreurPointage.ECRITURE, modele.panneau.value.erreur)
        assertFalse(modele.panneau.value.enregistrement)
        assertEquals(sarah, modele.panneau.value.enfantId)
        assertTrue(env.magasin.presences.isEmpty()) // rien d'enregistré : aucune fausse réussite
    }

    @Test
    fun selecteurDeDimanche_precedentSuivantEtBornes() = runTest(dispatcher) {
        val env = Environnement(instantLocal("2026-10-01", 14)) // suivi dès le 04/10
        env.inscrire("Rakoto", "Sarah")
        env.allerA("2026-10-18", 9, 0)
        val modele = vm(env)
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-10-18"), modele.pret().dimanche)
        assertTrue(modele.pret().peutReculer)
        modele.dimanchePrecedent(); advanceUntilIdle()
        assertEquals(dimanche, modele.pret().dimanche)
        modele.dimanchePrecedent(); advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-10-04"), modele.pret().dimanche)
        assertFalse(modele.pret().peutReculer) // jamais avant le premier suivi
        modele.dimancheSuivant(); advanceUntilIdle()
        assertEquals(dimanche, modele.pret().dimanche)
        modele.revenirAuDimancheParDefaut(); advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-10-18"), modele.pret().dimanche)
        assertEquals(LocalDate.parse("2026-10-18").plusWeeks(8), modele.pret().dimanchesProposes.first()) // plus récent d'abord
        assertEquals(LocalDate.parse("2026-10-04"), modele.pret().dimanchesProposes.last())
    }

    @Test
    fun rattrapageAvantAffichage_apres10h_lesNonPointesSontDejaAbsents() = runTest(dispatcher) {
        val (env, _, _) = prepare("2026-11-02", 8, 0) // lundi : les dimanches 11/10, 18/10, 25/10 et 01/11 sont échus
        val modele = vm(env)
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-11-08"), modele.pret().dimanche) // prochain dimanche, à venir
        assertEquals(12, env.magasin.presences.size)
        assertTrue(env.magasin.seances.values.all { it.cloturee })
    }
}
