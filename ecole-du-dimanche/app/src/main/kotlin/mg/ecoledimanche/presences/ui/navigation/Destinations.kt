package mg.ecoledimanche.presences.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.camera.ParcoursPhoto
import mg.ecoledimanche.presences.di.ConteneurApp
import mg.ecoledimanche.presences.domain.aujourdhui
import mg.ecoledimanche.presences.ui.assiduite.AssiduiteEcran
import mg.ecoledimanche.presences.ui.assiduite.AssiduiteViewModel
import mg.ecoledimanche.presences.ui.dimanche.ActionsDimanche
import mg.ecoledimanche.presences.ui.dimanche.DimancheEcran
import mg.ecoledimanche.presences.ui.dimanche.DimancheViewModel
import mg.ecoledimanche.presences.ui.enfants.EnfantsEcran
import mg.ecoledimanche.presences.ui.enfants.EnfantsViewModel
import mg.ecoledimanche.presences.ui.fabrique
import mg.ecoledimanche.presences.ui.fiche.ActionsFiche
import mg.ecoledimanche.presences.ui.fiche.ChangerPhotoViewModel
import mg.ecoledimanche.presences.ui.fiche.FicheEcran
import mg.ecoledimanche.presences.ui.fiche.FicheViewModel
import mg.ecoledimanche.presences.ui.formulaire.ActionsFormulaire
import mg.ecoledimanche.presences.ui.formulaire.FormulaireEcran
import mg.ecoledimanche.presences.ui.formulaire.FormulaireViewModel
import mg.ecoledimanche.presences.ui.historique.HistoriqueEcran
import mg.ecoledimanche.presences.ui.historique.HistoriqueViewModel

// Ce fichier ne fait que relier les ViewModels, le cycle de vie Android et les écrans sans état.
// Aucune règle métier ici : elles vivent dans `domain` et `data.repository`.

