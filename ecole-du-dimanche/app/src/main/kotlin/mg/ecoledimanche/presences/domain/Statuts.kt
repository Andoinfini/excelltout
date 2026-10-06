package mg.ecoledimanche.presences.domain

/**
 * Statut d'un enfant pour un dimanche. Les identifiants sont sans accent : ce sont eux
 * qui sont enregistrés en base (colonne `statut`). Les libellés affichés sont dans les ressources.
 */
enum class StatutPresence {
    NON_ENREGISTRE,
    PRESENT,
    EN_RETARD,
    ABSENT,
}

/** Sexe d'un enfant ; valeur contrôlée enregistrée en base sous son nom. */
enum class Sexe {
    GARCON,
    FILLE,
}
