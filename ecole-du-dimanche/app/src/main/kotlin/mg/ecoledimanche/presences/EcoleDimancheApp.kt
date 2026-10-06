package mg.ecoledimanche.presences

import android.app.Application
import mg.ecoledimanche.presences.di.ConteneurApp
import mg.ecoledimanche.presences.worker.PlanificateurRattrapage

class EcoleDimancheApp : Application() {
    lateinit var conteneur: ConteneurApp
        private set

    override fun onCreate() {
        super.onCreate()
        conteneur = ConteneurApp(this)
        // Filet de sécurité en arrière-plan : le rattrapage au lancement (MainActivity) reste la
        // référence, WorkManager ne garantit pas une exécution exactement à 10 h.
        PlanificateurRattrapage.planifier(this, conteneur.horloge)
    }
}
