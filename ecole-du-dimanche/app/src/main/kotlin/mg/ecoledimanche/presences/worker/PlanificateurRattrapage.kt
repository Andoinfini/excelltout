package mg.ecoledimanche.presences.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.RegleSeance

/**
 * Planification WorkManager, SANS contrainte réseau et sans alarme exacte :
 * - un travail périodique unique (toutes les heures) ;
 * - un travail unique ciblé sur le prochain dimanche à 10 h (+ petite marge), qui se réarme
 *   lui-même après son exécution.
 *
 * Android peut différer ces travaux (économie d'énergie, application arrêtée de force, téléphone
 * éteint) : le rattrapage à l'ouverture de l'application garantit de toute façon un résultat cohérent.
 */
object PlanificateurRattrapage {
    const val NOM_PERIODIQUE = "rattrapage_periodique"
    const val NOM_PROCHAINE_CLOTURE = "rattrapage_prochaine_cloture"
    const val CLE_REARMER = "rearmer"
    private val MARGE_APRES_CLOTURE: Duration = Duration.ofSeconds(30)

    /** À appeler au démarrage du processus : ne duplique jamais un travail déjà planifié. */
    fun planifier(contexte: Context, horloge: AppClock) {
        WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
            NOM_PERIODIQUE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RattrapageWorker>(1, TimeUnit.HOURS).build(),
        )
        planifierProchaineCloture(contexte, horloge, ExistingWorkPolicy.KEEP)
    }

    /**
     * Planifie le travail ciblé sur la prochaine clôture.
     * - KEEP : s'il y en a déjà un en attente ou en cours, on n'en ajoute pas (pas de chaîne qui grossit).
     * - APPEND_OR_REPLACE : réservé au travail ciblé lui-même, encore « en cours » quand il se réarme,
     *   pour qu'il ne s'annule pas lui-même.
     */
    fun planifierProchaineCloture(contexte: Context, horloge: AppClock, politique: ExistingWorkPolicy) {
        val maintenant = horloge.instant()
        val echeance = RegleSeance.prochaineCloture(maintenant, horloge.zone()).plus(MARGE_APRES_CLOTURE)
        val delai = Duration.between(maintenant, echeance).coerceAtLeast(Duration.ofSeconds(1))
        val demande = OneTimeWorkRequestBuilder<RattrapageWorker>()
            .setInitialDelay(delai.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CLE_REARMER to true))
            .build()
        WorkManager.getInstance(contexte).enqueueUniqueWork(NOM_PROCHAINE_CLOTURE, politique, demande)
    }
}
