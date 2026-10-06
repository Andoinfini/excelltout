package mg.ecoledimanche.presences.data.repository

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mg.ecoledimanche.presences.data.local.EnfantDao
import mg.ecoledimanche.presences.data.local.EnfantEntity
import mg.ecoledimanche.presences.data.local.PresenceDao
import mg.ecoledimanche.presences.data.local.PresenceEntity
import mg.ecoledimanche.presences.data.local.SeanceDao
import mg.ecoledimanche.presences.data.local.SeanceEntity
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.CompteursAssiduite
import mg.ecoledimanche.presences.domain.Dimanches
import mg.ecoledimanche.presences.domain.PhaseSeance
import mg.ecoledimanche.presences.domain.Recherche
import mg.ecoledimanche.presences.domain.RegleSeance
import mg.ecoledimanche.presences.domain.RegleSuivi
import mg.ecoledimanche.presences.domain.StatutPresence
import mg.ecoledimanche.presences.domain.aujourdhui

/** Un enfant dans la grille d'un dimanche, avec son statut tel qu'il doit être affiché. */
data class ElementDimanche(
    val enfant: EnfantEntity,
    val statut: StatutPresence,
)

data class EtatDimanche(
    val dimanche: LocalDate,
    val phase: PhaseSeance,
    val elements: List<ElementDimanche>,
)

data class LigneAssiduite(
    val enfant: EnfantEntity,
    val compteurs: CompteursAssiduite,
)

data class ElementHistorique(
    val dimanche: LocalDate,
    val statut: StatutPresence,
)

data class HistoriqueEnfant(
    val enfant: EnfantEntity,
    /** Du plus récent au plus ancien ; uniquement des dimanches déjà commencés. */
    val elements: List<ElementHistorique>,
    val compteurs: CompteursAssiduite,
)

enum class MotifRefus {
    PAS_UN_DIMANCHE,
    SEANCE_A_VENIR,
    ENFANT_NON_ADMISSIBLE,
    ABSENT_INTERDIT_AVANT_CLOTURE,
    ANNULATION_INTERDITE_APRES_CLOTURE,
}

sealed interface ResultatPointage {
    data class Enregistre(val statut: StatutPresence) : ResultatPointage
    data class Refuse(val motif: MotifRefus) : ResultatPointage
}

/**
 * Séances, pointage, clôture à 10 h, rattrapage, assiduité et historique.
 *
 * Règle d'or : l'écran ne contient aucune logique de clôture. Il appelle [rattraper] avant
 * d'afficher, puis observe les flux, qui ne montrent que ce qui est réellement enregistré.
 */
