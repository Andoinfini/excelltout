package mg.ecoledimanche.presences.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class EtatDemarrage { CHARGEMENT, PRET, ERREUR }

/**
 * Au démarrage : charger la base, effectuer le rattrapage des séances échues, puis afficher
 * Dimanche. Le [travail] est fourni par le conteneur (rattrapage + entretien des photos).
 */
class DemarrageViewModel(private val travail: suspend () -> Unit) : ViewModel() {
    private val _etat = MutableStateFlow(EtatDemarrage.CHARGEMENT)
    val etat: StateFlow<EtatDemarrage> = _etat

    init {
        lancer()
    }

    fun lancer() {
        _etat.value = EtatDemarrage.CHARGEMENT
        viewModelScope.launch {
            _etat.value = try {
                travail()
                EtatDemarrage.PRET
            } catch (erreur: CancellationException) {
                throw erreur
            } catch (erreur: Exception) {
                EtatDemarrage.ERREUR
            }
        }
    }
}
