package mg.ecoledimanche.presences.ui.assiduite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.data.repository.LigneAssiduite
import mg.ecoledimanche.presences.domain.CompteursAssiduite
import mg.ecoledimanche.presences.domain.FormatTaux
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.EcranErreur
import mg.ecoledimanche.presences.ui.components.EtatVide
import mg.ecoledimanche.presences.ui.components.PhotoEnfant

@Composable
fun AssiduiteEcran(
    etat: EtatEcranAssiduite,
    onVoirArchives: (Boolean) -> Unit,
    onOuvrirHistorique: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(R.string.assiduite_titre),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        val archives = (etat as? EtatEcranAssiduite.Pret)?.archives ?: false
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            FilterChip(selected = !archives, onClick = { onVoirArchives(false) }, label = { Text(stringResource(R.string.filtre_actifs)) })
            FilterChip(selected = archives, onClick = { onVoirArchives(true) }, label = { Text(stringResource(R.string.filtre_archives)) })
        }
        when (etat) {
            EtatEcranAssiduite.Chargement -> EcranChargement(stringResource(R.string.chargement))
            EtatEcranAssiduite.Erreur -> EcranErreur(stringResource(R.string.assiduite_erreur))
            is EtatEcranAssiduite.Pret -> if (etat.lignes.isEmpty()) {
                EtatVide(
                    icone = if (etat.archives) Icons.Filled.Inventory2 else Icons.Filled.Insights,
                    titre = stringResource(if (etat.archives) R.string.enfants_vide_archives else R.string.enfants_vide_actifs),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                    items(etat.lignes, key = { it.enfant.id }) { ligne ->
                        LigneEnfantAssiduite(ligne, onClick = { onOuvrirHistorique(ligne.enfant.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneEnfantAssiduite(ligne: LigneAssiduite, onClick: () -> Unit) {
    val enfant = ligne.enfant
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
        headlineContent = { Text("${enfant.prenom} ${enfant.nom}", style = MaterialTheme.typography.titleMedium) },
        supportingContent = { ResumeCompteurs(ligne.compteurs) },
        trailingContent = { TauxAssiduite(ligne.compteurs) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** « Présences 8 · Retards 1 · Absences 1 », ou « Aucune séance clôturée ». */
@Composable
fun ResumeCompteurs(compteurs: CompteursAssiduite) {
    if (compteurs.total == 0) {
        Text(stringResource(R.string.assiduite_aucune_seance))
    } else {
        Text(stringResource(R.string.assiduite_compteurs, compteurs.presences, compteurs.retards, compteurs.absences))
    }
}

/** Taux avec au plus une décimale et une virgule : « 87,5 % », « 100 % » ; « — » sans séance clôturée. */
@Composable
fun TauxAssiduite(compteurs: CompteursAssiduite, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = FormatTaux.format(compteurs.tauxDixiemes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
    }
}
