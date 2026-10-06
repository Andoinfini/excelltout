package mg.ecoledimanche.presences.ui.formulaire

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.data.repository.StockagePhotos
import mg.ecoledimanche.presences.domain.Age
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.ChampEnfant
import mg.ecoledimanche.presences.domain.ErreurChamp
import mg.ecoledimanche.presences.domain.ResultatValidation
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.domain.ValidationEnfant
import mg.ecoledimanche.presences.domain.aujourdhui

enum class ErreurEnregistrement { ECRITURE, PHOTO_ABSENTE, INTROUVABLE }

data class EtatFormulaire(
    val modeAjout: Boolean,
    val champs: ChampsFormulaire = ChampsFormulaire(),
    val erreurs: Map<ChampEnfant, ErreurChamp> = emptyMap(),
    /** Ajout : photo temporaire validée (nulle tant qu'elle n'a pas été prise). Modification : photo actuelle. */
    val photo: String? = null,
    /** Âge calculé, en lecture seule. */
    val age: Int? = null,
    val chargement: Boolean = false,
    val enregistrement: Boolean = false,
    val erreurEnregistrement: ErreurEnregistrement? = null,
    /** Vrai si la photo temporaire a disparu (ex. nettoyage) alors que le formulaire était récupéré. */
    val photoTemporairePerdue: Boolean = false,
    /** Identifiant de l'enfant une fois enregistré : déclenche la navigation. */
    val enfantEnregistre: Long? = null,
) {
    /** Vrai s'il y a quelque chose à perdre si l'utilisateur abandonne l'ajout. */
    val aDuContenu: Boolean get() = photo != null || champs != ChampsFormulaire()
}

/**
 * Formulaire d'ajout (enfantId nul) ou de modification.
 *
 * Toutes les valeurs sont persistées dans le [SavedStateHandle] : elles survivent à une rotation
 * de l'écran et, quand Android le permet, à la recréation du processus. La photo temporaire est
 * un fichier du stockage privé référencé par son chemin.
 */
