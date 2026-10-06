package mg.ecoledimanche.presences.ui.enfants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.domain.Age
import mg.ecoledimanche.presences.ui.components.EtatVide
import mg.ecoledimanche.presences.ui.components.PhotoEnfant
import mg.ecoledimanche.presences.ui.components.libelleAge

@Composable
fun EnfantsEcran(
    etat: EtatEcranEnfants,
    onRecherche: (String) -> Unit,
    onVoirArchives: (Boolean) -> Unit,
    onOuvrir: (Long) -> Unit,
    onAjouter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = stringResource(R.string.enfants_titre),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            ChampRecherche(valeurInitiale = etat.recherche, onRecherche = onRecherche)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = !etat.archives,
                    onClick = { onVoirArchives(false) },
                    label = { Text(stringResource(R.string.filtre_actifs)) },
                )
                FilterChip(
                    selected = etat.archives,
                    onClick = { onVoirArchives(true) },
                    label = { Text(stringResource(R.string.filtre_archives)) },
                )
            }
            when {
                etat.chargement -> Unit
                etat.totalSansFiltre == 0 -> EtatVide(
                    icone = if (etat.archives) Icons.Filled.Inventory2 else Icons.Filled.Groups,
                    titre = stringResource(if (etat.archives) R.string.enfants_vide_archives else R.string.enfants_vide_actifs),
                    message = if (etat.archives) null else stringResource(R.string.enfants_vide_actifs_message),
                )
                etat.enfants.isEmpty() -> EtatVide(
                    icone = Icons.Filled.SearchOff,
                    titre = stringResource(R.string.enfants_aucun_resultat, etat.recherche.trim()),
                )
                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    items(etat.enfants, key = { it.id }) { enfant ->
                        ListItem(
                            leadingContent = {
                                PhotoEnfant(
                                    chemin = enfant.photoPath,
                                    description = stringResource(R.string.photo_de, enfant.prenom, enfant.nom),
                                    coteMaxPx = 240,
                                    forme = CircleShape,
                                    modifier = Modifier.size(56.dp),
                                )
                            },
                            headlineContent = {
                                Text("${enfant.prenom} ${enfant.nom}", style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = { Text(libelleAge(Age.enAnnees(enfant.dateNaissance, etat.aujourdhui))) },
                            modifier = Modifier.fillMaxWidth().clickable { onOuvrir(enfant.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        if (!etat.archives) {
            ExtendedFloatingActionButton(
                onClick = onAjouter,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.enfants_ajouter)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** Le champ garde son propre état : le faire repasser par un flux asynchrone ferait sauter le curseur. */
@Composable
private fun ChampRecherche(valeurInitiale: String, onRecherche: (String) -> Unit) {
    var texte by rememberSaveable { mutableStateOf(valeurInitiale) }
    OutlinedTextField(
        value = texte,
        onValueChange = {
            texte = it
            onRecherche(it)
        },
        singleLine = true,
        label = { Text(stringResource(R.string.enfants_recherche)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (texte.isNotEmpty()) {
                IconButton(onClick = { texte = ""; onRecherche("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.recherche_effacer))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
