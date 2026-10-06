package mg.ecoledimanche.presences.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.Frontieres

/**
 * Ordres de réactualisation des écrans : l'exactitude de l'historique ne dépend jamais d'un
 * minuteur qui fonctionnerait seulement écran ouvert, mais un écran affiché doit se mettre à
 * jour tout seul quand 10 h est atteint ou que le jour change.
 *
 * Le flux émet :
 * - au démarrage de la collecte (donc au retour au premier plan, l'écran étant relancé) ;
 * - à chaque [signalerReprise] (retour de l'application au premier plan) ;
 * - à la prochaine frontière horaire (clôture à 10 h, minuit, au plus tous les quarts d'heure).
 */
class Actualisation(private val horloge: AppClock) {
    private val reprises = MutableStateFlow(0)

    fun signalerReprise() {
        reprises.update { it + 1 }
    }

    val flux: Flow<Unit> = merge(reprises.map { }, frontieres())

    private fun frontieres(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(Frontieres.delaiAvantProchaine(horloge.instant(), horloge.zone()).toMillis())
        }
    }
}