class PresenceRepository(
    private val enfantDao: EnfantDao,
    private val seanceDao: SeanceDao,
    private val presenceDao: PresenceDao,
    private val transaction: ExecuteurTransaction,
    private val clock: AppClock,
) {
    private val verrouRattrapage = Mutex()
    private val comparateur = Recherche.comparateurNoms()

    // ------------------------------------------------------------------------------------------
    // Rattrapage et clôture
    // ------------------------------------------------------------------------------------------

    /**
     * Matérialise toutes les clôtures échues, depuis le premier début de suivi connu jusqu'à
     * aujourd'hui, y compris les dimanches où l'application n'a jamais été ouverte.
     *
     * Idempotent : un dimanche déjà clôturé n'est jamais retouché, les lignes existantes ne sont
     * jamais remplacées, et une correction manuelle faite après clôture est donc conservée.
     * Aucune séance n'est créée pour un dimanche sans enfant admissible, ni avant un début de
     * suivi, ni pour une séance future.
     *
     * @return le nombre de séances clôturées par cet appel.
     */
    suspend fun rattraper(): Int = verrouRattrapage.withLock {
        val maintenant = clock.instant()
        val zone = clock.zone()
        val aujourdhui = maintenant.atZone(zone).toLocalDate()

        val plages = enfantDao.plagesDeSuivi()
        val premierSuivi = plages.minOfOrNull { it.dateDebutSuivi } ?: return@withLock 0
        val seances = seanceDao.toutes().associateBy { it.dateDimanche }

        // Tri préalable sans transaction : on évite d'ouvrir une écriture pour rien.
        val aTraiter = Dimanches.entre(premierSuivi, aujourdhui).filter { dimanche ->
            val seance = seances[dimanche]
            when {
                seance?.cloturee == true -> false
                maintenant.isBefore(seance?.cloturePrevueAt ?: RegleSeance.instantCloture(dimanche, zone)) -> false
                seance != null -> true
                else -> plages.any { RegleSuivi.estAdmissible(dimanche, it.dateDebutSuivi, it.dateFinSuivi) }
            }
        }

        var cloturees = 0
        for (dimanche in aTraiter) {
            // Une transaction par séance : la clôture d'un dimanche est atomique.
            val faite = transaction.executer { cloturerSiEchue(dimanche, maintenant, zone) }
            if (faite) cloturees++
        }
        cloturees
    }

    private suspend fun cloturerSiEchue(dimanche: LocalDate, maintenant: Instant, zone: ZoneId): Boolean {
        val seance = seanceDao.trouver(dimanche)
        if (seance?.cloturee == true) return false
        val echeance = seance?.cloturePrevueAt ?: RegleSeance.instantCloture(dimanche, zone)
        if (maintenant.isBefore(echeance)) return false
        if (seance == null && enfantDao.compterAdmissibles(dimanche) == 0) return false
        appliquerCloture(dimanche, maintenant, zone)
        return true
    }

    /**
     * Clôture d'un dimanche, à appeler dans une transaction :
     * 1. créer la séance si besoin (sans jamais écraser) ;
     * 2. créer en ABSENT les lignes manquantes des enfants admissibles ;
     * 3. convertir uniquement les NON_ENREGISTRE en ABSENT ;
     * 4. marquer la séance clôturée.
     * PRESENT, EN_RETARD et les corrections existantes ne sont jamais modifiés.
     */
    private suspend fun appliquerCloture(dimanche: LocalDate, maintenant: Instant, zone: ZoneId) {
        assurerSeance(dimanche, maintenant, zone)
        presenceDao.creerManquantesPourAdmissibles(dimanche, StatutPresence.ABSENT, maintenant)
        presenceDao.remplacerStatut(dimanche, StatutPresence.NON_ENREGISTRE, StatutPresence.ABSENT, maintenant)
        seanceDao.marquerCloturee(dimanche, maintenant)
    }

    /** Fige la date civile, le fuseau et l'instant de clôture à la création de la séance. */
    private suspend fun assurerSeance(dimanche: LocalDate, maintenant: Instant, zone: ZoneId) {
        seanceDao.insererSiAbsente(
            SeanceEntity(
                dateDimanche = dimanche,
                zoneId = zone.id,
                cloturePrevueAt = RegleSeance.instantCloture(dimanche, zone),
                cloturee = false,
                clotureeAt = null,
                createdAt = maintenant,
                updatedAt = maintenant,
            ),
        )
    }

    // ------------------------------------------------------------------------------------------
    // Pointage et correction
    // ------------------------------------------------------------------------------------------

    /**
     * Enregistre immédiatement un statut dans Room (le seul endroit d'où l'écran lit).
     *
     * - Séance à venir : refusé, aucune écriture.
     * - Avant la clôture : PRESENT, EN_RETARD, ou NON_ENREGISTRE (« Annuler le pointage »).
     * - Après la clôture (y compris échue mais pas encore matérialisée) : la clôture est d'abord
     *   appliquée, puis correction explicite vers PRESENT, EN_RETARD ou ABSENT ; la séance reste
     *   clôturée. NON_ENREGISTRE n'est pas autorisé.
     *
     * Une seule ligne par enfant et par dimanche (clé primaire composite) : des clics rapprochés
     * sont sérialisés par la transaction et ne peuvent pas créer de doublon.
     */
    suspend fun pointer(enfantId: Long, dimanche: LocalDate, statut: StatutPresence): ResultatPointage =
        transaction.executer {
            if (!Dimanches.estDimanche(dimanche)) {
                return@executer ResultatPointage.Refuse(MotifRefus.PAS_UN_DIMANCHE)
            }
            val maintenant = clock.instant()
            val zone = clock.zone()
            val seance = seanceDao.trouver(dimanche)
            val phase = RegleSeance.phase(dimanche, seance?.versEtat(), maintenant, zone)
            if (phase == PhaseSeance.A_VENIR) {
                return@executer ResultatPointage.Refuse(MotifRefus.SEANCE_A_VENIR)
            }
            if (enfantDao.compterAdmissible(enfantId, dimanche) == 0) {
                return@executer ResultatPointage.Refuse(MotifRefus.ENFANT_NON_ADMISSIBLE)
            }

            if (phase == PhaseSeance.EN_COURS) {
                if (statut == StatutPresence.ABSENT) {
                    return@executer ResultatPointage.Refuse(MotifRefus.ABSENT_INTERDIT_AVANT_CLOTURE)
                }
                assurerSeance(dimanche, maintenant, zone)
            } else {
                // La clôture est échue : on la matérialise d'abord, la saisie est une correction.
                if (seance?.cloturee != true) appliquerCloture(dimanche, maintenant, zone)
                if (statut == StatutPresence.NON_ENREGISTRE) {
                    return@executer ResultatPointage.Refuse(MotifRefus.ANNULATION_INTERDITE_APRES_CLOTURE)
                }
            }

            presenceDao.insererSiAbsente(PresenceEntity(enfantId, dimanche, statut, maintenant, maintenant))
            presenceDao.changerStatut(enfantId, dimanche, statut, maintenant)
            ResultatPointage.Enregistre(statut)
        }

    // ------------------------------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------------------------------

    /** Premier dimanche de suivi connu : borne basse du sélecteur de dimanches. */
    suspend fun premierDimancheSuivi(): LocalDate? =
        enfantDao.plagesDeSuivi().minOfOrNull { it.dateDebutSuivi }?.let { Dimanches.courantOuSuivant(it) }

    /**
     * Grille d'un dimanche : seuls les enfants admissibles. Une séance clôturée n'affiche jamais
     * « Non enregistré » : un statut absent ou NON_ENREGISTRE y est présenté comme ABSENT, même si
     * l'écriture n'a pas encore eu lieu.
     */
    fun observerDimanche(dimanche: LocalDate): Flow<EtatDimanche> =
        combine(enfantDao.observerAdmissiblesAvecStatut(dimanche), seanceDao.observer(dimanche)) { lignes, seance ->
            val phase = RegleSeance.phase(dimanche, seance?.versEtat(), clock.instant(), clock.zone())
            val elements = lignes
                .map { ElementDimanche(it.enfant, statutAffiche(it.statut, phase)) }
                .sortedWith { a, b -> comparateur.compare(a.enfant.prenom to a.enfant.nom, b.enfant.prenom to b.enfant.nom) }
            EtatDimanche(dimanche, phase, elements)
        }

    /** Enfants (actifs ou archivés) avec leurs compteurs sur les séances clôturées uniquement. */
    fun observerAssiduite(archives: Boolean): Flow<List<LigneAssiduite>> =
        combine(enfantDao.observerSelonArchivage(archives), presenceDao.observerCompteurs()) { enfants, lignes ->
            val parEnfant = lignes.groupBy { it.enfantId }
            enfants
                .map { enfant ->
                    val compteurs = parEnfant[enfant.id].orEmpty()
                    LigneAssiduite(
                        enfant,
                        CompteursAssiduite(
                            presences = compteurs.filter { it.statut == StatutPresence.PRESENT }.sumOf { it.nombre },
                            retards = compteurs.filter { it.statut == StatutPresence.EN_RETARD }.sumOf { it.nombre },
                            absences = compteurs.filter { it.statut == StatutPresence.ABSENT }.sumOf { it.nombre },
                        ),
                    )
                }
                .sortedWith { a, b -> comparateur.compare(a.enfant.prenom to a.enfant.nom, b.enfant.prenom to b.enfant.nom) }
        }

    /**
     * Historique d'un enfant : les dimanches admissibles déjà commencés, du plus récent au plus
     * ancien. Les dimanches futurs n'en font pas partie. Les compteurs ne portent que sur les
     * séances clôturées.
     */
    fun observerHistorique(enfantId: Long): Flow<HistoriqueEnfant?> =
        combine(enfantDao.observer(enfantId), presenceDao.observerHistorique(enfantId)) { enfant, lignes ->
            if (enfant == null) return@combine null
            val maintenant = clock.instant()
            val zone = clock.zone()
            val aujourdhui = clock.aujourdhui()
            val derniereDate = enfant.dateFinSuivi?.let { if (it.isBefore(aujourdhui)) it else aujourdhui } ?: aujourdhui
            val parDate = lignes.associateBy { it.dateDimanche }
            val elements = Dimanches.entre(enfant.dateDebutSuivi, derniereDate)
                .map { dimanche ->
                    val ligne = parDate[dimanche]
                    val statut = if (ligne != null) {
                        ligne.statut
                    } else {
                        statutAffiche(null, RegleSeance.phase(dimanche, null, maintenant, zone))
                    }
                    ElementHistorique(dimanche, statut)
                }
                // asReversed() (Kotlin) et non reversed() : compilé contre un SDK récent, reversed() viserait la méthode
                // Java 21 de List, absente avant Android 15.
                .asReversed()
            val cloturees = lignes.filter { it.cloturee }
            HistoriqueEnfant(
                enfant = enfant,
                elements = elements,
                compteurs = CompteursAssiduite(
                    presences = cloturees.count { it.statut == StatutPresence.PRESENT },
                    retards = cloturees.count { it.statut == StatutPresence.EN_RETARD },
                    absences = cloturees.count { it.statut == StatutPresence.ABSENT },
                ),
            )
        }

    private fun statutAffiche(statut: StatutPresence?, phase: PhaseSeance): StatutPresence =
        if (phase == PhaseSeance.CLOTUREE && (statut == null || statut == StatutPresence.NON_ENREGISTRE)) {
            StatutPresence.ABSENT
        } else {
            statut ?: StatutPresence.NON_ENREGISTRE
        }
}
