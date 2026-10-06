@file:OptIn(ExperimentalMaterial3Api::class)

package mg.ecoledimanche.presences.ui.formulaire

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.domain.ChampEnfant
import mg.ecoledimanche.presences.domain.ErreurChamp
import mg.ecoledimanche.presences.domain.FormatsFr
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.ui.components.EcranChargement
import mg.ecoledimanche.presences.ui.components.PhotoEnfant
import mg.ecoledimanche.presences.ui.components.libelleAge

class ActionsFormulaire(
    val onRetour: () -> Unit,
    val onChamps: ((ChampsFormulaire) -> ChampsFormulaire) -> Unit,
    val onReprendrePhoto: () -> Unit,
    val onEnregistrer: () -> Unit,
)

@Composable
fun FormulaireEcran(
    etat: EtatFormulaire,
    aujourdhui: LocalDate,
    actions: ActionsFormulaire,
    modifier: Modifier = Modifier,
) {
    val champs = etat.champs
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (etat.modeAjout) R.string.formulaire_titre_ajout else R.string.formulaire_titre_modif))
                },
                navigationIcon = {
                    IconButton(onClick = actions.onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_retour))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Button(
                    onClick = actions.onEnregistrer,
                    enabled = !etat.enregistrement && !etat.chargement,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 56.dp),
                ) {
                    if (etat.enregistrement) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.formulaire_enregistrer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { marges ->
        if (etat.chargement) {
            EcranChargement(stringResource(R.string.chargement), Modifier.padding(marges))
            return@Scaffold
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(marges).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // --- Photo -------------------------------------------------------------------------
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PhotoEnfant(
                    chemin = etat.photo,
                    description = stringResource(R.string.formulaire_photo_description),
                    coteMaxPx = 480,
                    forme = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(140.dp),
                )
                if (etat.modeAjout) {
                    TextButton(onClick = actions.onReprendrePhoto, enabled = !etat.enregistrement) {
                        Text(stringResource(R.string.formulaire_reprendre_photo))
                    }
                }
            }

            // --- Enfant ------------------------------------------------------------------------
            SectionFormulaire(R.string.section_enfant) {
                ChampNom(R.string.label_nom, champs.nom, true, etat.erreurs[ChampEnfant.NOM]) { v -> actions.onChamps { it.copy(nom = v) } }
                ChampNom(R.string.label_prenom, champs.prenom, true, etat.erreurs[ChampEnfant.PRENOM]) { v -> actions.onChamps { it.copy(prenom = v) } }
                ChampDate(
                    libelle = R.string.label_naissance,
                    obligatoire = true,
                    valeur = champs.dateNaissance,
                    erreur = etat.erreurs[ChampEnfant.DATE_NAISSANCE],
                    dateMin = null,
                    dateMax = aujourdhui,
                    dateAffichageParDefaut = aujourdhui.minusYears(7),
                    effacable = false,
                    onChoisir = { d -> actions.onChamps { it.copy(dateNaissance = d) } },
                )
                AgeLectureSeule(etat.age)
                ChoixSexe(champs.sexe, etat.erreurs[ChampEnfant.SEXE]) { v -> actions.onChamps { it.copy(sexe = v) } }
                ChampTexte(R.string.label_adresse, champs.adresse, etat.erreurs[ChampEnfant.ADRESSE], multiligne = true) { v ->
                    actions.onChamps { it.copy(adresse = v) }
                }
            }

            // --- Père --------------------------------------------------------------------------
            SectionFormulaire(R.string.section_pere) {
                ChampNom(R.string.label_nom_pere, champs.nomPere, false, etat.erreurs[ChampEnfant.NOM_PERE]) { v -> actions.onChamps { it.copy(nomPere = v) } }
                ChoixOuiNon(R.string.label_pere_membre, champs.pereMembre) { v -> actions.onChamps { it.copy(pereMembre = v) } }
            }

            // --- Mère --------------------------------------------------------------------------
            SectionFormulaire(R.string.section_mere) {
                ChampNom(R.string.label_nom_mere, champs.nomMere, false, etat.erreurs[ChampEnfant.NOM_MERE]) { v -> actions.onChamps { it.copy(nomMere = v) } }
                ChoixOuiNon(R.string.label_mere_membre, champs.mereMembre) { v -> actions.onChamps { it.copy(mereMembre = v) } }
            }

            // --- Frères et sœurs ---------------------------------------------------------------
            SectionFormulaire(R.string.section_fratrie) {
                ChampNombre(R.string.label_nb_freres, champs.nombreFreresSoeurs, etat.erreurs[ChampEnfant.NB_FRERES_SOEURS]) { v ->
                    actions.onChamps { it.copy(nombreFreresSoeurs = v) }
                }
                ChampNombre(R.string.label_nb_freres_membres, champs.nombreFreresSoeursMembres, etat.erreurs[ChampEnfant.NB_FRERES_SOEURS_MEMBRES]) { v ->
                    actions.onChamps { it.copy(nombreFreresSoeursMembres = v) }
                }
            }

            // --- Église ------------------------------------------------------------------------
            SectionFormulaire(R.string.section_eglise) {
                ChampDate(
                    libelle = R.string.label_arrivee,
                    obligatoire = false,
                    valeur = champs.dateArrivee,
                    erreur = etat.erreurs[ChampEnfant.DATE_ARRIVEE],
                    dateMin = champs.dateNaissance,
                    dateMax = aujourdhui,
                    dateAffichageParDefaut = aujourdhui,
                    effacable = true,
                    onChoisir = { d -> actions.onChamps { it.copy(dateArrivee = d) } },
                )
            }

            val erreurGenerale = etat.erreurEnregistrement
            if (erreurGenerale != null) {
                Text(
                    text = stringResource(
                        when (erreurGenerale) {
                            ErreurEnregistrement.ECRITURE -> R.string.erreur_formulaire_ecriture
                            ErreurEnregistrement.PHOTO_ABSENTE -> R.string.erreur_photo_absente
                            ErreurEnregistrement.INTROUVABLE -> R.string.fiche_introuvable
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionFormulaire(titre: Int, contenu: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Text(stringResource(titre), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            contenu()
        }
    }
}

@Composable
private fun messageErreur(erreur: ErreurChamp?): String? = erreur?.let {
    stringResource(
        when (it) {
            ErreurChamp.OBLIGATOIRE -> R.string.erreur_obligatoire
            ErreurChamp.DATE_FUTURE -> R.string.erreur_date_future
            ErreurChamp.ARRIVEE_AVANT_NAISSANCE -> R.string.erreur_arrivee_avant_naissance
            ErreurChamp.NOMBRE_INVALIDE -> R.string.erreur_nombre_invalide
            ErreurChamp.MEMBRES_SUPERIEUR_AU_TOTAL -> R.string.erreur_membres_superieur
            ErreurChamp.TROP_LONG -> R.string.erreur_trop_long
        },
    )
}

@Composable
private fun libelleChamp(libelle: Int, obligatoire: Boolean): String =
    if (obligatoire) stringResource(R.string.label_obligatoire, stringResource(libelle)) else stringResource(libelle)

@Composable
private fun ChampNom(libelle: Int, valeur: String, obligatoire: Boolean, erreur: ErreurChamp?, onChange: (String) -> Unit) {
    val message = messageErreur(erreur)
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelleChamp(libelle, obligatoire)) },
        isError = message != null,
        supportingText = message?.let { texte -> { Text(texte) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChampTexte(libelle: Int, valeur: String, erreur: ErreurChamp?, multiligne: Boolean, onChange: (String) -> Unit) {
    val message = messageErreur(erreur)
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(stringResource(libelle)) },
        isError = message != null,
        supportingText = message?.let { texte -> { Text(texte) } },
        minLines = if (multiligne) 2 else 1,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChampNombre(libelle: Int, valeur: String, erreur: ErreurChamp?, onChange: (String) -> Unit) {
    val message = messageErreur(erreur)
    OutlinedTextField(
        value = valeur,
        // On ne laisse taper que des chiffres : jamais de signe ni de décimale ; la validation reste le filet final.
        onValueChange = { saisie -> onChange(saisie.filter { it in '0'..'9' }.take(2)) },
        label = { Text(stringResource(libelle)) },
        isError = message != null,
        supportingText = message?.let { texte -> { Text(texte) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Âge calculé depuis la date de naissance : en lecture seule, jamais saisi ni enregistré. */
@Composable
private fun AgeLectureSeule(age: Int?) {
    OutlinedTextField(
        value = age?.let { libelleAge(it) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(stringResource(R.string.label_age_calcule)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoixSexe(valeur: Sexe?, erreur: ErreurChamp?, onChange: (Sexe) -> Unit) {
    val message = messageErreur(erreur)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(libelleChamp(R.string.label_sexe, true), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = valeur == Sexe.GARCON, onClick = { onChange(Sexe.GARCON) }, label = { Text(stringResource(R.string.sexe_garcon)) })
            FilterChip(selected = valeur == Sexe.FILLE, onClick = { onChange(Sexe.FILLE) }, label = { Text(stringResource(R.string.sexe_fille)) })
        }
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Oui / Non / non renseigné : toucher à nouveau le choix actif le retire (l'inconnu n'est jamais enregistré comme « Non »). */
@Composable
private fun ChoixOuiNon(libelle: Int, valeur: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(libelle), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = valeur == true, onClick = { onChange(if (valeur == true) null else true) }, label = { Text(stringResource(R.string.oui)) })
            FilterChip(selected = valeur == false, onClick = { onChange(if (valeur == false) null else false) }, label = { Text(stringResource(R.string.non)) })
            if (valeur == null) {
                Text(stringResource(R.string.non_renseigne), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Date affichée au format JJ/MM/AAAA, choisie dans un calendrier (qui accepte aussi la saisie au clavier). */
@Composable
private fun ChampDate(
    libelle: Int,
    obligatoire: Boolean,
    valeur: LocalDate?,
    erreur: ErreurChamp?,
    dateMin: LocalDate?,
    dateMax: LocalDate,
    dateAffichageParDefaut: LocalDate,
    effacable: Boolean,
    onChoisir: (LocalDate?) -> Unit,
) {
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val message = messageErreur(erreur)
    val source = remember { MutableInteractionSource() }
    // Un champ en lecture seule ne reçoit pas de clic : on ouvre le calendrier au relâchement du doigt.
    LaunchedEffect(source) {
        source.interactions.collect { interaction -> if (interaction is PressInteraction.Release) ouvert = true }
    }
    OutlinedTextField(
        value = valeur?.let { FormatsFr.dateCourte(it) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        interactionSource = source,
        label = { Text(libelleChamp(libelle, obligatoire)) },
        placeholder = { Text(stringResource(R.string.date_placeholder)) },
        isError = message != null,
        supportingText = message?.let { texte -> { Text(texte) } },
        singleLine = true,
        trailingIcon = {
            Row {
                if (effacable && valeur != null) {
                    IconButton(onClick = { onChoisir(null) }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.date_effacer))
                    }
                }
                IconButton(onClick = { ouvert = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.date_choisir))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (ouvert) {
        val bornes = remember(dateMin, dateMax) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val jour = enDate(utcTimeMillis)
                    return !jour.isAfter(dateMax) && (dateMin == null || !jour.isBefore(dateMin))
                }

                override fun isSelectableYear(year: Int): Boolean =
                    year <= dateMax.year && (dateMin == null || year >= dateMin.year)
            }
        }
        val etat = rememberDatePickerState(
            initialSelectedDateMillis = valeur?.let { enMillis(it) },
            initialDisplayedMonthMillis = enMillis(valeur ?: dateAffichageParDefaut),
            yearRange = (dateMax.year - 120)..dateMax.year,
            selectableDates = bornes,
        )
        DatePickerDialog(
            onDismissRequest = { ouvert = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        etat.selectedDateMillis?.let { onChoisir(enDate(it)) }
                        ouvert = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { ouvert = false }) { Text(stringResource(R.string.action_annuler)) } },
        ) {
            DatePicker(state = etat)
        }
    }
}

/** Le calendrier Material travaille en millisecondes UTC à minuit : on convertit sans jamais décaler d'un jour. */
private fun enMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun enDate(millisUtc: Long): LocalDate = Instant.ofEpochMilli(millisUtc).atZone(ZoneOffset.UTC).toLocalDate()
