package mg.ecoledimanche.presences.di

import android.content.Context
import mg.ecoledimanche.presences.camera.StockagePhotosPrive
import mg.ecoledimanche.presences.data.local.AppDatabase
import mg.ecoledimanche.presences.data.local.ExecuteurTransactionRoom
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.data.repository.PresenceRepository
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.SystemAppClock
import mg.ecoledimanche.presences.ui.Actualisation

/**
 * Injection de dépendances manuelle : un conteneur simple, créé une fois par processus.
 * Tout est paresseux : la base n'est ouverte qu'au premier accès, jamais sur le thread principal
 * (les DAO sont des fonctions `suspend` ou des `Flow` exécutés par Room hors du thread principal).
 */
class ConteneurApp(contexte: Context) {
    private val contexteApplication: Context = contexte.applicationContext

    val horloge: AppClock = SystemAppClock

    /** Dossier privé de l'application : base et photos, jamais de stockage public. */
    val stockagePhotos: StockagePhotosPrive by lazy { StockagePhotosPrive(contexteApplication.filesDir) }

    private val base: AppDatabase by lazy { AppDatabase.creer(contexteApplication) }
    private val transaction by lazy { ExecuteurTransactionRoom(base) }

    val enfantRepository: EnfantRepository by lazy {
        EnfantRepository(base.enfantDao(), base.seanceDao(), transaction, stockagePhotos, horloge)
    }

    val presenceRepository: PresenceRepository by lazy {
        PresenceRepository(base.enfantDao(), base.seanceDao(), base.presenceDao(), transaction, horloge)
    }

    /** Ordres de réactualisation des écrans : retour au premier plan, clôture à 10 h, minuit. */
    val actualisation: Actualisation by lazy { Actualisation(horloge) }

    /**
     * Rattrapage des clôtures échues puis entretien des photos orphelines.
     * À appeler hors du thread principal (fonction `suspend`).
     */
    suspend fun rattraperEtEntretenir() {
        presenceRepository.rattraper()
        stockagePhotos.nettoyer(base.enfantDao().tousLesCheminsPhoto().toSet())
    }
}
