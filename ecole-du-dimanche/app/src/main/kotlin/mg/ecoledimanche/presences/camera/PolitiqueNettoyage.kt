package mg.ecoledimanche.presences.camera

/** Un fichier du stockage privé, identifié par son chemin relatif. */
data class FichierStocke(val chemin: String, val modifieLe: Long)

/**
 * Qui peut être supprimé sans risque.
 *
 * Les opérations de fichiers et de Room ne forment pas une transaction unique : des fichiers
 * orphelins peuvent subsister (arrêt brutal, échec d'écriture). On ne supprime que :
 * - dans `photos/` : les fichiers que AUCUNE fiche ne référence ET assez anciens pour ne pas être
 *   en cours d'enregistrement ;
 * - dans `photos_tmp/` : les brouillons abandonnés depuis plus de 24 h (un brouillon récent reste
 *   récupérable après une rotation ou une recréation du processus).
 */
object PolitiqueNettoyage {
    const val DELAI_ORPHELINE_MS: Long = 60L * 60 * 1000
    const val DELAI_TEMPORAIRE_MS: Long = 24L * 60 * 60 * 1000

    fun orphelinesASupprimer(fichiers: List<FichierStocke>, references: Set<String>, maintenant: Long): List<FichierStocke> =
        fichiers.filter { it.chemin !in references && maintenant - it.modifieLe > DELAI_ORPHELINE_MS }

    fun temporairesASupprimer(fichiers: List<FichierStocke>, maintenant: Long): List<FichierStocke> =
        fichiers.filter { maintenant - it.modifieLe > DELAI_TEMPORAIRE_MS }
}
