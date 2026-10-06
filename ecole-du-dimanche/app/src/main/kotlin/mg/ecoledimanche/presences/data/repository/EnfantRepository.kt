package mg.ecoledimanche.presences.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mg.ecoledimanche.presences.data.local.EnfantDao
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.data.local.SeanceDao
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.Dimanches
import mg.ecoledimanche.presences.domain.DonneesEnfant
import mg.ecoledimanche.presences.domain.RegleSeance
import mg.ecoledimanche.presences.domain.RegleSuivi
import mg.ecoledimanche.presences.domain.Recherche

/** Fiches enfants : création, modification, changement de photo et archivage. */
class EnfantRepository(
    private val enfantDao: EnfantDao,
    private val seanceDao: SeanceDao,
    private val transaction: ExecuteurTransaction,
    private val photos: StockagePhotos,
    private val clock: AppClock,
) {
    private val comparateur = Recherche.comparateurNoms()

    private fun trier(liste: List<EnfantEntity>): List<EnfantEntity> =
        liste.sortedWith { a, b -> comparateur.compare(a.prenom to a.nom, b.prenom to b.nom) }

    fun observerActifs(): Flow<List<EnfantEntity>> = enfantDao.observerSelonArchivage(false).map(::trier)

    fun observerArchives(): Flow<List<EnfantEntity>> = enfantDao.observerSelonArchivage(true).map(::trier)

    fun observer(id: Long): Flow<EnfantEntity?> = enfantDao.observer(id)

    /**
     * Crée la fiche : la photo temporaire validée est d'abord copiée définitivement, puis la
     * ligne est insérée. Si l'insertion échoue, la copie définitive est retirée (la temporaire
     * reste, pour réessayer) : aucune fiche incomplète ni photo orpheline volontaire. La
     * temporaire n'est supprimée qu'une fois la fiche enregistrée.
     *
     * Le début de suivi suit la règle de la section 10 (jamais antidaté).
     * L'opération n'est pas annulable en cours de route : une annulation entre le commit et le
     * nettoyage ne doit jamais retirer la photo d'une fiche déjà enregistrée.
     * @return l'identifiant de l'enfant créé.
     */
    suspend fun ajouter(donnees: DonneesEnfant, cheminPhotoTemporaire: String): Long = withContext(NonCancellable) {
        val cheminFinal = photos.promouvoirTemporaire(cheminPhotoTemporaire)
        val id = try {
            transaction.executer {
                val maintenant = clock.instant()
                val zone = clock.zone()
                val aujourdhui = maintenant.atZone(zone).toLocalDate()
                val seanceDuJour = if (Dimanches.estDimanche(aujourdhui)) seanceDao.trouver(aujourdhui) else null
                val phase = RegleSeance.phase(aujourdhui, seanceDuJour?.versEtat(), maintenant, zone)
                enfantDao.inserer(
                    EnfantEntity(
                        nom = donnees.nom,
                        prenom = donnees.prenom,
                        dateNaissance = donnees.dateNaissance,
                        sexe = donnees.sexe,
                        adresse = donnees.adresse,
                        nomPere = donnees.nomPere,
                        pereMembre = donnees.pereMembre,
                        nomMere = donnees.nomMere,
                        mereMembre = donnees.mereMembre,
                        nombreFreresSoeurs = donnees.nombreFreresSoeurs,
                        nombreFreresSoeursMembres = donnees.nombreFreresSoeursMembres,
                        photoPath = cheminFinal,
                        dateArriveeEglise = donnees.dateArrivee,
                        dateDebutSuivi = RegleSuivi.dateDebutSuivi(aujourdhui, phase),
                        dateFinSuivi = null,
                        isArchived = false,
                        archivedAt = null,
                        createdAt = maintenant,
                        updatedAt = maintenant,
                    ),
                )
            }
        } catch (erreur: Throwable) {
            photos.supprimer(cheminFinal)
            throw erreur
        }
        photos.supprimer(cheminPhotoTemporaire)
        id
    }

    /**
     * Met à jour les informations personnelles uniquement : début/fin de suivi, archivage et
     * photo sont repris tels quels de la fiche existante, l'historique n'est pas touché.
     * @return faux si l'enfant n'existe pas.
     */
    suspend fun modifier(id: Long, donnees: DonneesEnfant): Boolean = transaction.executer {
        val existant = enfantDao.trouver(id) ?: return@executer false
        enfantDao.mettreAJour(
            existant.copy(
                nom = donnees.nom,
                prenom = donnees.prenom,
                dateNaissance = donnees.dateNaissance,
                sexe = donnees.sexe,
                adresse = donnees.adresse,
                nomPere = donnees.nomPere,
                pereMembre = donnees.pereMembre,
                nomMere = donnees.nomMere,
                mereMembre = donnees.mereMembre,
                nombreFreresSoeurs = donnees.nombreFreresSoeurs,
                nombreFreresSoeursMembres = donnees.nombreFreresSoeursMembres,
                dateArriveeEglise = donnees.dateArrivee,
                updatedAt = clock.instant(),
            ),
        )
        true
    }

    /**
     * Remplace la photo : nouveau fichier enregistré, référence mise à jour dans Room, et
     * seulement ensuite suppression de l'ancien fichier. En cas d'échec, l'ancienne photo reste.
     * @return faux si l'enfant n'existe pas.
     */
    suspend fun remplacerPhoto(id: Long, cheminPhotoTemporaire: String): Boolean = withContext(NonCancellable) {
        val nouveau = photos.promouvoirTemporaire(cheminPhotoTemporaire)
        val ancien: String? = try {
            transaction.executer<String?> {
                val existant = enfantDao.trouver(id)
                if (existant == null) {
                    null
                } else {
                    enfantDao.changerPhoto(id, nouveau, clock.instant())
                    existant.photoPath
                }
            }
        } catch (erreur: Throwable) {
            photos.supprimer(nouveau)
            throw erreur
        }
        if (ancien == null) {
            photos.supprimer(nouveau)
            false
        } else {
            photos.supprimer(cheminPhotoTemporaire)
            if (ancien != nouveau) photos.supprimer(ancien)
            true
        }
    }

    /**
     * Archive l'enfant : fiche, photo et historique sont conservés. La fin de suivi est la date
     * locale du jour (borne incluse) : un archivage le dimanche garde ce dimanche, puis l'enfant
     * est exclu des dimanches suivants.
     * @return vrai si l'enfant vient d'être archivé.
     */
    suspend fun archiver(id: Long): Boolean = transaction.executer {
        val maintenant = clock.instant()
        val aujourdhui = maintenant.atZone(clock.zone()).toLocalDate()
        enfantDao.archiver(id, aujourdhui, maintenant) > 0
    }
}
