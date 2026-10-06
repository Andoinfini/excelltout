package mg.ecoledimanche.presences.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.data.Environnement
import mg.ecoledimanche.presences.data.instantLocal
import mg.ecoledimanche.presences.ui.enfants.EnfantsViewModel
import mg.ecoledimanche.presences.ui.fiche.EtatEcranFiche
import mg.ecoledimanche.presences.ui.fiche.FicheViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnfantsEtFicheViewModelTest : TestAvecMain() {
    private fun environnement() = Environnement(instantLocal("2026-10-06", 14))

    @Test
    fun recherche_ignoreCasseEtAccents_surNomEtPrenom() = runTest(dispatcher) {
        val env = environnement()
        env.inscrire("Rakoto", "Éloïse")
        env.inscrire("Andriamanjato", "David")
        env.inscrire("Rasoa", "Nathan")
        val vm = EnfantsViewModel(env.enfants, env.horloge)
        backgroundScope.launch { vm.etat.collect { } }
        advanceUntilIdle()
        assertEquals(listOf("David", "Éloïse", "Nathan"), vm.etat.value.enfants.map { it.prenom })

        vm.rechercher("eloise"); advanceUntilIdle()
        assertEquals(listOf("Éloïse"), vm.etat.value.enfants.map { it.prenom })
        vm.rechercher("ANDRIA"); advanceUntilIdle()
        assertEquals(listOf("David"), vm.etat.value.enfants.map { it.prenom })
        vm.rechercher("zzz"); advanceUntilIdle()
        assertTrue(vm.etat.value.enfants.isEmpty())
        assertEquals(3, vm.etat.value.totalSansFiltre) // « aucun résultat » ≠ « aucun enfant »
    }

    @Test
    fun archives_sontSeparees_desActifs_etRestentConsultables() = runTest(dispatcher) {
        val env = environnement()
        val id = env.inscrire("Rakoto", "Sarah")
        env.inscrire("Rakoto", "David")
        env.enfants.archiver(id)
        val vm = EnfantsViewModel(env.enfants, env.horloge)
        backgroundScope.launch { vm.etat.collect { } }
        advanceUntilIdle()
        assertEquals(listOf("David"), vm.etat.value.enfants.map { it.prenom })
        vm.voirArchives(true); advanceUntilIdle()
        assertTrue(vm.etat.value.archives)
        assertEquals(listOf("Sarah"), vm.etat.value.enfants.map { it.prenom })
    }

    @Test
    fun fiche_calculeLAge_et_signaleUnePhotoManquante() = runTest(dispatcher) {
        val env = environnement()
        val id = env.inscrire("Rakoto", "Sarah") // née le 15/03/2017
        val vm = FicheViewModel(env.enfants, env.photos, id, env.horloge)
        backgroundScope.launch { vm.etat.collect { } }
        advanceUntilIdle()
        val pret = vm.etat.value as EtatEcranFiche.Pret
        assertEquals(9, pret.age)
        assertTrue(pret.photoDisponible)

        env.photos.definitives.clear() // fichier disparu
        env.enfants.modifier(id, mg.ecoledimanche.presences.data.donnees("Rakoto", "Sarah")) // force une réémission
        advanceUntilIdle()
        assertFalse((vm.etat.value as EtatEcranFiche.Pret).photoDisponible)
    }

    @Test
    fun fiche_inexistante_estSignalee() = runTest(dispatcher) {
        val env = environnement()
        val vm = FicheViewModel(env.enfants, env.photos, 404, env.horloge)
        backgroundScope.launch { vm.etat.collect { } }
        advanceUntilIdle()
        assertEquals(EtatEcranFiche.Introuvable, vm.etat.value)
    }

    @Test
    fun archivage_demandeConfirmationPuisArchiveEtConserveLaFiche() = runTest(dispatcher) {
        val env = environnement()
        val id = env.inscrire("Rakoto", "Sarah")
        val vm = FicheViewModel(env.enfants, env.photos, id, env.horloge)
        backgroundScope.launch { vm.etat.collect { } }
        backgroundScope.launch { vm.archivage.collect { } }
        advanceUntilIdle()

        vm.demanderArchivage()
        assertTrue(vm.archivage.value.confirmation)
        vm.annulerArchivage()
        assertFalse(vm.archivage.value.confirmation)
        assertFalse(env.magasin.enfants.getValue(id).isArchived) // annuler n'archive rien

        vm.demanderArchivage()
        vm.confirmerArchivage()
        vm.confirmerArchivage() // double clic
        advanceUntilIdle()
        assertTrue(env.magasin.enfants.getValue(id).isArchived)
        assertFalse(vm.archivage.value.confirmation)
        assertTrue((vm.etat.value as EtatEcranFiche.Pret).enfant.isArchived)
        assertTrue(env.magasin.enfants.getValue(id).photoPath in env.photos.definitives) // photo conservée
    }
}
