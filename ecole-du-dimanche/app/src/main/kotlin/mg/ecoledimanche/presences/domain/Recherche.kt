package mg.ecoledimanche.presences.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Outils de recherche et de tri insensibles à la casse et aux accents (noms français et malgaches). */
object Recherche {
    private val marquesDiacritiques = Regex("\\p{M}+")
    private val espaces = Regex("\\s+")

    /** « Rakotö  Éva » devient « rakoto eva ». */
    fun normaliser(texte: String): String {
        val decompose = Normalizer.normalize(texte, Normalizer.Form.NFD)
        return marquesDiacritiques.replace(decompose, "")
            .lowercase(Locale.ROOT)
            .replace(espaces, " ")
            .trim()
    }

    /** Vrai si chaque mot de la requête figure dans « prénom nom » ou « nom prénom ». */
    fun correspond(nom: String, prenom: String, requete: String): Boolean {
        val mots = normaliser(requete).split(' ').filter { it.isNotEmpty() }
        if (mots.isEmpty()) return true
        val complet = normaliser("$prenom $nom") + " " + normaliser("$nom $prenom")
        return mots.all { complet.contains(it) }
    }

    /** Comparateur « prénom puis nom », sans distinction de casse ni d'accents. */
    fun comparateurNoms(): Comparator<Pair<String, String>> {
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
        return Comparator { a, b ->
            val parPrenom = collator.compare(a.first, b.first)
            if (parPrenom != 0) parPrenom else collator.compare(a.second, b.second)
        }
    }
}
