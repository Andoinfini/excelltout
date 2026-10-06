package mg.ecoledimanche.presences.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import mg.ecoledimanche.presences.domain.EtatSeance
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.domain.StatutPresence

/**
 * Table ENFANTS. L'âge n'est pas stocké (calculé depuis [dateNaissance]) et la photo n'est pas
 * un BLOB : [photoPath] est un chemin RELATIF au répertoire privé de l'application.
 *
 * [dateDebutSuivi] et [dateFinSuivi] sont des données techniques, distinctes de
 * [dateArriveeEglise] (information de la fiche). [dateFinSuivi] est une borne inclusive.
 */
@Entity(
    tableName = "enfants",
    indices = [
        Index(value = ["isArchived"]),
        Index(value = ["dateDebutSuivi"]),
        Index(value = ["nom", "prenom"]),
    ],
)
data class EnfantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val prenom: String,
    val dateNaissance: LocalDate,
    val sexe: Sexe,
    val adresse: String?,
    val nomPere: String?,
    /** Nul = information non renseignée (différent de « Non »). */
    val pereMembre: Boolean?,
    val nomMere: String?,
    val mereMembre: Boolean?,
    val nombreFreresSoeurs: Int,
    val nombreFreresSoeursMembres: Int,
    val photoPath: String,
    val dateArriveeEglise: LocalDate?,
    val dateDebutSuivi: LocalDate,
    val dateFinSuivi: LocalDate?,
    val isArchived: Boolean,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Table SEANCES : une ligne par dimanche matérialisé. La date civile est la clé primaire ;
 * le fuseau et l'instant de clôture prévus sont figés à la création, pour qu'un changement
 * de fuseau ou d'horloge ne déplace ni ne rouvre une séance.
 */
@Entity(tableName = "seances")
data class SeanceEntity(
    @PrimaryKey val dateDimanche: LocalDate,
    val zoneId: String,
    val cloturePrevueAt: Instant,
    val cloturee: Boolean,
    val clotureeAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun versEtat(): EtatSeance = EtatSeance(cloturee = cloturee, cloturePrevueAt = cloturePrevueAt)
}

/**
 * Table PRESENCES : clé primaire composite (enfantId, dateDimanche), donc une seule présence
 * par enfant et par dimanche. Les clés étrangères en RESTRICT interdisent de supprimer un
 * enfant ou une séance qui a un historique ; l'archivage est une simple mise à jour.
 */
@Entity(
    tableName = "presences",
    primaryKeys = ["enfantId", "dateDimanche"],
    foreignKeys = [
        ForeignKey(
            entity = EnfantEntity::class,
            parentColumns = ["id"],
            childColumns = ["enfantId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SeanceEntity::class,
            parentColumns = ["dateDimanche"],
            childColumns = ["dateDimanche"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["dateDimanche"]),
        Index(value = ["enfantId", "statut"]),
    ],
)
data class PresenceEntity(
    val enfantId: Long,
    val dateDimanche: LocalDate,
    val statut: StatutPresence,
    val createdAt: Instant,
    val updatedAt: Instant,
)