@Composable
fun DimancheRoute(conteneur: ConteneurApp, onAjouter: () -> Unit) {
    val vm: DimancheViewModel = viewModel(
        factory = fabrique { DimancheViewModel(conteneur.presenceRepository, conteneur.horloge, conteneur.actualisation.flux) },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    val panneau by vm.panneau.collectAsStateWithLifecycle()
    DimancheEcran(
        etat = etat,
        panneau = panneau,
        aujourdhui = conteneur.horloge.aujourdhui(),
        actions = ActionsDimanche(
            onPrecedent = vm::dimanchePrecedent,
            onSuivant = vm::dimancheSuivant,
            onChoisirDimanche = vm::choisirDimanche,
            onRevenirAuDefaut = vm::revenirAuDimancheParDefaut,
            onOuvrirPanneau = vm::ouvrirPanneau,
            onFermerPanneau = vm::fermerPanneau,
            onPointer = vm::pointer,
            onAjouter = onAjouter,
        ),
    )
}

@Composable
fun EnfantsRoute(conteneur: ConteneurApp, onOuvrir: (Long) -> Unit, onAjouter: () -> Unit) {
    val vm: EnfantsViewModel = viewModel(factory = fabrique { EnfantsViewModel(conteneur.enfantRepository, conteneur.horloge) })
    val etat by vm.etat.collectAsStateWithLifecycle()
    EnfantsEcran(
        etat = etat,
        onRecherche = vm::rechercher,
        onVoirArchives = vm::voirArchives,
        onOuvrir = onOuvrir,
        onAjouter = onAjouter,
    )
}

@Composable
fun AssiduiteRoute(conteneur: ConteneurApp, onOuvrirHistorique: (Long) -> Unit) {
    val vm: AssiduiteViewModel = viewModel(
        factory = fabrique { AssiduiteViewModel(conteneur.presenceRepository, conteneur.actualisation.flux) },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    AssiduiteEcran(etat = etat, onVoirArchives = vm::voirArchives, onOuvrirHistorique = onOuvrirHistorique)
}

@Composable
fun HistoriqueRoute(conteneur: ConteneurApp, enfantId: Long, onRetour: () -> Unit) {
    val vm: HistoriqueViewModel = viewModel(
        factory = fabrique { HistoriqueViewModel(conteneur.presenceRepository, enfantId, conteneur.actualisation.flux) },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    HistoriqueEcran(etat = etat, onRetour = onRetour)
}

@Composable
fun FicheRoute(
    conteneur: ConteneurApp,
    enfantId: Long,
    onRetour: () -> Unit,
    onModifier: () -> Unit,
    onChangerPhoto: () -> Unit,
    onHistorique: () -> Unit,
) {
    val vm: FicheViewModel = viewModel(
        factory = fabrique { FicheViewModel(conteneur.enfantRepository, conteneur.stockagePhotos, enfantId, conteneur.horloge) },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    val archivage by vm.archivage.collectAsStateWithLifecycle()
    FicheEcran(
        etat = etat,
        archivage = archivage,
        actions = ActionsFiche(
            onRetour = onRetour,
            onModifier = onModifier,
            onChangerPhoto = onChangerPhoto,
            onHistorique = onHistorique,
            onDemanderArchivage = vm::demanderArchivage,
            onConfirmerArchivage = vm::confirmerArchivage,
            onAnnulerArchivage = vm::annulerArchivage,
        ),
    )
}

/**
 * Ajout d'un enfant, photo en premier : caméra, aperçu (« Reprendre » / « Utiliser cette photo »),
 * puis formulaire. Aucune fiche n'existe tant que « ENREGISTRER L'ENFANT » n'a pas réussi.
 */
@Composable
fun AjoutRoute(conteneur: ConteneurApp, onTermine: (Long) -> Unit, onAbandon: () -> Unit) {
    val vm: FormulaireViewModel = viewModel(
        factory = fabrique {
            FormulaireViewModel(conteneur.enfantRepository, conteneur.stockagePhotos, conteneur.horloge, null, createSavedStateHandle())
        },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    var confirmerAbandon by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(etat.enfantEnregistre) {
        etat.enfantEnregistre?.let(onTermine)
    }

    if (etat.photo == null) {
        ParcoursPhoto(
            stockage = conteneur.stockagePhotos,
            onValidee = vm::definirPhoto,
            onAnnuler = {
                vm.abandonner()
                onAbandon()
            },
            messageInitial = if (etat.photoTemporairePerdue) stringResource(R.string.photo_temporaire_perdue) else null,
        )
    } else {
        BackHandler { confirmerAbandon = true }
        FormulaireEcran(
            etat = etat,
            aujourdhui = conteneur.horloge.aujourdhui(),
            actions = ActionsFormulaire(
                onRetour = { confirmerAbandon = true },
                onChamps = vm::modifierChamps,
                onReprendrePhoto = vm::reprendrePhoto,
                onEnregistrer = vm::enregistrer,
            ),
        )
        if (confirmerAbandon) {
            AlertDialog(
                onDismissRequest = { confirmerAbandon = false },
                title = { Text(stringResource(R.string.abandon_titre)) },
                text = { Text(stringResource(R.string.abandon_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmerAbandon = false
                            vm.abandonner()
                            onAbandon()
                        },
                    ) { Text(stringResource(R.string.abandon_confirmer)) }
                },
                dismissButton = { TextButton(onClick = { confirmerAbandon = false }) { Text(stringResource(R.string.abandon_continuer)) } },
            )
        }
    }
}

@Composable
fun ModifierRoute(conteneur: ConteneurApp, enfantId: Long, onTermine: () -> Unit, onRetour: () -> Unit) {
    val vm: FormulaireViewModel = viewModel(
        factory = fabrique {
            FormulaireViewModel(conteneur.enfantRepository, conteneur.stockagePhotos, conteneur.horloge, enfantId, createSavedStateHandle())
        },
    )
    val etat by vm.etat.collectAsStateWithLifecycle()
    LaunchedEffect(etat.enfantEnregistre) {
        if (etat.enfantEnregistre != null) onTermine()
    }
    FormulaireEcran(
        etat = etat,
        aujourdhui = conteneur.horloge.aujourdhui(),
        actions = ActionsFormulaire(
            onRetour = onRetour,
            onChamps = vm::modifierChamps,
            onReprendrePhoto = {},
            onEnregistrer = vm::enregistrer,
        ),
    )
}

/** Remplacement de la photo : l'ancienne n'est supprimée qu'une fois la nouvelle référence enregistrée. */
@Composable
fun ChangerPhotoRoute(conteneur: ConteneurApp, enfantId: Long, onTermine: () -> Unit, onAnnuler: () -> Unit) {
    val vm: ChangerPhotoViewModel = viewModel(factory = fabrique { ChangerPhotoViewModel(conteneur.enfantRepository, enfantId) })
    val etat by vm.etat.collectAsStateWithLifecycle()
    LaunchedEffect(etat.termine) {
        if (etat.termine) onTermine()
    }
    ParcoursPhoto(
        stockage = conteneur.stockagePhotos,
        onValidee = vm::valider,
        onAnnuler = onAnnuler,
        enCours = etat.enCours,
    )
    if (etat.erreur) {
        AlertDialog(
            onDismissRequest = vm::effacerErreur,
            text = { Text(stringResource(R.string.erreur_remplacement_photo)) },
            confirmButton = { TextButton(onClick = vm::effacerErreur) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