class FormulaireViewModel(
    private val enfants: EnfantRepository,
    private val photos: StockagePhotos,
    private val horloge: AppClock,
    private val enfantId: Long?,
    private val sauvegarde: SavedStateHandle,
) : ViewModel() {
    private val modeAjout = enfantId == null

    /** Devient vrai après une première tentative refusée : les erreurs suivent alors la saisie. */
    private var validationActive: Boolean = sauvegarde.get<Boolean>(CLE_VALIDATION) ?: false

    private val dejaInitialise: Boolean = sauvegarde.contains(CLE_INITIALISE)

    private val _etat = MutableStateFlow(
        run {
            val champs = if (modeAjout || dejaInitialise) lireChamps() else ChampsFormulaire()
            EtatFormulaire(
                modeAjout = modeAjout,
                champs = champs,
                photo = if (modeAjout) sauvegarde.get<String>(CLE_PHOTO) else null,
                age = calculerAge(champs),
                chargement = !modeAjout,
            )
        },
    )
    val etat: StateFlow<EtatFormulaire> = _etat

    init {
        if (modeAjout) verifierPhotoRestauree() else chargerEnfant()
    }

    // ---------------------------------------------------------------------------------------------
    // Saisie
    // ---------------------------------------------------------------------------------------------

    fun modifierChamps(transformation: (ChampsFormulaire) -> ChampsFormulaire) {
        if (_etat.value.chargement || _etat.value.enregistrement) return
        _etat.update { courant ->
            val champs = transformation(courant.champs)
            ecrireChamps(champs)
            courant.copy(
                champs = champs,
                age = calculerAge(champs),
                erreurs = if (validationActive) erreursDe(champs) else courant.erreurs,
                erreurEnregistrement = null,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Photo (ajout)
    // ---------------------------------------------------------------------------------------------

    /** Photo prise et validée : le formulaire s'affiche. Une éventuelle photo précédente est supprimée. */
    fun definirPhoto(cheminTemporaire: String) {
        val precedente = _etat.value.photo
        sauvegarde[CLE_PHOTO] = cheminTemporaire
        _etat.update { it.copy(photo = cheminTemporaire, photoTemporairePerdue = false, erreurEnregistrement = null) }
        if (precedente != null && precedente != cheminTemporaire) supprimerEnArrierePlan(precedente)
    }

    /** « Reprendre la photo » : la photo temporaire est supprimée et la caméra s'ouvre à nouveau. */
    fun reprendrePhoto() {
        val precedente = _etat.value.photo
        sauvegarde.remove<String>(CLE_PHOTO)
        _etat.update { it.copy(photo = null) }
        if (precedente != null) supprimerEnArrierePlan(precedente)
    }

    /** Abandon de l'ajout : aucune fiche n'est créée et la photo temporaire est supprimée. */
    fun abandonner() {
        val precedente = _etat.value.photo
        if (modeAjout && precedente != null) supprimerEnArrierePlan(precedente)
    }

    private fun verifierPhotoRestauree() {
        val chemin = _etat.value.photo ?: return
        viewModelScope.launch {
            if (!photos.existe(chemin)) {
                sauvegarde.remove<String>(CLE_PHOTO)
                _etat.update { if (it.photo == chemin) it.copy(photo = null, photoTemporairePerdue = true) else it }
            }
        }
    }

    private fun supprimerEnArrierePlan(chemin: String) {
        viewModelScope.launch { withContext(NonCancellable) { photos.supprimer(chemin) } }
    }

    // ---------------------------------------------------------------------------------------------
    // Enregistrement
    // ---------------------------------------------------------------------------------------------

    fun enregistrer() {
        val courant = _etat.value
        // Plusieurs clics rapides : un seul enregistrement, jamais deux fiches.
        if (courant.enregistrement || courant.chargement || courant.enfantEnregistre != null) return

        val resultat = ValidationEnfant.valider(courant.champs.versSaisie(), horloge.aujourdhui())
        if (resultat !is ResultatValidation.Valide) {
            validationActive = true
            sauvegarde[CLE_VALIDATION] = true
            _etat.update { it.copy(erreurs = (resultat as ResultatValidation.Invalide).erreurs) }
            return
        }
        val cheminPhoto = courant.photo
        if (modeAjout && cheminPhoto == null) {
            _etat.update { it.copy(erreurEnregistrement = ErreurEnregistrement.PHOTO_ABSENTE) }
            return
        }

        _etat.update { it.copy(enregistrement = true, erreurs = emptyMap(), erreurEnregistrement = null) }
        viewModelScope.launch {
            try {
                val id: Long? = if (modeAjout) {
                    enfants.ajouter(resultat.donnees, cheminPhoto!!)
                } else if (enfants.modifier(enfantId!!, resultat.donnees)) {
                    enfantId
                } else {
                    null
                }
                _etat.update {
                    if (id != null) {
                        it.copy(enregistrement = false, enfantEnregistre = id)
                    } else {
                        it.copy(enregistrement = false, erreurEnregistrement = ErreurEnregistrement.INTROUVABLE)
                    }
                }
            } catch (erreur: CancellationException) {
                throw erreur
            } catch (erreur: Exception) {
                // Aucune fiche incomplète n'a été créée ; la saisie et la photo sont conservées pour réessayer.
                _etat.update { it.copy(enregistrement = false, erreurEnregistrement = ErreurEnregistrement.ECRITURE) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Chargement (modification)
    // ---------------------------------------------------------------------------------------------

    private fun chargerEnfant() {
        viewModelScope.launch {
            try {
                val enfant = enfants.observer(enfantId!!).first()
                if (enfant == null) {
                    _etat.update { it.copy(chargement = false, erreurEnregistrement = ErreurEnregistrement.INTROUVABLE) }
                    return@launch
                }
                // Après une recréation, la saisie en cours (déjà restaurée) prime sur la fiche en base.
                val champs = if (dejaInitialise) _etat.value.champs else ChampsFormulaire.depuis(enfant)
                if (!dejaInitialise) {
                    ecrireChamps(champs)
                    sauvegarde[CLE_INITIALISE] = true
                }
                _etat.update { it.copy(champs = champs, age = calculerAge(champs), photo = enfant.photoPath, chargement = false) }
            } catch (erreur: CancellationException) {
                throw erreur
            } catch (erreur: Exception) {
                _etat.update { it.copy(chargement = false, erreurEnregistrement = ErreurEnregistrement.INTROUVABLE) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Outils
    // ---------------------------------------------------------------------------------------------

    private fun calculerAge(champs: ChampsFormulaire): Int? =
        champs.dateNaissance?.let { Age.enAnnees(it, horloge.aujourdhui()) }

    private fun erreursDe(champs: ChampsFormulaire): Map<ChampEnfant, ErreurChamp> =
        when (val resultat = ValidationEnfant.valider(champs.versSaisie(), horloge.aujourdhui())) {
            is ResultatValidation.Invalide -> resultat.erreurs
            is ResultatValidation.Valide -> emptyMap()
        }

    private fun ecrireChamps(champs: ChampsFormulaire) {
        sauvegarde[CLE_NOM] = champs.nom
        sauvegarde[CLE_PRENOM] = champs.prenom
        sauvegarde[CLE_NAISSANCE] = champs.dateNaissance?.toString().orEmpty()
        sauvegarde[CLE_SEXE] = champs.sexe?.name.orEmpty()
        sauvegarde[CLE_ADRESSE] = champs.adresse
        sauvegarde[CLE_NOM_PERE] = champs.nomPere
        sauvegarde[CLE_PERE_MEMBRE] = booleenVersTexte(champs.pereMembre)
        sauvegarde[CLE_NOM_MERE] = champs.nomMere
        sauvegarde[CLE_MERE_MEMBRE] = booleenVersTexte(champs.mereMembre)
        sauvegarde[CLE_NB_FRERES] = champs.nombreFreresSoeurs
        sauvegarde[CLE_NB_FRERES_MEMBRES] = champs.nombreFreresSoeursMembres
        sauvegarde[CLE_ARRIVEE] = champs.dateArrivee?.toString().orEmpty()
    }

    private fun lireChamps(): ChampsFormulaire {
        val defaut = ChampsFormulaire()
        return ChampsFormulaire(
            nom = sauvegarde.get<String>(CLE_NOM) ?: defaut.nom,
            prenom = sauvegarde.get<String>(CLE_PRENOM) ?: defaut.prenom,
            dateNaissance = texteVersDate(sauvegarde.get<String>(CLE_NAISSANCE)),
            sexe = sauvegarde.get<String>(CLE_SEXE)?.let { nom -> Sexe.entries.firstOrNull { it.name == nom } },
            adresse = sauvegarde.get<String>(CLE_ADRESSE) ?: defaut.adresse,
            nomPere = sauvegarde.get<String>(CLE_NOM_PERE) ?: defaut.nomPere,
            pereMembre = texteVersBooleen(sauvegarde.get<String>(CLE_PERE_MEMBRE)),
            nomMere = sauvegarde.get<String>(CLE_NOM_MERE) ?: defaut.nomMere,
            mereMembre = texteVersBooleen(sauvegarde.get<String>(CLE_MERE_MEMBRE)),
            nombreFreresSoeurs = sauvegarde.get<String>(CLE_NB_FRERES) ?: defaut.nombreFreresSoeurs,
            nombreFreresSoeursMembres = sauvegarde.get<String>(CLE_NB_FRERES_MEMBRES) ?: defaut.nombreFreresSoeursMembres,
            dateArrivee = texteVersDate(sauvegarde.get<String>(CLE_ARRIVEE)),
        )
    }

    private fun texteVersDate(texte: String?): LocalDate? =
        texte?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun booleenVersTexte(valeur: Boolean?): String = when (valeur) {
        true -> "OUI"
        false -> "NON"
        null -> ""
    }

    private fun texteVersBooleen(texte: String?): Boolean? = when (texte) {
        "OUI" -> true
        "NON" -> false
        else -> null
    }

    private companion object {
        const val CLE_NOM = "form_nom"
        const val CLE_PRENOM = "form_prenom"
        const val CLE_NAISSANCE = "form_naissance"
        const val CLE_SEXE = "form_sexe"
        const val CLE_ADRESSE = "form_adresse"
        const val CLE_NOM_PERE = "form_nom_pere"
        const val CLE_PERE_MEMBRE = "form_pere_membre"
        const val CLE_NOM_MERE = "form_nom_mere"
        const val CLE_MERE_MEMBRE = "form_mere_membre"
        const val CLE_NB_FRERES = "form_nb_freres"
        const val CLE_NB_FRERES_MEMBRES = "form_nb_freres_membres"
        const val CLE_ARRIVEE = "form_arrivee"
        const val CLE_PHOTO = "form_photo_temporaire"
        const val CLE_INITIALISE = "form_initialise"
        const val CLE_VALIDATION = "form_validation_active"
    }
}
