package mg.ecoledimanche.presences.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import mg.ecoledimanche.presences.domain.StatutPresence

/** Un enfant admissible à un dimanche, avec son statut s'il est déjà enregistré (sinon nul). */
data class EnfantAvecStatut(
    @Embedded val enfant: EnfantEntity,
    val statut: StatutPresence?,
)

/** Plage de suivi d'un enfant, pour décider sans requête supplémentaire quels dimanches traiter. */
data class PlageSuivi(
    val dateDebutSuivi: LocalDate,
    val dateFinSuivi: LocalDate?,
)

/** Nombre de présences d'un enfant dans un statut donné, sur les séances clôturées. */
data class CompteurStatutLigne(
    val enfantId: Long,
    val statut: StatutPresence,
    val nombre: Int,
)

/** Une ligne d'historique : le statut et l'état de clôture de la séance correspondante. */
data class LigneHistorique(
    val dateDimanche: LocalDate,
    val statut: StatutPresence,
    val cloturee: Boolean,
)

@Dao
interface EnfantDao {
    @Insert
    suspend fun inserer(enfant: EnfantEntity): Long

    @Update
    suspend fun mettreAJour(enfant: EnfantEntity)

    @Query("SELECT * FROM enfants WHERE id = :id")
    suspend fun trouver(id: Long): EnfantEntity?

    @Query("SELECT * FROM enfants WHERE id = :id")
    fun observer(id: Long): Flow<EnfantEntity?>

    @Query("SELECT * FROM enfants WHERE isArchived = :archive")
    fun observerSelonArchivage(archive: Boolean): Flow<List<EnfantEntity>>

    @Query("SELECT dateDebutSuivi, dateFinSuivi FROM enfants")
    suspend fun plagesDeSuivi(): List<PlageSuivi>

    @Query(
        "SELECT COUNT(*) FROM enfants WHERE id = :id AND dateDebutSuivi <= :dimanche " +
            "AND (dateFinSuivi IS NULL OR dateFinSuivi >= :dimanche)",
    )
    suspend fun compterAdmissible(id: Long, dimanche: LocalDate): Int

    @Query(
        "SELECT COUNT(*) FROM enfants WHERE dateDebutSuivi <= :dimanche " +
            "AND (dateFinSuivi IS NULL OR dateFinSuivi >= :dimanche)",
    )
    suspend fun compterAdmissibles(dimanche: LocalDate): Int

    /** Grille d'un dimanche : seuls les enfants admissibles, avec leur statut éventuel. */
    @Query(
        "SELECT e.*, p.statut AS statut FROM enfants e " +
            "LEFT JOIN presences p ON p.enfantId = e.id AND p.dateDimanche = :dimanche " +
            "WHERE e.dateDebutSuivi <= :dimanche AND (e.dateFinSuivi IS NULL OR e.dateFinSuivi >= :dimanche)",
    )
    fun observerAdmissiblesAvecStatut(dimanche: LocalDate): Flow<List<EnfantAvecStatut>>

    /** Archivage = mise à jour de la fiche : la photo et l'historique sont conservés. */
    @Query(
        "UPDATE enfants SET isArchived = 1, dateFinSuivi = :finSuivi, archivedAt = :maintenant, " +
            "updatedAt = :maintenant WHERE id = :id AND isArchived = 0",
    )
    suspend fun archiver(id: Long, finSuivi: LocalDate, maintenant: Instant): Int

    @Query("UPDATE enfants SET photoPath = :chemin, updatedAt = :maintenant WHERE id = :id")
    suspend fun changerPhoto(id: Long, chemin: String, maintenant: Instant): Int

    @Query("SELECT photoPath FROM enfants")
    suspend fun tousLesCheminsPhoto(): List<String>
}

@Dao
interface SeanceDao {
    /** N'écrase jamais une séance existante (retourne -1 si elle existe déjà). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insererSiAbsente(seance: SeanceEntity): Long

    @Query("SELECT * FROM seances WHERE dateDimanche = :dimanche")
    suspend fun trouver(dimanche: LocalDate): SeanceEntity?

    @Query("SELECT * FROM seances WHERE dateDimanche = :dimanche")
    fun observer(dimanche: LocalDate): Flow<SeanceEntity?>

    @Query("SELECT * FROM seances")
    suspend fun toutes(): List<SeanceEntity>

    /** Ne touche qu'une séance encore ouverte : une séance clôturée n'est jamais modifiée. */
    @Query(
        "UPDATE seances SET cloturee = 1, clotureeAt = :maintenant, updatedAt = :maintenant " +
            "WHERE dateDimanche = :dimanche AND cloturee = 0",
    )
    suspend fun marquerCloturee(dimanche: LocalDate, maintenant: Instant): Int
}

@Dao
interface PresenceDao {
    /** N'écrase jamais un statut déjà enregistré. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insererSiAbsente(presence: PresenceEntity): Long

    @Query(
        "UPDATE presences SET statut = :statut, updatedAt = :maintenant " +
            "WHERE enfantId = :enfantId AND dateDimanche = :dimanche",
    )
    suspend fun changerStatut(enfantId: Long, dimanche: LocalDate, statut: StatutPresence, maintenant: Instant): Int

    /**
     * Crée, pour chaque enfant admissible au dimanche, la ligne manquante avec [statut].
     * Les lignes existantes ne sont jamais remplacées (INSERT OR IGNORE sur la clé composite).
     */
    @Query(
        "INSERT OR IGNORE INTO presences (enfantId, dateDimanche, statut, createdAt, updatedAt) " +
            "SELECT id, :dimanche, :statut, :maintenant, :maintenant FROM enfants " +
            "WHERE dateDebutSuivi <= :dimanche AND (dateFinSuivi IS NULL OR dateFinSuivi >= :dimanche)",
    )
    suspend fun creerManquantesPourAdmissibles(dimanche: LocalDate, statut: StatutPresence, maintenant: Instant)

    /** Convertit uniquement les lignes du dimanche qui ont le statut [ancien]. */
    @Query(
        "UPDATE presences SET statut = :nouveau, updatedAt = :maintenant " +
            "WHERE dateDimanche = :dimanche AND statut = :ancien",
    )
    suspend fun remplacerStatut(dimanche: LocalDate, ancien: StatutPresence, nouveau: StatutPresence, maintenant: Instant): Int

    @Query("SELECT * FROM presences WHERE dateDimanche = :dimanche")
    suspend fun duDimanche(dimanche: LocalDate): List<PresenceEntity>

    @Query("SELECT * FROM presences WHERE enfantId = :enfantId AND dateDimanche = :dimanche")
    suspend fun trouver(enfantId: Long, dimanche: LocalDate): PresenceEntity?

    /** Compteurs par enfant et par statut, sur les seules séances CLÔTURÉES. */
    @Query(
        "SELECT p.enfantId AS enfantId, p.statut AS statut, COUNT(*) AS nombre FROM presences p " +
            "INNER JOIN seances s ON s.dateDimanche = p.dateDimanche " +
            "WHERE s.cloturee = 1 GROUP BY p.enfantId, p.statut",
    )
    fun observerCompteurs(): Flow<List<CompteurStatutLigne>>

    @Query(
        "SELECT p.dateDimanche AS dateDimanche, p.statut AS statut, s.cloturee AS cloturee FROM presences p " +
            "INNER JOIN seances s ON s.dateDimanche = p.dateDimanche WHERE p.enfantId = :enfantId " +
            "ORDER BY p.dateDimanche DESC",
    )
    fun observerHistorique(enfantId: Long): Flow<List<LigneHistorique>>
}
