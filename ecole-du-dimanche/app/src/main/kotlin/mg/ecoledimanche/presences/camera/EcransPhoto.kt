package mg.ecoledimanche.presences.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.ui.components.EtatVide
import mg.ecoledimanche.presences.ui.components.PhotoEnfant

/** Prévisualisation avant validation : « Reprendre » ou « Utiliser cette photo ». */
@Composable
fun ApercuPhoto(
    chemin: String,
    onReprendre: () -> Unit,
    onUtiliser: () -> Unit,
    modifier: Modifier = Modifier,
    enCours: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.apercu_titre),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        PhotoEnfant(
            chemin = chemin,
            description = stringResource(R.string.apercu_description),
            coteMaxPx = 1280,
            forme = RoundedCornerShape(20.dp),
            echelle = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReprendre,
                enabled = !enCours,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.apercu_reprendre)) }
            Button(
                onClick = onUtiliser,
                enabled = !enCours,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.apercu_utiliser), fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Permission caméra refusée. Les autres fonctions de l'application restent accessibles :
 * « Réessayer » tant que le refus n'est pas définitif, « Ouvrir les paramètres » ensuite,
 * et toujours « Annuler l'ajout ».
 */
@Composable
fun ExplicationPermissionCamera(
    refusDefinitif: Boolean,
    onReessayer: () -> Unit,
    onOuvrirParametres: () -> Unit,
    onAnnuler: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        EtatVide(
            icone = Icons.Filled.NoPhotography,
            titre = stringResource(R.string.permission_titre),
            message = stringResource(if (refusDefinitif) R.string.permission_refus_definitif else R.string.permission_refus_temporaire),
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (refusDefinitif) {
                        Button(onClick = onOuvrirParametres, modifier = Modifier.heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.permission_parametres))
                        }
                    } else {
                        Button(onClick = onReessayer, modifier = Modifier.heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.permission_autoriser))
                        }
                    }
                    TextButton(onClick = onAnnuler) { Text(stringResource(R.string.permission_annuler)) }
                }
            },
        )
    }
}

/** Caméra absente ou en erreur : message compréhensible et retour possible, aucune fiche créée. */
@Composable
fun CameraIndisponible(onRetour: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        EtatVide(
            icone = Icons.Filled.NoPhotography,
            titre = stringResource(R.string.camera_indisponible),
            message = stringResource(R.string.camera_indisponible_aide),
            action = { Button(onClick = onRetour, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.action_retour)) } },
        )
    }
}

@Composable
fun MessageSousCamera(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(16.dp),
    )
}
