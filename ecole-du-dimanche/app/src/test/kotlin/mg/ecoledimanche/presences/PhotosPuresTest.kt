package mg.ecoledimanche.presences

import mg.ecoledimanche.presences.camera.CalculsImage
import mg.ecoledimanche.presences.camera.FichierStocke
import mg.ecoledimanche.presences.camera.PolitiqueNettoyage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotosPuresTest {
    @Test
    fun echantillonnage_garde_au_moins_1280_sur_le_grand_cote() {
        assertEquals(1, CalculsImage.echantillonnage(1000, 800))
        assertEquals(1, CalculsImage.echantillonnage(1280, 960))
        assertEquals(1, CalculsImage.echantillonnage(2559, 1919)) // /2 -> 1279 < 1280
        assertEquals(2, CalculsImage.echantillonnage(2560, 1920))
        assertEquals(2, CalculsImage.echantillonnage(4000, 3000)) // 12 Mpx -> 2000 x 1500
        assertEquals(4, CalculsImage.echantillonnage(8000, 6000))
    }

    @Test
    fun echantillonnage_apres_decodage_donne_toujours_assez_de_pixels() {
        for ((l, h) in listOf(4000 to 3000, 3000 to 4000, 4032 to 3024, 9000 to 1200, 1200 to 9000)) {
            val n = CalculsImage.echantillonnage(l, h)
            assertTrue("$l x $h : le grand côté échantillonné doit rester >= 1280 ou l'image est déjà petite",
                maxOf(l, h) / n >= CalculsImage.COTE_MAX || n == 1)
        }
    }

    @Test
    fun dimensionsCibles_cote_max_1280_ratio_conserve_sans_agrandissement() {
        assertEquals(1280 to 960, CalculsImage.dimensionsCibles(2000, 1500))
        assertEquals(960 to 1280, CalculsImage.dimensionsCibles(1500, 2000))
        assertEquals(1280 to 720, CalculsImage.dimensionsCibles(1920, 1080))
        assertEquals(800 to 600, CalculsImage.dimensionsCibles(800, 600)) // jamais agrandie
        assertEquals(1280 to 1280, CalculsImage.dimensionsCibles(1280, 1280))
        assertEquals(1280 to 1, CalculsImage.dimensionsCibles(6000, 2)) // au moins 1 pixel
    }

    private val heure = 60L * 60 * 1000
    private val maintenant = 100 * 24 * heure

    @Test
    fun nettoyage_ne_supprime_jamais_un_fichier_reference() {
        val fichiers = listOf(FichierStocke("photos/a.jpg", 0), FichierStocke("photos/b.jpg", 0))
        val aSupprimer = PolitiqueNettoyage.orphelinesASupprimer(fichiers, setOf("photos/a.jpg"), maintenant)
        assertEquals(listOf("photos/b.jpg"), aSupprimer.map { it.chemin })
    }

    @Test
    fun nettoyage_epargne_un_fichier_recent_meme_non_reference() {
        val fichiers = listOf(
            FichierStocke("photos/recent.jpg", maintenant - 5 * 60 * 1000),
            FichierStocke("photos/ancien.jpg", maintenant - 2 * heure),
        )
        assertEquals(listOf("photos/ancien.jpg"), PolitiqueNettoyage.orphelinesASupprimer(fichiers, emptySet(), maintenant).map { it.chemin })
    }

    @Test
    fun nettoyage_des_temporaires_epargne_les_brouillons_de_moins_de_24h() {
        val fichiers = listOf(
            FichierStocke("photos_tmp/brouillon.jpg", maintenant - 3 * heure),
            FichierStocke("photos_tmp/abandonnee.jpg", maintenant - 25 * heure),
        )
        assertEquals(listOf("photos_tmp/abandonnee.jpg"), PolitiqueNettoyage.temporairesASupprimer(fichiers, maintenant).map { it.chemin })
    }
}
