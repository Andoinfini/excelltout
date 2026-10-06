package mg.ecoledimanche.presences.data

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import mg.ecoledimanche.presences.data.local.CompteurStatutLigne
import mg.ecoledimanche.presences.data.local.EnfantAvecStatut
import mg.ecoledimanche.presences.data.local.EnfantDao
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.data.local.LigneHistorique
import mg.ecoledimanche.presences.data.local.PlageSuivi
import mg.ecoledimanche.presences.data.local.PresenceDao
import mg.ecoledimanche.presences.data.local.PresenceEntity
import mg.ecoledimanche.presences.data.local.SeanceDao
import mg.ecoledimanche.presences.data.local.SeanceEntity
import mg.ecoledimanche.presences.data.repository.ExecuteurTransaction
import mg.ecoledimanche.presences.data.repository.StockagePhotos
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.StatutPresence

/** Horloge contrôlée : on la positionne sur un dimanche à 09 h 59 ou 10 h 00 sans attendre. */
class HorlogeTest(
    var maintenant: Instant,
    var fuseau: ZoneId = ZoneId.of("Indian/Antananarivo"),
) : AppClock {
    override fun instant(): Instant = maintenant
    override fun zone(): ZoneId = fuseau
}

/**
 * Base en mémoire qui reproduit le comportement de Room utile aux règles métier :
 * clés primaires, INSERT OR IGNORE, clés étrangères en RESTRICT, et transactions qui
 * s'annulent en bloc en cas d'exception.
 */
class FauxMagasin {
    val enfants = LinkedHashMap<Long, EnfantEntity>()
    val seances = LinkedHashMap<LocalDate, SeanceEntity>()
    val presences = LinkedHashMap<Pair<Long, LocalDate>, PresenceEntity>()

    /** Si vrai, `marquerCloturee` échoue : sert à vérifier l'atomicité de la clôture. */
    var echecSurMarquerCloturee = false

    /** Si vrai, l'insertion d'un enfant échoue. */
    var echecSurInsertionEnfant = false

    /** Si vrai, la mise à jour de la référence de photo échoue (après la copie du fichier). */
    var echecSurChangerPhoto = false

    private var sequence = 0L
    private val invalidation = MutableStateFlow(0)

    private fun notifier() {
        invalidation.value = invalidation.value + 1
    }

    private fun <T> flux(calcul: () -> T): Flow<T> = invalidation.map { calcul() }

    private fun admissible(e: EnfantEntity, dimanche: LocalDate): Boolean =
        e.dateDebutSuivi <= dimanche && (e.dateFinSuivi == null || e.dateFinSuivi >= dimanche)

    // --- Instantanés pour les transactions -------------------------------------------------------

    class Instantane(
        val enfants: Map<Long, EnfantEntity>,
        val seances: Map<LocalDate, SeanceEntity>,
        val presences: Map<Pair<Long, LocalDate>, PresenceEntity>,
        val sequence: Long,
    )

    fun capturer() = Instantane(LinkedHashMap(enfants), LinkedHashMap(seances), LinkedHashMap(presences), sequence)

    fun restaurer(i: Instantane) {
        enfants.clear(); enfants.putAll(i.enfants)
        seances.clear(); seances.putAll(i.seances)
        presences.clear(); presences.putAll(i.presences)
        sequence = i.sequence
        notifier()
    }

    // --- DAO -----------------------------------------------------------------------------------------

    val enfantDao = object : EnfantDao {
        override suspend fun inserer(enfant: EnfantEntity): Long {
            if (echecSurInsertionEnfant) throw IllegalStateException("insertion refusée (panne simulée)")
            val id = ++sequence
            enfants[id] = enfant.copy(id = id)
            notifier()
            return id
        }

        override suspend fun mettreAJour(enfant: EnfantEntity) {
            check(enfants.containsKey(enfant.id)) { "enfant inconnu" }
            enfants[enfant.id] = enfant
            notifier()
        }

        override suspend fun trouver(id: Long): EnfantEntity? = enfants[id]

        override fun observer(id: Long): Flow<EnfantEntity?> = flux { enfants[id] }

        override fun observerSelonArchivage(archive: Boolean): Flow<List<EnfantEntity>> =
            flux { enfants.values.filter { it.isArchived == archive } }

        override suspend fun plagesDeSuivi(): List<PlageSuivi> =
            enfants.values.map { PlageSuivi(it.dateDebutSuivi, it.dateFinSuivi) }

        override suspend fun compterAdmissible(id: Long, dimanche: LocalDate): Int =
            enfants[id]?.let { if (admissible(it, dimanche)) 1 else 0 } ?: 0

        override suspend fun compterAdmissibles(dimanche: LocalDate): Int =
            enfants.values.count { admissible(it, dimanche) }

        override fun observerAdmissiblesAvecStatut(dimanche: LocalDate): Flow<List<EnfantAvecStatut>> = flux {
            enfants.values.filter { admissible(it, dimanche) }
                .map { EnfantAvecStatut(it, presences[it.id to dimanche]?.statut) }
        }

        override suspend fun archiver(id: Long, finSuivi: LocalDate, maintenant: Instant): Int {
            val e = enfants[id] ?: return 0
            if (e.isArchived) return 0
            enfants[id] = e.copy(isArchived = true, dateFinSuivi = finSuivi, archivedAt = maintenant, updatedAt = maintenant)
            notifier()
            return 1
        }

        override suspend fun changerPhoto(id: Long, chemin: String, maintenant: Instant): Int {
            if (echecSurChangerPhoto) throw IllegalStateException("mise à jour refusée (panne simulée)")
            val e = enfants[id] ?: return 0
            enfants[id] = e.copy(photoPath = chemin, updatedAt = maintenant)
            notifier()
            return 1
        }

        override suspend fun tousLesCheminsPhoto(): List<String> = enfants.values.map { it.photoPath }
    }

