package mg.ecoledimanche.presences.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.domain.StatutPresence
import mg.ecoledimanche.presences.ui.theme.style

/** « 9 ans », « 1 an », « 0 an » (le pluriel français commence à 2). */
@Composable
fun libelleAge(age: Int): String =
    if (age <= 1) stringResource(R.string.age_an, age) else stringResource(R.string.age_ans, age)

/** Pastille de statut : icône + libellé + couleur (la couleur seule ne suffit pas). */
@Composable
fun BadgeStatut(statut: StatutPresence, modifier: Modifier = Modifier) {
    val style = statut.style()
    Surface(color = style.fond, contentColor = style.contenu, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(imageVector = style.icone, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = stringResource(style.libelle), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** État vide lisible : icône, titre, explication et action éventuelle. */
@Composable
fun EtatVide(
    icone: ImageVector,
    titre: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(PaddingValues(horizontal = 32.dp, vertical = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun EcranChargement(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Message d'erreur compréhensible avec, si possible, un bouton « Réessayer ». */
@Composable
fun EcranErreur(message: String, modifier: Modifier = Modifier, onReessayer: (() -> Unit)? = null) {
    EtatVide(
        icone = Icons.Filled.ErrorOutline,
        titre = message,
        modifier = modifier,
        action = onReessayer?.let { reessayer ->
            { Button(onClick = reessayer) { Text(stringResource(R.string.action_reessayer)) } }
        },
    )
}
