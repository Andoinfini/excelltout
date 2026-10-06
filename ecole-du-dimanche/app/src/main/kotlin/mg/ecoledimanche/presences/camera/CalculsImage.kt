package mg.ecoledimanche.presences.camera

import kotlin.math.max
import kotlin.math.roundToInt

/** Calculs de taille d'image (purs, testables sans Android). */
object CalculsImage {
    /** Côté maximal des photos enregistrées : grille fluide et stockage maîtrisé. */
    const val COTE_MAX = 1280

    /**
     * Facteur de sous-échantillonnage (puissance de 2) à passer au décodeur pour éviter de charger
     * une photo de 12 Mpx en mémoire, tout en gardant au moins [coteMax] pixels sur le grand côté.
     */
    fun echantillonnage(largeur: Int, hauteur: Int, coteMax: Int = COTE_MAX): Int {
        require(largeur > 0 && hauteur > 0 && coteMax > 0) { "dimensions invalides" }
        var facteur = 1
        while (largeur / (facteur * 2) >= coteMax || hauteur / (facteur * 2) >= coteMax) facteur *= 2
        return facteur
    }

    /** Dimensions finales : ratio conservé, jamais agrandi, grand côté limité à [coteMax]. */
    fun dimensionsCibles(largeur: Int, hauteur: Int, coteMax: Int = COTE_MAX): Pair<Int, Int> {
        require(largeur > 0 && hauteur > 0 && coteMax > 0) { "dimensions invalides" }
        val grandCote = max(largeur, hauteur)
        if (grandCote <= coteMax) return largeur to hauteur
        val facteur = coteMax.toDouble() / grandCote
        return max(1, (largeur * facteur).roundToInt()) to max(1, (hauteur * facteur).roundToInt())
    }
}
