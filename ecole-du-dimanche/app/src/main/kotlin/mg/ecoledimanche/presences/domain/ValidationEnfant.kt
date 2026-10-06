package mg.ecoledimanche.presences.domain

import java.time.LocalDate

/** Champs du formulaire pouvant porter une erreur. */
enum class ChampEnfant {
    NOM,
    PRENOM,
    DATE_NAISSANCE,
    SEXE,
    ADRESSE,
    NOM_PERE,
    NOM_MERE,
    NB_FRERES_SOEURS,
    NB_FRERES_SOEURS_MEMBRES,
    DATE_ARRIVEE,
}

enum class ErreurChamp {
    OBLIGATOIRE,
    DATE_FUTURE,
    ARRIVEE_AVANT_NAISSANCE,
    NOMBRE_INVALIDE,
    MEMBRES_SUPERIEUR_AU_TOTAL,
    TROP_LONG,
}

/** Saisie brute du formulaire, telle que tapée par l'utilisateur. */
data class SaisieEnfant(
    val nom: String = "",
    val prenom: String = "",
    val dateNaissance: LocalDate? = null,
    val sexe: Sexe? = null,
    val adresse: String = "",
    val nomPere: String = "",
    val pereMembre: Boolean? = null,
    val nomMere: String = "",
    val mereMembre: Boolean? = null,
    val nombreFreresSoeurs: String = "0",
    val nombreFreresSoeursMembres: String = "0",
    val dateArrivee: LocalDate? = null,
)

/** Données validées et normalisées, prêtes à être enregistrées. */
data class DonneesEnfant(
    val nom: String,
    val prenom: String,
    val dateNaissance: LocalDate,
    val sexe: Sexe,
    val adresse: String?,
    val nomPere: String?,
    val pereMembre: Boolean?,
    val nomMere: String?,
    val mereMembre: Boolean?,
    val nombreFreresSoeurs: Int,
    val nombreFreresSoeursMembres: Int,
    val dateArrivee: LocalDate?,
)

sealed interface ResultatValidation {
    data class Valide(val donnees: DonneesEnfant) : ResultatValidation
    data class Invalide(val erreurs: Map<ChampEnfant, ErreurChamp>) : ResultatValidation
}

object ValidationEnfant {
    const val LONGUEUR_MAX_NOM = 100
    const val LONGUEUR_MAX_ADRESSE = 300
    const val NOMBRE_MAX_FRATRIE = 99

    fun valider(saisie: SaisieEnfant, aujourdhui: LocalDate): ResultatValidation {
        val erreurs = LinkedHashMap<ChampEnfant, ErreurChamp>()

        // Espaces superflus retirés au début et à la fin ; les accents sont conservés.
        val nom = saisie.nom.trim()
        val prenom = saisie.prenom.trim()
        val adresse = saisie.adresse.trim().ifEmpty { null }
        val nomPere = saisie.nomPere.trim().ifEmpty { null }
        val nomMere = saisie.nomMere.trim().ifEmpty { null }

        verifierNom(nom, ChampEnfant.NOM, erreurs)
        verifierNom(prenom, ChampEnfant.PRENOM, erreurs)
        if (adresse != null && adresse.length > LONGUEUR_MAX_ADRESSE) erreurs[ChampEnfant.ADRESSE] = ErreurChamp.TROP_LONG
        if (nomPere != null && nomPere.length > LONGUEUR_MAX_NOM) erreurs[ChampEnfant.NOM_PERE] = ErreurChamp.TROP_LONG
        if (nomMere != null && nomMere.length > LONGUEUR_MAX_NOM) erreurs[ChampEnfant.NOM_MERE] = ErreurChamp.TROP_LONG

        val naissance = saisie.dateNaissance
        when {
            naissance == null -> erreurs[ChampEnfant.DATE_NAISSANCE] = ErreurChamp.OBLIGATOIRE
            naissance.isAfter(aujourdhui) -> erreurs[ChampEnfant.DATE_NAISSANCE] = ErreurChamp.DATE_FUTURE
        }

        if (saisie.sexe == null) erreurs[ChampEnfant.SEXE] = ErreurChamp.OBLIGATOIRE

        val arrivee = saisie.dateArrivee
        if (arrivee != null) {
            when {
                arrivee.isAfter(aujourdhui) -> erreurs[ChampEnfant.DATE_ARRIVEE] = ErreurChamp.DATE_FUTURE
                naissance != null && arrivee.isBefore(naissance) ->
                    erreurs[ChampEnfant.DATE_ARRIVEE] = ErreurChamp.ARRIVEE_AVANT_NAISSANCE
            }
        }

        val total = analyserEntier(saisie.nombreFreresSoeurs)
        val membres = analyserEntier(saisie.nombreFreresSoeursMembres)
        if (total == null) erreurs[ChampEnfant.NB_FRERES_SOEURS] = ErreurChamp.NOMBRE_INVALIDE
        if (membres == null) {
            erreurs[ChampEnfant.NB_FRERES_SOEURS_MEMBRES] = ErreurChamp.NOMBRE_INVALIDE
        } else if (total != null && membres > total) {
            erreurs[ChampEnfant.NB_FRERES_SOEURS_MEMBRES] = ErreurChamp.MEMBRES_SUPERIEUR_AU_TOTAL
        }

        if (erreurs.isNotEmpty()) return ResultatValidation.Invalide(erreurs)

        return ResultatValidation.Valide(
            DonneesEnfant(
                nom = nom,
                prenom = prenom,
                dateNaissance = naissance!!,
                sexe = saisie.sexe!!,
                adresse = adresse,
                nomPere = nomPere,
                pereMembre = saisie.pereMembre,
                nomMere = nomMere,
                mereMembre = saisie.mereMembre,
                nombreFreresSoeurs = total!!,
                nombreFreresSoeursMembres = membres!!,
                dateArrivee = arrivee,
            ),
        )
    }

    private fun verifierNom(valeur: String, champ: ChampEnfant, erreurs: MutableMap<ChampEnfant, ErreurChamp>) {
        when {
            valeur.isEmpty() -> erreurs[champ] = ErreurChamp.OBLIGATOIRE
            valeur.length > LONGUEUR_MAX_NOM -> erreurs[champ] = ErreurChamp.TROP_LONG
        }
    }

    /** Entier positif ou nul ; une zone laissée vide vaut 0. Refuse signe, décimales et lettres. */
    internal fun analyserEntier(texte: String): Int? {
        val propre = texte.trim()
        if (propre.isEmpty()) return 0
        if (propre.length > 2 || !propre.all { it in '0'..'9' }) return null
        return propre.toInt().takeIf { it <= NOMBRE_MAX_FRATRIE }
    }
}
