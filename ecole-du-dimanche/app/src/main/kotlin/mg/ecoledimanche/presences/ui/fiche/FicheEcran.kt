package mg.ecoledimanche.presences.ui.fiche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.domain.FormatsFr
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.EcranErreur
import mg.ecoledimanche.presences.ui.components.PhotoEnfant
import mg.ecoledimanche.presences.ui.components.libelleAge

class ActionsFiche(
    val onRetour: () -> Unit,
    val onModifier: () -> Unit,
    val onChangerPhoto: () -> Unit,
    val onHistorique: () -> Unit,
    val onDemanderArchivage: () -> Unit,
    val onConfirmerArchivage: () -> Unit,
    val onAnnulerArchivage: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheEcran(
    etat: EtatEcranFiche,
    archivage: ArchivageUi,
    actions: ActionsFiche,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fiche_titre)) },
                navigationIcon = {
                    IconButton(onClick = actions.onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_retour))
                    }
                },
            )
        },
    ) { marges ->
        Column(modifier = Modifier.fillMaxSize().padding(marges)) {
            when (etat) {
                EtatEcranFiche.Chargement -> EcranChargement(stringResource(R.string.chargement))
                EtatEcranFiche.Introuvable -> EcranErreur(stringResource(R.string.fiche_introuvable))
                EtatEcranFiche.Erreur -> EcranErreur(stringResource(R.string.fiche_erreur))
                is EtatEcranFiche.Pret -> ContenuFiche(etat, actions)
            }
        }
    }
    if (archivage.confirmation) {
        AlertDialog(
            onDismissRequest = actions.onAnnulerArchivage,
            title = { Text(stringResource(R.string.archivage_titre)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.archivage_message))
                    if (archivage.erreur) {
                        Text(stringResource(R.string.archivage_erreur), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmerArchivage, enabled = !archivage.enCours) {
                    if (archivage.enCours) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.archivage_confirmer))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onAnnulerArchivage, enabled = !archivage.enCours) {
                    Text(stringResource(R.string.action_annuler))
                }
            },
        )
    }
}

@Composable
private fun ContenuFiche(etat: EtatEcranFiche.Pret, actions: ActionsFiche) {
    val enfant = etat.enfant
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            PhotoEnfant(
                chemin = enfant.photoPath,
                description = stringResource(R.string.photo_de, enfant.prenom, enfant.nom),
                coteMaxPx = 720,
                forme = RoundedCornerShape(24.dp),
                modifier = Modifier.size(200.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${enfant.prenom} ${enfant.nom}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(text = libelleAge(etat.age), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (enfant.isArchived) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                    Text(
                        text = stringResource(R.string.fiche_archive_badge),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            if (!etat.photoDisponible) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fiche_photo_manquante),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Actions(enfant, actions)

        Section(R.string.section_enfant) {
            Ligne(R.string.label_naissance, FormatsFr.dateCourte(enfant.dateNaissance))
            Ligne(R.string.label_sexe, stringResource(if (enfant.sexe == Sexe.GARCON) R.string.sexe_garcon else R.string.sexe_fille))
            Ligne(R.string.label_adresse, enfant.adresse ?: stringResource(R.string.non_renseigne))
        }
        Section(R.string.section_pere) {
            Ligne(R.string.label_nom, enfant.nomPere ?: stringResource(R.string.non_renseigne))
            Ligne(R.string.label_pere_membre, libelleOuiNon(enfant.pereMembre))
        }
        Section(R.string.section_mere) {
            Ligne(R.string.label_nom, enfant.nomMere ?: stringResource(R.string.non_renseigne))
            Ligne(R.string.label_mere_membre, libelleOuiNon(enfant.mereMembre))
        }
        Section(R.string.section_fratrie) {
            Ligne(R.string.label_nb_freres, enfant.nombreFreresSoeurs.toString())
            Ligne(R.string.label_nb_freres_membres, enfant.nombreFreresSoeursMembres.toString())
        }
        Section(R.string.section_eglise) {
            Ligne(R.string.label_arrivee, enfant.dateArriveeEglise?.let(FormatsFr::dateCourte) ?: stringResource(R.string.non_renseigne))
        }
        Section(R.string.section_suivi) {
            Ligne(R.string.label_debut_suivi, FormatsFr.dateCourte(enfant.dateDebutSuivi))
            enfant.dateFinSuivi?.let { Ligne(R.string.label_fin_suivi, FormatsFr.dateCourte(it)) }
        }
    }
}

@Composable
private fun Actions(enfant: EnfantEntity, actions: ActionsFiche) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = actions.onHistorique, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.History, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fiche_historique))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = actions.onModifier, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fiche_modifier))
            }
            FilledTonalButton(onClick = actions.onChangerPhoto, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fiche_changer_photo))
            }
        }
        if (!enfant.isArchived) {
            OutlinedButton(
                onClick = actions.onDemanderArchivage,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fiche_archiver))
            }
        }
    }
}

@Composable
private fun Section(titre: Int, contenu: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Text(stringResource(titre), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            contenu()
        }
    }
}

@Composable
private fun Ligne(libelle: Int, valeur: String) {
    Column {
        Text(stringResource(libelle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valeur, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun libelleOuiNon(valeur: Boolean?): String = stringResource(
    when (valeur) {
        true -> R.string.oui
        false -> R.string.non
        null -> R.string.non_renseigne
    },
)
