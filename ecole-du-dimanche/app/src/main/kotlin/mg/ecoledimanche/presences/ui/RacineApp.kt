package mg.ecoledimanche.presences.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.di.ConteneurApp
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.EcranErreur
import mg.ecoledimanche.presences.ui.navigation.NavigationApp

/** Démarrage : base chargée et rattrapage effectué, puis l'écran Dimanche s'affiche. */
@Composable
fun RacineApp(conteneur: ConteneurApp) {
    val demarrage: DemarrageViewModel = viewModel(
        factory = fabrique { DemarrageViewModel { conteneur.rattraperEtEntretenir() } },
    )
    val etat by demarrage.etat.collectAsStateWithLifecycle()

    // Retour au premier plan : les écrans affichés refont le rattrapage avant de montrer leurs données.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { conteneur.actualisation.signalerReprise() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (etat) {
            EtatDemarrage.CHARGEMENT -> EcranChargement(stringResource(R.string.demarrage_chargement))
            EtatDemarrage.ERREUR -> EcranErreur(stringResource(R.string.demarrage_erreur), onReessayer = demarrage::lancer)
            EtatDemarrage.PRET -> NavigationApp(conteneur)
        }
    }
}
