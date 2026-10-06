package mg.ecoledimanche.presences.ui.fiche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.ecoledimanche.presences.data.repository.EnfantRepository

data class EtatChangerPhoto(
    val enCours: Boolean = false,
    val erreur: Boolean = false,
    val termine: Boolean = false,
)

class ChangerPhotoViewModel(
    private val enfants: EnfantRepository,
    private val enfantId: Long,
) : ViewModel() {
    private val _etat = MutableStateFlow(EtatChangerPhoto())
    val etat: StateFlow<EtatChangerPhoto> = _etat

    /** Photo validée par l'utilisateur : enregistrement, puis suppression de l'ancienne seulement si tout a réussi. */
    fun valider(cheminTemporaire: String) {
        if (_etat.value.enCours || _etat.value.termine) return
        _etat.update { it.copy(enCours = true, erreur = false) }
        viewModelScope.launch {
            try {
                val trouve = enfants.remplacerPhoto(enfantId, cheminTemporaire)
                _etat.value = EtatChangerPhoto(termine = trouve, erreur = !trouve)
            } catch (erreur: CancellationException) {
                throw erreur
            } catch (erreur: Exception) {
                // L'ancienne photo est conservée.
                _etat.value = EtatChangerPhoto(erreur = true)
            }
        }
    }

    fun effacerErreur() {
        _etat.update { it.copy(erreur = false) }
    }
}
