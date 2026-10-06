package mg.ecoledimanche.presences.ui.navigation

/** Routes de navigation (chaînes simples, sans plugin de sérialisation). */
object Routes {
    const val DIMANCHE = "dimanche"
    const val ENFANTS = "enfants"
    const val ASSIDUITE = "assiduite"
    const val AJOUTER = "ajouter"

    const val ARG_ID = "id"
    const val FICHE = "enfant/{$ARG_ID}"
    const val MODIFIER = "enfant/{$ARG_ID}/modifier"
    const val PHOTO = "enfant/{$ARG_ID}/photo"
    const val HISTORIQUE = "enfant/{$ARG_ID}/historique"

    fun fiche(id: Long) = "enfant/$id"
    fun modifier(id: Long) = "enfant/$id/modifier"
    fun photo(id: Long) = "enfant/$id/photo"
    fun historique(id: Long) = "enfant/$id/historique"

    /** Écrans qui affichent la barre de navigation basse. */
    val ONGLETS = setOf(DIMANCHE, ENFANTS, ASSIDUITE)
}
