package mg.ecoledimanche.presences.ui.historique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.data.repository.HistoriqueEnfant
import mg.ecoledimanche.presences.domain.FormatsFr
import mg.ecoledimanche.presences.ui.assiduite.ResumeCompteurs
import mg.ecoledimanche.presences.ui.assiduite.TauxAssiduite
import mg.ecoledimanche.presences.ui.components.BadgeStatut
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.EcranErreur
import mg.ecoledimanche.presences.ui.components.EtatVide
import mg.ecoledimanche.presences.ui.components.PhotoEnfant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriqueEcran(
    etat: EtatEcranHistorique,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historique_titre)) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_retour))
                    }
                },
            )
        },
    ) { marges ->
        Column(modifier = Modifier.fillMaxSize().padding(marges)) {
            when (etat) {
                EtatEcranHistorique.Chargement -> EcranChargement(stringResource(R.string.chargement))
                EtatEcranHistorique.Erreur -> EcranErreur(stringResource(R.string.historique_erreur))
                EtatEcranHistorique.Introuvable -> EcranErreur(stringResource(R.string.fiche_introuvable))
                is EtatEcranHistorique.Pret -> ContenuHistorique(etat.historique)
            }
        }
    }
}

@Composable
private fun ContenuHistorique(historique: HistoriqueEnfant) {
    val enfant = historique.enfant
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    PhotoEnfant(
                        chemin = enfant.photoPath,
                        description = stringResource(R.string.photo_de, enfant.prenom, enfant.nom),
                        coteMaxPx = 360,
                        forme = CircleShape,
                        modifier = Modifier.size(72.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${enfant.prenom} ${enfant.nom}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        ResumeCompteurs(historique.compteurs)
                    }
                    TauxAssiduite(historique.compteurs)
                }
            }
        }
        if (historique.elements.isEmpty()) {
            item {
                val message = if (enfant.isArchived) {
                    stringResource(R.string.historique_vide_archive)
                } else {
                    stringResource(R.string.historique_vide_debut, FormatsFr.dateLongue(enfant.dateDebutSuivi))
                }
                EtatVide(
                    icone = Icons.Filled.EventBusy,
                    titre = stringResource(R.string.historique_vide_titre),
                    message = message,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            items(historique.elements, key = { it.dimanche.toEpochDay() }) { element ->
                ListItem(
                    headlineContent = { Text(FormatsFr.dateCourte(element.dimanche), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(FormatsFr.dateLongue(element.dimanche).replaceFirstChar { it.uppercase() }) },
                    trailingContent = { BadgeStatut(element.statut) },
                )
                HorizontalDivider()
            }
        }
    }
}
