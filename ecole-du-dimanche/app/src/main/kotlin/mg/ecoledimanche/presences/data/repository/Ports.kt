package mg.ecoledimanche.presences.data.repository

/** Exécute un bloc de façon atomique : tout est validé, ou rien. Implémenté avec Room en production. */
interface ExecuteurTransaction {
    suspend fun <T> executer(bloc: suspend () -> T): T
}

/**
 * Accès aux fichiers photo, vu des repositories. Les chemins sont RELATIFS au répertoire privé
 * de l'application. Implémentation Android : `camera/StockagePhotosPrive`.
 */
interface StockagePhotos {
    /**
     * Enregistre une COPIE définitive de la photo temporaire validée et retourne son chemin
     * relatif. La temporaire est conservée : si l'écriture en base échoue ensuite, l'utilisateur
     * peut réessayer sans reprendre la photo. Lève une exception si le fichier est absent,
     * illisible ou ne peut pas être écrit.
     */
    suspend fun promouvoirTemporaire(cheminTemporaire: String): String

    /** Supprime un fichier (sans erreur s'il n'existe pas). */
    suspend fun supprimer(chemin: String)

    /** Vrai si le fichier existe et peut être décodé comme une image. */
    suspend fun existe(chemin: String): Boolean
}
