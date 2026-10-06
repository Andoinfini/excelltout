package mg.ecoledimanche.presences.ui.fiche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.data.repository.StockagePhotos
import mg.ecoledimanche.presences.domain.Age
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.aujourdhui

sealed interface EtatEcranFiche {
    data object Chargement : EtatEcranFiche
    data object Introuvable : EtatEcranFiche
    data object Erreur : EtatEcranFiche

    data class Pret(
        val enfant: EnfantEntity,
        /** Âge calculé à l'affichage depuis la date de naissance, jamais stocké. */
        val age: Int,
        /** Faux si le fichier photo est absent ou illisible : l'écran propose alors de la remplacer. */
        val photoDisponible: Boolean,
    ) : EtatEcranFiche
}

data class ArchivageUi(
    val confirmation: Boolean = false,
    val enCours: Boolean = false,
    val erreur: Boolean = false,
)

class FicheViewModel(
    private val enfants: EnfantRepository,
    private val photos: StockagePhotos,
    private val enfantId: Long,
    private val horloge: AppClock,
) : ViewModel() {
    val etat: StateFlow<EtatEcranFiche> = enfants.observer(enfantId)
        .map { enfant ->
            if (enfant == null) {
                EtatEcranFiche.Introuvable
            } else {
                EtatEcranFiche.Pret(
                    enfant = enfant,
                    age = Age.enAnnees(enfant.dateNaissance, horloge.aujourdhui()),
                    photoDisponible = photos.existe(enfant.photoPath),
                )
            }
        }
        .catch { erreur ->
            if (erreur is CancellationException) throw erreur
            emit(EtatEcranFiche.Erreur)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatEcranFiche.Chargement)

    private val _archivage = MutableStateFlow(ArchivageUi())
    val archivage: StateFlow<ArchivageUi> = _archivage

    fun demanderArchivage() {
        _archivage.value = ArchivageUi(confirmation = true)
    }

    fun annulerArchivage() {
        if (_archivage.value.enCours) return
        _archivage.value = ArchivageUi()
    }

    fun confirmerArchivage() {
        if (_archivage.value.enCours) return // un seul archivage à la fois
        _archivage.update { it.copy(enCours = true, erreur = false) }
        viewModelScope.launch {
            try {
                enfants.archiver(enfantId)
                _archivage.value = ArchivageUi()
            } catch (erreur: CancellationException) {
                throw erreur
            } catch (erreur: Exception) {
                _archivage.value = ArchivageUi(confirmation = true, erreur = true)
            }
        }
    }
}
