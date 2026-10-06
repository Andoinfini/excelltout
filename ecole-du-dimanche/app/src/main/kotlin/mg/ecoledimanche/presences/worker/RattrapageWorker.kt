package mg.ecoledimanche.presences.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import mg.ecoledimanche.presences.EcoleDimancheApp

/**
 * Tente de matérialiser les clôtures échues pendant que l'application est fermée.
 * Sans effet de bord s'il n'y a rien à faire ; idempotent, donc sans risque en cas de double exécution.
 */
class RattrapageWorker(contexte: Context, parametres: WorkerParameters) : CoroutineWorker(contexte, parametres) {
    override suspend fun doWork(): Result {
        val conteneur = (applicationContext as EcoleDimancheApp).conteneur
        return try {
            conteneur.rattraperEtEntretenir()
            // Le travail ciblé se réarme pour le dimanche suivant ; le travail périodique se contente
            // de vérifier qu'un travail ciblé existe toujours.
            val politique = if (inputData.getBoolean(PlanificateurRattrapage.CLE_REARMER, false)) {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }
            PlanificateurRattrapage.planifierProchaineCloture(applicationContext, conteneur.horloge, politique)
            Result.success()
        } catch (erreur: CancellationException) {
            throw erreur
        } catch (erreur: Exception) {
            Result.retry()
        }
    }
}
