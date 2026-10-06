package mg.ecoledimanche.presences.ui.assiduite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mg.ecoledimanche.presences.data.repository.LigneAssiduite
import mg.ecoledimanche.presences.data.repository.PresenceRepository

sealed interface EtatEcranAssiduite {
    data object Chargement : EtatEcranAssiduite
    data object Erreur : EtatEcranAssiduite
    data class Pret(val archives: Boolean, val lignes: List<LigneAssiduite>) : EtatEcranAssiduite
}

class AssiduiteViewModel(
    private val presences: PresenceRepository,
    private val actualisation: Flow<Unit>,
) : ViewModel() {
    private val archives = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val etat: StateFlow<EtatEcranAssiduite> = combine(archives, actualisation) { voirArchives, _ -> voirArchives }
        .flatMapLatest { voirArchives -> fluxAssiduite(voirArchives) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatEcranAssiduite.Chargement)

    private fun fluxAssiduite(voirArchives: Boolean): Flow<EtatEcranAssiduite> = flow<EtatEcranAssiduite> {
        // Rattrapage avant de lire : les statistiques n'incluent que des séances clôturées à jour.
        presences.rattraper()
        emitAll(presences.observerAssiduite(voirArchives).map { EtatEcranAssiduite.Pret(voirArchives, it) })
    }.catch { erreur ->
        if (erreur is CancellationException) throw erreur
        emit(EtatEcranAssiduite.Erreur)
    }

    fun voirArchives(voir: Boolean) {
        archives.value = voir
    }
}
