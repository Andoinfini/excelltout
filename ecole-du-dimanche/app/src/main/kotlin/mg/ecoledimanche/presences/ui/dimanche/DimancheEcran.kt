package mg.ecoledimanche.presences.ui.dimanche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.data.repository.ElementDimanche
import mg.ecoledimanche.presences.domain.Age
import mg.ecoledimanche.presences.domain.ConfigurationSeance
import mg.ecoledimanche.presences.domain.FormatsFr
import mg.ecoledimanche.presences.domain.PhaseSeance
import mg.ecoledimanche.presences.domain.Recherche
import mg.ecoledimanche.presences.domain.StatutPresence
import mg.ecoledimanche.presences.ui.components.BadgeStatut
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.EcranErreur
import mg.ecoledimanche.presences.ui.components.EtatVide
import mg.ecoledimanche.presences.ui.components.PhotoEnfant
import mg.ecoledimanche.presences.ui.components.libelleAge
import mg.ecoledimanche.presences.ui.theme.style

/** Toutes les actions de l'écran Dimanche, passées d'un bloc pour garder l'écran sans état. */
class ActionsDimanche(
    val onPrecedent: () -> Unit,
    val onSuivant: () -> Unit,
    val onChoisirDimanche: (LocalDate) -> Unit,
    val onRevenirAuDefaut: () -> Unit,
    val onOuvrirPanneau: (Long) -> Unit,
    val onFermerPanneau: () -> Unit,
    val onPointer: (StatutPresence) -> Unit,
    val onAjouter: () -> Unit,
)

@Composable
fun DimancheEcran(
    etat: EtatEcranDimanche,
    panneau: PanneauStatutUi,
    aujourdhui: LocalDate,
    actions: ActionsDimanche,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        when (etat) {
            EtatEcranDimanche.Chargement -> EcranChargement(stringResource(R.string.chargement))
            EtatEcranDimanche.Erreur -> EcranErreur(stringResource(R.string.dimanche_erreur))
            is EtatEcranDimanche.Pret -> {
                EnteteDimanche(etat, actions)
                if (etat.elements.isEmpty()) {
                    EtatVide(
                        icone = Icons.Filled.Groups,
                        titre = stringResource(R.string.dimanche_vide_titre),
                        message = stringResource(R.string.dimanche_vide_message),
                        action = {
                            Button(onClick = actions.onAjouter) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.enfants_ajouter))
                            }
                        },
                    )
                } else {
                    GrilleEnfants(etat, aujourdhui, actions)
                }
                val selection = etat.elements.firstOrNull { it.enfant.id == panneau.enfantId }
                if (selection != null) {
                    PanneauStatut(selection, etat.phase, panneau, actions.onPointer, actions.onFermerPanneau)
                }
            }
        }
    }
}

@Composable
private fun EnteteDimanche(pret: EtatEcranDimanche.Pret, actions: ActionsDimanche) {
    var selecteurOuvert by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = actions.onPrecedent, enabled = pret.peutReculer) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.dimanche_precedent))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.dimanche_choisir)) { selecteurOuvert = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = FormatsFr.dateLongueMajuscules(pret.dimanche),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            LignePhase(pret.phase)
        }
        IconButton(onClick = actions.onSuivant, enabled = pret.peutAvancer) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.dimanche_suivant))
        }
    }
    if (selecteurOuvert) {
        SelecteurDimanche(
            dimanches = pret.dimanchesProposes,
            selection = pret.dimanche,
            onChoisir = { selecteurOuvert = false; actions.onChoisirDimanche(it) },
            onRevenirAuDefaut = { selecteurOuvert = false; actions.onRevenirAuDefaut() },
            onFermer = { selecteurOuvert = false },
        )
    }
}

