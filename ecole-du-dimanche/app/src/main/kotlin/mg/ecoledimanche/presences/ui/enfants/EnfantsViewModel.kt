package mg.ecoledimanche.presences.ui.enfants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.Recherche
import mg.ecoledimanche.presences.domain.aujourdhui

data class EtatEcranEnfants(
    val archives: Boolean = false,
    val recherche: String = "",
    /** Enfants correspondant à la recherche, triés par prénom puis nom. */
    val enfants: List<EnfantEntity> = emptyList(),
    /** Nombre d'enfants de la liste avant filtrage (distingue « aucun enfant » de « aucun résultat »). */
    val totalSansFiltre: Int = 0,
    val aujourdhui: LocalDate = LocalDate.of(2000, 1, 1),
    val chargement: Boolean = true,
)

class EnfantsViewModel(
    private val enfants: EnfantRepository,
    private val horloge: AppClock,
) : ViewModel() {
    private val archives = MutableStateFlow(false)
    private val recherche = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val etat: StateFlow<EtatEcranEnfants> = archives
        .flatMapLatest { voirArchives ->
            (if (voirArchives) enfants.observerArchives() else enfants.observerActifs())
                .map { liste -> voirArchives to liste }
        }
        .combine(recherche) { (voirArchives, liste), requete ->
            EtatEcranEnfants(
                archives = voirArchives,
                recherche = requete,
                enfants = liste.filter { Recherche.correspond(it.nom, it.prenom, requete) },
                totalSansFiltre = liste.size,
                aujourdhui = horloge.aujourdhui(),
                chargement = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatEcranEnfants())

    fun rechercher(texte: String) {
        recherche.value = texte
    }

    fun voirArchives(voir: Boolean) {
        archives.value = voir
    }
}
