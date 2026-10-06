package mg.ecoledimanche.presences.ui.historique

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mg.ecoledimanche.presences.data.repository.HistoriqueEnfant
import mg.ecoledimanche.presences.data.repository.PresenceRepository

sealed interface EtatEcranHistorique {
    data object Chargement : EtatEcranHistorique
    data object Erreur : EtatEcranHistorique
    data object Introuvable : EtatEcranHistorique
    data class Pret(val historique: HistoriqueEnfant) : EtatEcranHistorique
}

class HistoriqueViewModel(
    private val presences: PresenceRepository,
    private val enfantId: Long,
    private val actualisation: Flow<Unit>,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val etat: StateFlow<EtatEcranHistorique> = actualisation
        .flatMapLatest { fluxHistorique() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatEcranHistorique.Chargement)

    private fun fluxHistorique(): Flow<EtatEcranHistorique> = flow<EtatEcranHistorique> {
        presences.rattraper()
        emitAll(
            presences.observerHistorique(enfantId).map { historique ->
                if (historique == null) EtatEcranHistorique.Introuvable else EtatEcranHistorique.Pret(historique)
            },
        )
    }.catch { erreur ->
        if (erreur is CancellationException) throw erreur
        emit(EtatEcranHistorique.Erreur)
    }
}
