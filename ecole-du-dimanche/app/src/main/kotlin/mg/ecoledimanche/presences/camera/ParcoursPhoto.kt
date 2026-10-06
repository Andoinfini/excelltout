package mg.ecoledimanche.presences.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.ui.components.EcranChargement

/**
 * Parcours photo : permission, caméra, capture, prévisualisation (« Reprendre » /
 * « Utiliser cette photo »). Rien n'est enregistré en base ici : [onValidee] reçoit seulement le
 * chemin de la photo TEMPORAIRE validée. Annuler ne crée aucune fiche.
 */
@Composable
fun ParcoursPhoto(
    stockage: StockagePhotosPrive,
    onValidee: (cheminTemporaire: String) -> Unit,
    onAnnuler: () -> Unit,
    modifier: Modifier = Modifier,
    enCours: Boolean = false,
    messageInitial: String? = null,
) {
    var capturee by rememberSaveable { mutableStateOf<String?>(null) }
    var traitement by remember { mutableStateOf(false) }
    var erreur by rememberSaveable { mutableStateOf<ErreurCamera?>(null) }
    var message by rememberSaveable { mutableStateOf(messageInitial) }
    val portee = rememberCoroutineScope()

    // Une photo restaurée après recréation du processus peut avoir disparu : on revient à la caméra.
    LaunchedEffect(capturee) {
        val chemin = capturee ?: return@LaunchedEffect
        if (!stockage.existe(chemin)) capturee = null
    }

    fun reprendre() {
        val chemin = capturee
        capturee = null
        if (chemin != null) portee.launch { withContext(NonCancellable) { stockage.supprimer(chemin) } }
    }

    BackHandler(enabled = capturee != null && !enCours) { reprendre() }

    Box(modifier = modifier.fillMaxSize()) {
        val chemin = capturee
        when {
            chemin != null -> ApercuPhoto(
                chemin = chemin,
                enCours = enCours,
                onReprendre = ::reprendre,
                onUtiliser = { onValidee(chemin) },
            )
            erreur == ErreurCamera.INDISPONIBLE -> CameraIndisponible(onRetour = onAnnuler)
            traitement -> EcranChargement(stringResource(R.string.camera_traitement))
            else -> PorteCamera(onAnnuler = onAnnuler) {
                CameraCapture(
                    stockage = stockage,
                    onCapture = { brut ->
                        traitement = true
                        portee.launch {
                            try {
                                capturee = stockage.traiterPhotoCapturee(brut)
                            } catch (annulation: CancellationException) {
                                throw annulation
                            } catch (echec: Exception) {
                                erreur = ErreurCamera.CAPTURE
                            } finally {
                                traitement = false
                            }
                        }
                    },
                    onErreur = { erreur = it },
                    onFermer = onAnnuler,
                )
            }
        }
    }

    if (erreur == ErreurCamera.CAPTURE) {
        AlertDialog(
            onDismissRequest = { erreur = null },
            text = { Text(stringResource(R.string.camera_erreur_capture)) },
            confirmButton = { TextButton(onClick = { erreur = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    } else if (message != null && capturee == null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
