package mg.ecoledimanche.presences.ui.formulaire

import java.time.LocalDate
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.domain.SaisieEnfant
import mg.ecoledimanche.presences.domain.Sexe

/** Valeurs saisies dans le formulaire (non validées), telles que l'utilisateur les voit. */
data class ChampsFormulaire(
    val nom: String = "",
    val prenom: String = "",
    val dateNaissance: LocalDate? = null,
    val sexe: Sexe? = null,
    val adresse: String = "",
    val nomPere: String = "",
    /** Nul = non renseigné (jamais « Non » par défaut). */
    val pereMembre: Boolean? = null,
    val nomMere: String = "",
    val mereMembre: Boolean? = null,
    val nombreFreresSoeurs: String = "0",
    val nombreFreresSoeursMembres: String = "0",
    val dateArrivee: LocalDate? = null,
) {
    fun versSaisie() = SaisieEnfant(
        nom = nom,
        prenom = prenom,
        dateNaissance = dateNaissance,
        sexe = sexe,
        adresse = adresse,
        nomPere = nomPere,
        pereMembre = pereMembre,
        nomMere = nomMere,
        mereMembre = mereMembre,
        nombreFreresSoeurs = nombreFreresSoeurs,
        nombreFreresSoeursMembres = nombreFreresSoeursMembres,
        dateArrivee = dateArrivee,
    )

    companion object {
        fun depuis(enfant: EnfantEntity) = ChampsFormulaire(
            nom = enfant.nom,
            prenom = enfant.prenom,
            dateNaissance = enfant.dateNaissance,
            sexe = enfant.sexe,
            adresse = enfant.adresse.orEmpty(),
            nomPere = enfant.nomPere.orEmpty(),
            pereMembre = enfant.pereMembre,
            nomMere = enfant.nomMere.orEmpty(),
            mereMembre = enfant.mereMembre,
            nombreFreresSoeurs = enfant.nombreFreresSoeurs.toString(),
            nombreFreresSoeursMembres = enfant.nombreFreresSoeursMembres.toString(),
            dateArrivee = enfant.dateArriveeEglise,
        )
    }
}