    val seanceDao = object : SeanceDao {
        override suspend fun insererSiAbsente(seance: SeanceEntity): Long {
            if (seances.containsKey(seance.dateDimanche)) return -1
            seances[seance.dateDimanche] = seance
            notifier()
            return 1
        }

        override suspend fun trouver(dimanche: LocalDate): SeanceEntity? = seances[dimanche]

        override fun observer(dimanche: LocalDate): Flow<SeanceEntity?> = flux { seances[dimanche] }

        override suspend fun toutes(): List<SeanceEntity> = seances.values.toList()

        override suspend fun marquerCloturee(dimanche: LocalDate, maintenant: Instant): Int {
            if (echecSurMarquerCloturee) throw IllegalStateException("écriture refusée (panne simulée)")
            val s = seances[dimanche] ?: return 0
            if (s.cloturee) return 0
            seances[dimanche] = s.copy(cloturee = true, clotureeAt = maintenant, updatedAt = maintenant)
            notifier()
            return 1
        }
    }

    val presenceDao = object : PresenceDao {
        private fun verifierClesEtrangeres(enfantId: Long, dimanche: LocalDate) {
            check(enfants.containsKey(enfantId)) { "FOREIGN KEY constraint failed (enfant)" }
            check(seances.containsKey(dimanche)) { "FOREIGN KEY constraint failed (séance)" }
        }

        override suspend fun insererSiAbsente(presence: PresenceEntity): Long {
            verifierClesEtrangeres(presence.enfantId, presence.dateDimanche)
            val cle = presence.enfantId to presence.dateDimanche
            if (presences.containsKey(cle)) return -1
            presences[cle] = presence
            notifier()
            return 1
        }

        override suspend fun changerStatut(enfantId: Long, dimanche: LocalDate, statut: StatutPresence, maintenant: Instant): Int {
            val cle = enfantId to dimanche
            val p = presences[cle] ?: return 0
            presences[cle] = p.copy(statut = statut, updatedAt = maintenant)
            notifier()
            return 1
        }

        override suspend fun creerManquantesPourAdmissibles(dimanche: LocalDate, statut: StatutPresence, maintenant: Instant) {
            for (e in enfants.values.filter { admissible(it, dimanche) }) {
                val cle = e.id to dimanche
                verifierClesEtrangeres(e.id, dimanche)
                if (!presences.containsKey(cle)) presences[cle] = PresenceEntity(e.id, dimanche, statut, maintenant, maintenant)
            }
            notifier()
        }

        override suspend fun remplacerStatut(dimanche: LocalDate, ancien: StatutPresence, nouveau: StatutPresence, maintenant: Instant): Int {
            var n = 0
            for ((cle, p) in presences.toList()) {
                if (cle.second == dimanche && p.statut == ancien) {
                    presences[cle] = p.copy(statut = nouveau, updatedAt = maintenant)
                    n++
                }
            }
            notifier()
            return n
        }

        override suspend fun duDimanche(dimanche: LocalDate): List<PresenceEntity> =
            presences.values.filter { it.dateDimanche == dimanche }

        override suspend fun trouver(enfantId: Long, dimanche: LocalDate): PresenceEntity? = presences[enfantId to dimanche]

        override fun observerCompteurs(): Flow<List<CompteurStatutLigne>> = flux {
            presences.values
                .filter { seances[it.dateDimanche]?.cloturee == true }
                .groupBy { it.enfantId to it.statut }
                .map { (cle, liste) -> CompteurStatutLigne(cle.first, cle.second, liste.size) }
        }

        override fun observerHistorique(enfantId: Long): Flow<List<LigneHistorique>> = flux {
            presences.values.filter { it.enfantId == enfantId }
                .sortedByDescending { it.dateDimanche }
                .map { LigneHistorique(it.dateDimanche, it.statut, seances.getValue(it.dateDimanche).cloturee) }
        }
    }

    val transaction = object : ExecuteurTransaction {
        override suspend fun <T> executer(bloc: suspend () -> T): T {
            val avant = capturer()
            try {
                return bloc()
            } catch (erreur: Throwable) {
                restaurer(avant)
                throw erreur
            }
        }
    }
}

/** Faux stockage de photos : suit les fichiers temporaires et définitifs sans toucher au disque. */
class FauxStockagePhotos : StockagePhotos {
    val temporaires = LinkedHashSet<String>()
    val definitives = LinkedHashSet<String>()
    var echecPromotion = false

    /**
     * Si vrai, la photo temporaire n'est jamais consommée : sert à prouver que ce sont bien les gardes
     * du code (et non un effet du faux) qui empêchent les doublons.
     */
    var temporairesIndelebiles = false
    private var numero = 0

    fun nouvelleTemporaire(): String = "photos_tmp/t${++numero}.jpg".also { temporaires.add(it) }

    override suspend fun promouvoirTemporaire(cheminTemporaire: String): String {
        if (echecPromotion || (!temporairesIndelebiles && cheminTemporaire !in temporaires)) {
            throw IOException("photo temporaire illisible : $cheminTemporaire")
        }
        return "photos/f${++numero}.jpg".also { definitives.add(it) } // copie : la temporaire est conservée
    }

    override suspend fun supprimer(chemin: String) {
        definitives.remove(chemin)
        if (!temporairesIndelebiles) temporaires.remove(chemin)
    }

    override suspend fun existe(chemin: String): Boolean = chemin in temporaires || chemin in definitives
}
