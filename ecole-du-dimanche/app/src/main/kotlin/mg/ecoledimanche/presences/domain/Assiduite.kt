package mg.ecoledimanche.presences.domain

/**
 * Compteurs d'un enfant sur les séances CLÔTURÉES auxquelles il était admissible.
 * NON_ENREGISTRE n'entre jamais ici, et une séance encore ouverte est exclue en amont.
 */
data class CompteursAssiduite(
    val presences: Int,
    val retards: Int,
    val absences: Int,
) {
    val total: Int get() = presences + retards + absences

    /**
     * Taux d'assiduité en dixièmes de pour cent, arrondi à la demi-unité supérieure :
     * (présences + retards) / (présences + retards + absences) × 100.
     * `null` si aucune séance clôturée (pas de division par zéro, pas de faux 0 %).
     *
     * Calcul entier exact : 7/8 donne 875, soit 87,5 %.
     */
    val tauxDixiemes: Int?
        get() {
            val total = total
            if (total == 0) return null
            val participations = (presences + retards).toLong()
            return ((2L * participations * 1000L + total) / (2L * total)).toInt()
        }

    companion object {
        val VIDE = CompteursAssiduite(0, 0, 0)
    }
}

object FormatTaux {
    const val AUCUN = "—"

    /** « 87,5 % », « 100 % », « 0 % » ; « — » sans séance clôturée. Une seule décimale, virgule française. */
    fun format(dixiemes: Int?): String {
        if (dixiemes == null) return AUCUN
        val entier = dixiemes / 10
        val decimale = dixiemes % 10
        return if (decimale == 0) "$entier %" else "$entier,$decimale %"
    }
}
