package mg.ecoledimanche.presences.ui

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.data.Environnement
import mg.ecoledimanche.presences.data.instantLocal
import mg.ecoledimanche.presences.domain.ChampEnfant
import mg.ecoledimanche.presences.domain.ErreurChamp
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.ui.formulaire.ErreurEnregistrement
import mg.ecoledimanche.presences.ui.formulaire.FormulaireViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormulaireViewModelTest : TestAvecMain() {
    private fun environnement() = Environnement(instantLocal("2026-10-06", 14))

    private fun ajout(env: Environnement, etat: SavedStateHandle = SavedStateHandle()) =
        FormulaireViewModel(env.enfants, env.photos, env.horloge, null, etat)

    /** Remplit les quatre champs obligatoires. */
    private fun FormulaireViewModel.remplirObligatoires() = modifierChamps {
        it.copy(nom = "  Rakoto ", prenom = "Sarah", dateNaissance = LocalDate.parse("2017-03-15"), sexe = Sexe.FILLE)
    }

    @Test
    fun ajoutSansPhoto_neCreeAucuneFiche() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        vm.remplirObligatoires()
        vm.enregistrer()
        advanceUntilIdle()
        assertEquals(ErreurEnregistrement.PHOTO_ABSENTE, vm.etat.value.erreurEnregistrement)
        assertTrue(env.magasin.enfants.isEmpty())
    }

    @Test
    fun champsInvalides_afficheLesErreursPresDesChamps_sansRienEcrire() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        vm.definirPhoto(env.photos.nouvelleTemporaire())
        vm.enregistrer()
        advanceUntilIdle()
        val erreurs = vm.etat.value.erreurs
        assertEquals(ErreurChamp.OBLIGATOIRE, erreurs[ChampEnfant.NOM])
        assertEquals(ErreurChamp.OBLIGATOIRE, erreurs[ChampEnfant.PRENOM])
        assertEquals(ErreurChamp.OBLIGATOIRE, erreurs[ChampEnfant.DATE_NAISSANCE])
        assertEquals(ErreurChamp.OBLIGATOIRE, erreurs[ChampEnfant.SEXE])
        assertTrue(env.magasin.enfants.isEmpty())
        assertFalse(vm.etat.value.enregistrement)
    }

    @Test
    fun apresUnePremiereTentative_lesErreursSuiventLaSaisieEnDirect() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        vm.definirPhoto(env.photos.nouvelleTemporaire())
        vm.enregistrer()
        assertEquals(4, vm.etat.value.erreurs.size)
        vm.modifierChamps { it.copy(nom = "Rakoto") }
        assertFalse(vm.etat.value.erreurs.containsKey(ChampEnfant.NOM))
        vm.modifierChamps { it.copy(nombreFreresSoeurs = "1", nombreFreresSoeursMembres = "2") }
        assertEquals(ErreurChamp.MEMBRES_SUPERIEUR_AU_TOTAL, vm.etat.value.erreurs[ChampEnfant.NB_FRERES_SOEURS_MEMBRES])
    }

    @Test
    fun enregistrementReussi_creeLaFiche_etFournitLIdentifiantPourLaNavigation() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        val temporaire = env.photos.nouvelleTemporaire()
        vm.definirPhoto(temporaire)
        vm.remplirObligatoires()
        vm.enregistrer()
        advanceUntilIdle()
        val id = vm.etat.value.enfantEnregistre
        assertNotNull(id)
        val fiche = env.magasin.enfants.getValue(id!!)
        assertEquals("Rakoto", fiche.nom) // espaces superflus retirés
        assertEquals(LocalDate.parse("2026-10-11"), fiche.dateDebutSuivi)
        assertNull(fiche.pereMembre) // non renseigné, jamais « Non »
        assertFalse(temporaire in env.photos.temporaires)
        assertFalse(vm.etat.value.enregistrement)
    }

    @Test
    fun cliquesRapides_neCreentQuUneSeuleFiche() = runTest(dispatcher) {
        val env = environnement()
        env.photos.temporairesIndelebiles = true // sans cela, le faux consommerait la photo et masquerait un doublon
        val vm = ajout(env)
        vm.definirPhoto(env.photos.nouvelleTemporaire())
        vm.remplirObligatoires()
        repeat(10) { vm.enregistrer() } // avant que la première coroutine ait pu s'exécuter
        advanceUntilIdle()
        repeat(3) { vm.enregistrer() } // et après l'enregistrement
        advanceUntilIdle()
        assertEquals(1, env.magasin.enfants.size)
    }

    @Test
    fun echecDeLaBase_conserveSaisieEtPhoto_puisLeReessaiReussit() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        val temporaire = env.photos.nouvelleTemporaire()
        vm.definirPhoto(temporaire)
        vm.remplirObligatoires()
        env.magasin.echecSurInsertionEnfant = true
        vm.enregistrer()
        advanceUntilIdle()
        assertEquals(ErreurEnregistrement.ECRITURE, vm.etat.value.erreurEnregistrement)
        assertFalse(vm.etat.value.enregistrement)
        assertNull(vm.etat.value.enfantEnregistre)
        assertEquals("Sarah", vm.etat.value.champs.prenom)
        assertEquals(temporaire, vm.etat.value.photo)
        assertTrue(env.magasin.enfants.isEmpty())

        env.magasin.echecSurInsertionEnfant = false
        vm.enregistrer()
        advanceUntilIdle()
        assertNotNull(vm.etat.value.enfantEnregistre)
        assertEquals(1, env.magasin.enfants.size)
    }

    @Test
    fun rotationOuRecreationDuProcessus_restaureLaSaisieEtLaPhoto() = runTest(dispatcher) {
        val env = environnement()
        val sauvegarde = SavedStateHandle()
        val avant = ajout(env, sauvegarde)
        val temporaire = env.photos.nouvelleTemporaire()
        avant.definirPhoto(temporaire)
        avant.modifierChamps {
            it.copy(
                nom = "Rakoto", prenom = "Sarah", dateNaissance = LocalDate.parse("2017-03-15"), sexe = Sexe.GARCON, adresse = "Lot 5",
                nomPere = "Jean", pereMembre = true, nomMere = "Hanta", mereMembre = false,
                nombreFreresSoeurs = "3", nombreFreresSoeursMembres = "2", dateArrivee = LocalDate.parse("2021-05-02"),
            )
        }

        // Nouveau ViewModel, même état sauvegardé : c'est ce que fait Android après une recréation.
        val apres = ajout(env, sauvegarde)
        advanceUntilIdle()
        assertEquals(avant.etat.value.champs, apres.etat.value.champs)
        assertEquals(temporaire, apres.etat.value.photo)
        assertEquals(true, apres.etat.value.champs.pereMembre)
        assertEquals(false, apres.etat.value.champs.mereMembre)
        assertEquals(9, apres.etat.value.age)
        assertFalse(apres.etat.value.photoTemporairePerdue)
    }

    @Test
    fun uneValeurNonRenseignee_resteNonRenseigneeApresRestauration() = runTest(dispatcher) {
        val env = environnement()
        val sauvegarde = SavedStateHandle()
        ajout(env, sauvegarde).modifierChamps { it.copy(nom = "Rakoto") }
        val apres = ajout(env, sauvegarde)
        assertNull(apres.etat.value.champs.pereMembre)
        assertNull(apres.etat.value.champs.sexe)
        assertNull(apres.etat.value.champs.dateNaissance)
    }

    @Test
    fun photoTemporaireDisparue_ramenAuParcoursPhoto_sansPlanter() = runTest(dispatcher) {
        val env = environnement()
        val sauvegarde = SavedStateHandle()
        val temporaire = env.photos.nouvelleTemporaire()
        ajout(env, sauvegarde).definirPhoto(temporaire)
        env.photos.temporaires.remove(temporaire) // nettoyée entre-temps
        val apres = ajout(env, sauvegarde)
        advanceUntilIdle()
        assertNull(apres.etat.value.photo)
        assertTrue(apres.etat.value.photoTemporairePerdue)
    }

    @Test
    fun ageCalculeEnLectureSeule_suitLaDateDeNaissance() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        assertNull(vm.etat.value.age)
        vm.modifierChamps { it.copy(dateNaissance = LocalDate.parse("2017-03-15")) }
        assertEquals(9, vm.etat.value.age) // 06/10/2026
        vm.modifierChamps { it.copy(dateNaissance = LocalDate.parse("2017-10-07")) }
        assertEquals(8, vm.etat.value.age) // anniversaire pas encore atteint
    }

    @Test
    fun reprendreLaPhoto_supprimeLaTemporaire_etRamenaALaCamera() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        val temporaire = env.photos.nouvelleTemporaire()
        vm.definirPhoto(temporaire)
        vm.reprendrePhoto()
        advanceUntilIdle()
        assertNull(vm.etat.value.photo)
        assertFalse(temporaire in env.photos.temporaires)
    }

    @Test
    fun abandonDeLAjout_supprimeLaPhotoTemporaire_etNeCreeAucuneFiche() = runTest(dispatcher) {
        val env = environnement()
        val vm = ajout(env)
        val temporaire = env.photos.nouvelleTemporaire()
        vm.definirPhoto(temporaire)
        vm.remplirObligatoires()
        assertTrue(vm.etat.value.aDuContenu)
        vm.abandonner()
        advanceUntilIdle()
        assertFalse(temporaire in env.photos.temporaires)
        assertTrue(env.magasin.enfants.isEmpty())
    }

    @Test
    fun modification_chargeLaFiche_puisEnregistreSansToucherAuSuivi() = runTest(dispatcher) {
        val env = environnement()
        val id = env.inscrire("Rakoto", "Sarah")
        val avant = env.magasin.enfants.getValue(id)
        val vm = FormulaireViewModel(env.enfants, env.photos, env.horloge, id, SavedStateHandle())
        assertTrue(vm.etat.value.chargement)
        advanceUntilIdle()
        assertFalse(vm.etat.value.chargement)
        assertEquals("Sarah", vm.etat.value.champs.prenom)
        assertEquals(avant.photoPath, vm.etat.value.photo)

        vm.modifierChamps { it.copy(prenom = "Sarah-Éva", adresse = "Lot 7") }
        vm.enregistrer()
        advanceUntilIdle()
        assertEquals(id, vm.etat.value.enfantEnregistre)
        val apres = env.magasin.enfants.getValue(id)
        assertEquals("Sarah-Éva", apres.prenom)
        assertEquals("Lot 7", apres.adresse)
        assertEquals(avant.dateDebutSuivi, apres.dateDebutSuivi)
        assertEquals(avant.photoPath, apres.photoPath)
    }

    @Test
    fun modification_dUneFicheInexistante_signaleUneErreur() = runTest(dispatcher) {
        val env = environnement()
        val vm = FormulaireViewModel(env.enfants, env.photos, env.horloge, 404, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(ErreurEnregistrement.INTROUVABLE, vm.etat.value.erreurEnregistrement)
        assertFalse(vm.etat.value.chargement)
    }
}
