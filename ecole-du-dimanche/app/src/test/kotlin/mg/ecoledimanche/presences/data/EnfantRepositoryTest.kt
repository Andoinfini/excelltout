package mg.ecoledimanche.presences.data

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.domain.StatutPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnfantRepositoryTest {
    private val dimanche = LocalDate.parse("2026-10-11")

    @Test
    fun ajout_enregistreLaPhotoDefinitive_etLaFicheLaReference() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val temporaire = env.photos.nouvelleTemporaire()
        val id = env.enfants.ajouter(donnees("Rakoto", "Sarah"), temporaire)
        val fiche = env.magasin.enfants.getValue(id)
        assertTrue(fiche.photoPath in env.photos.definitives)
        assertFalse(temporaire in env.photos.temporaires) // la temporaire a été consommée
        assertFalse(fiche.isArchived)
        assertEquals(null, fiche.dateFinSuivi)
        assertEquals(dimanche, fiche.dateDebutSuivi)
    }

    @Test
    fun ajout_sansPhotoLisible_neCreeAucuneFicheIncomplete() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        try {
            env.enfants.ajouter(donnees("Rakoto", "Sarah"), "photos_tmp/inexistante.jpg")
            fail("l'ajout aurait dû échouer")
        } catch (e: IOException) {
            // attendu
        }
        assertTrue(env.magasin.enfants.isEmpty())
        assertTrue(env.photos.definitives.isEmpty())
    }

    @Test
    fun ajout_siLaBaseEchoue_laCopieDefinitiveEstRetiree_laTemporaireReste_etOnPeutReessayer() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val temporaire = env.photos.nouvelleTemporaire()
        env.magasin.echecSurInsertionEnfant = true
        try {
            env.enfants.ajouter(donnees("Rakoto", "Sarah"), temporaire)
            fail("l'ajout aurait dû échouer")
        } catch (e: IllegalStateException) {
            // attendu
        }
        assertTrue(env.magasin.enfants.isEmpty())
        assertTrue("aucune photo définitive orpheline", env.photos.definitives.isEmpty())
        assertTrue("la photo temporaire est conservée pour réessayer", temporaire in env.photos.temporaires)

        env.magasin.echecSurInsertionEnfant = false
        val id = env.enfants.ajouter(donnees("Rakoto", "Sarah"), temporaire)
        assertEquals(1, env.magasin.enfants.size)
        assertTrue(env.magasin.enfants.getValue(id).photoPath in env.photos.definitives)
        assertFalse(temporaire in env.photos.temporaires)
    }

    @Test
    fun deuxHomonymes_sontDeuxFichesDistinctes() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val a = env.inscrire("Rakoto", "Sarah")
        val b = env.inscrire("Rakoto", "Sarah")
        assertNotEquals(a, b)
        assertEquals(2, env.enfants.observerActifs().first().size)
    }

    @Test
    fun modification_neChangeNiLeDebutNiLaFinDeSuivi_niLaPhoto_niLHistorique() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        env.allerA("2026-10-11", 9, 0)
        env.presences.pointer(id, dimanche, StatutPresence.PRESENT)
        val avant = env.magasin.enfants.getValue(id)

        env.allerA("2026-10-12", 9, 0)
        val modif = donnees("Randria", "Sarah-Éva", naissance = "2017-03-16").copy(adresse = "Lot 5")
        assertTrue(env.enfants.modifier(id, modif))
        val apres = env.magasin.enfants.getValue(id)
        assertEquals("Randria", apres.nom)
        assertEquals("Sarah-Éva", apres.prenom)
        assertEquals("Lot 5", apres.adresse)
        assertEquals(avant.dateDebutSuivi, apres.dateDebutSuivi)
        assertEquals(avant.dateFinSuivi, apres.dateFinSuivi)
        assertEquals(avant.photoPath, apres.photoPath)
        assertEquals(avant.isArchived, apres.isArchived)
        assertEquals(avant.createdAt, apres.createdAt)
        assertEquals(StatutPresence.PRESENT, env.magasin.presences.getValue(id to dimanche).statut)
        assertFalse(env.enfants.modifier(9999, modif))
    }

    @Test
    fun modification_dUnEnfantArchive_neLeReactivePas() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        env.enfants.archiver(id)
        env.enfants.modifier(id, donnees("Rakoto", "Sarah-Éva"))
        val fiche = env.magasin.enfants.getValue(id)
        assertTrue(fiche.isArchived)
        assertEquals(LocalDate.parse("2026-10-06"), fiche.dateFinSuivi)
    }

    @Test
    fun remplacementDePhoto_nouveauFichier_puisReference_puisSuppressionDeLAncien() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        val ancienne = env.magasin.enfants.getValue(id).photoPath

        assertTrue(env.enfants.remplacerPhoto(id, env.photos.nouvelleTemporaire()))
        val nouvelle = env.magasin.enfants.getValue(id).photoPath
        assertNotEquals(ancienne, nouvelle)
        assertEquals(setOf(nouvelle), env.photos.definitives) // l'ancienne a été supprimée après la mise à jour
    }

    @Test
    fun remplacementDePhoto_enCasDEchecDeFichier_conserveLAncienne() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        val ancienne = env.magasin.enfants.getValue(id).photoPath
        env.photos.echecPromotion = true
        try {
            env.enfants.remplacerPhoto(id, env.photos.nouvelleTemporaire())
            fail("le remplacement aurait dû échouer")
        } catch (e: IOException) {
            // attendu
        }
        assertEquals(ancienne, env.magasin.enfants.getValue(id).photoPath)
        assertEquals(setOf(ancienne), env.photos.definitives)
    }

    @Test
    fun remplacementDePhoto_siLaBaseEchoue_conserveLAncienneEtRetireLaNouvelleCopie() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        val ancienne = env.magasin.enfants.getValue(id).photoPath
        val temporaire = env.photos.nouvelleTemporaire()
        env.magasin.echecSurChangerPhoto = true
        try {
            env.enfants.remplacerPhoto(id, temporaire)
            fail("le remplacement aurait dû échouer")
        } catch (e: IllegalStateException) {
            // attendu
        }
        assertEquals(ancienne, env.magasin.enfants.getValue(id).photoPath) // la fiche pointe toujours sur l'ancienne
        assertEquals(setOf(ancienne), env.photos.definitives) // l'ancienne n'a PAS été supprimée, la copie est retirée
        assertTrue(temporaire in env.photos.temporaires) // et on peut réessayer sans reprendre la photo

        env.magasin.echecSurChangerPhoto = false
        assertTrue(env.enfants.remplacerPhoto(id, temporaire))
        assertTrue(env.magasin.enfants.getValue(id).photoPath != ancienne)
        assertEquals(setOf(env.magasin.enfants.getValue(id).photoPath), env.photos.definitives)
    }

    @Test
    fun remplacementDePhoto_dUnEnfantInexistant_neLaisseAucunFichierOrphelin() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        assertFalse(env.enfants.remplacerPhoto(404, env.photos.nouvelleTemporaire()))
        assertTrue(env.photos.definitives.isEmpty())
    }

    @Test
    fun archivage_nEffacePasLaPhoto() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        val id = env.inscrire("Rakoto", "Sarah")
        val photo = env.magasin.enfants.getValue(id).photoPath
        env.enfants.archiver(id)
        assertTrue(photo in env.photos.definitives)
        assertEquals(photo, env.magasin.enfants.getValue(id).photoPath)
    }

    @Test
    fun listeDesEnfants_triee_sansDistinctionDeCasseNiDAccents() = runTest {
        val env = Environnement(instantLocal("2026-10-06", 14))
        env.inscrire("Z", "Zoé")
        env.inscrire("A", "émilie")
        env.inscrire("B", "Élodie")
        env.inscrire("C", "david")
        assertEquals(listOf("david", "Élodie", "émilie", "Zoé"), env.enfants.observerActifs().first().map { it.prenom })
    }
}