/** « Séance à venir » / « Pointage en cours — clôture à 10 h » / « Séance clôturée ». */
@Composable
private fun LignePhase(phase: PhaseSeance) {
    val (icone, texte) = when (phase) {
        PhaseSeance.A_VENIR -> Icons.Filled.EventAvailable to stringResource(R.string.phase_a_venir)
        PhaseSeance.EN_COURS -> Icons.Filled.Schedule to
            stringResource(R.string.phase_en_cours, FormatsFr.heure(ConfigurationSeance.HEURE_CLOTURE))
        PhaseSeance.CLOTUREE -> Icons.Filled.Lock to stringResource(R.string.phase_cloturee)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text = texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GrilleEnfants(pret: EtatEcranDimanche.Pret, aujourdhui: LocalDate, actions: ActionsDimanche) {
    val pointageActif = pret.phase != PhaseSeance.A_VENIR
    // Le nom n'est ajouté sous le prénom que pour distinguer des homonymes ; l'âge, si même nom et prénom.
    val prenomsDoubles = pret.elements.groupingBy { Recherche.normaliser(it.enfant.prenom) }.eachCount().filterValues { it > 1 }.keys
    val nomsCompletsDoubles = pret.elements
        .groupingBy { Recherche.normaliser("${it.enfant.prenom} ${it.enfant.nom}") }
        .eachCount().filterValues { it > 1 }.keys
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(pret.elements, key = { it.enfant.id }) { element ->
            val enfant = element.enfant
            CarteEnfant(
                element = element,
                afficherNom = Recherche.normaliser(enfant.prenom) in prenomsDoubles,
                afficherAge = Recherche.normaliser("${enfant.prenom} ${enfant.nom}") in nomsCompletsDoubles,
                age = Age.enAnnees(enfant.dateNaissance, aujourdhui),
                actif = pointageActif,
                onClick = { actions.onOuvrirPanneau(enfant.id) },
            )
        }
    }
}

@Composable
private fun CarteEnfant(
    element: ElementDimanche,
    afficherNom: Boolean,
    afficherAge: Boolean,
    age: Int,
    actif: Boolean,
    onClick: () -> Unit,
) {
    val style = element.statut.style()
    val enfant = element.enfant
    val description = stringResource(R.string.carte_description, enfant.prenom, enfant.nom, stringResource(style.libelle))
    Card(
        onClick = onClick,
        enabled = actif,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = style.fond,
            contentColor = style.contenu,
            disabledContainerColor = style.fond,
            disabledContentColor = style.contenu,
        ),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                PhotoEnfant(
                    chemin = enfant.photoPath,
                    description = null,
                    modifier = Modifier.fillMaxSize(),
                )
                BadgeStatut(element.statut, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = enfant.prenom,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (afficherNom) {
                Text(text = enfant.nom, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (afficherAge) {
                Text(text = libelleAge(age), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Panneau de choix du statut
// -------------------------------------------------------------------------------------------------

/**
 * Pointage : deux grands boutons (PRÉSENT vert, EN RETARD rouge) et l'action secondaire
 * « Annuler le pointage ». Après la clôture, le même panneau devient un panneau de correction
 * (PRÉSENT, EN RETARD ou ABSENT) ; « Non enregistré » n'y est plus proposé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanneauStatut(
    element: ElementDimanche,
    phase: PhaseSeance,
    panneau: PanneauStatutUi,
    onPointer: (StatutPresence) -> Unit,
    onFermer: () -> Unit,
) {
    val feuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enfant = element.enfant
    val correction = phase == PhaseSeance.CLOTUREE
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = feuille) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            PhotoEnfant(
                chemin = enfant.photoPath,
                description = stringResource(R.string.photo_de, enfant.prenom, enfant.nom),
                coteMaxPx = 720,
                forme = RoundedCornerShape(20.dp),
                modifier = Modifier.size(140.dp),
            )
            Text(
                text = "${enfant.prenom} ${enfant.nom}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.panneau_statut_actuel), style = MaterialTheme.typography.bodyLarge)
                BadgeStatut(element.statut)
            }
            if (correction) {
                Text(
                    text = stringResource(R.string.panneau_correction_titre),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BoutonStatut(StatutPresence.PRESENT, R.string.bouton_present, Icons.Filled.Check, !panneau.enregistrement) { onPointer(StatutPresence.PRESENT) }
            BoutonStatut(StatutPresence.EN_RETARD, R.string.bouton_en_retard, Icons.Filled.Schedule, !panneau.enregistrement) { onPointer(StatutPresence.EN_RETARD) }
            if (correction) {
                BoutonStatut(StatutPresence.ABSENT, R.string.bouton_absent, StatutPresence.ABSENT.style().icone, !panneau.enregistrement) { onPointer(StatutPresence.ABSENT) }
            } else if (element.statut != StatutPresence.NON_ENREGISTRE) {
                TextButton(onClick = { onPointer(StatutPresence.NON_ENREGISTRE) }, enabled = !panneau.enregistrement) {
                    Text(stringResource(R.string.action_annuler_pointage))
                }
            }
            if (panneau.enregistrement) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (panneau.erreur != null) {
                Text(
                    text = libelleErreurPointage(panneau.erreur),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BoutonStatut(
    statut: StatutPresence,
    libelle: Int,
    icone: ImageVector,
    actif: Boolean,
    onClick: () -> Unit,
) {
    val style = statut.style()
    Button(
        onClick = onClick,
        enabled = actif,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = style.fond,
            contentColor = style.contenu,
            disabledContainerColor = style.fond.copy(alpha = 0.45f),
            disabledContentColor = style.contenu.copy(alpha = 0.8f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = stringResource(libelle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun libelleErreurPointage(erreur: ErreurPointage): String = stringResource(
    when (erreur) {
        ErreurPointage.ECRITURE -> R.string.erreur_pointage_ecriture
        ErreurPointage.SEANCE_A_VENIR -> R.string.erreur_pointage_a_venir
        ErreurPointage.ENFANT_NON_ADMISSIBLE -> R.string.erreur_pointage_non_admissible
        ErreurPointage.ABSENT_INTERDIT_AVANT_CLOTURE -> R.string.erreur_pointage_absent_interdit
        ErreurPointage.ANNULATION_INTERDITE_APRES_CLOTURE -> R.string.erreur_pointage_annulation
    },
)

// -------------------------------------------------------------------------------------------------
// Sélecteur de dimanche
// -------------------------------------------------------------------------------------------------

@Composable
private fun SelecteurDimanche(
    dimanches: List<LocalDate>,
    selection: LocalDate,
    onChoisir: (LocalDate) -> Unit,
    onRevenirAuDefaut: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(stringResource(R.string.dimanche_choisir)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(dimanches, key = { it.toEpochDay() }) { dimanche ->
                    val choisi = dimanche == selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoisir(dimanche) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = FormatsFr.dateLongue(dimanche).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (choisi) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (choisi) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onRevenirAuDefaut) { Text(stringResource(R.string.dimanche_revenir)) } },
        dismissButton = { TextButton(onClick = onFermer) { Text(stringResource(R.string.action_fermer)) } },
    )
}
