package mg.ecoledimanche.presences.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import mg.ecoledimanche.presences.domain.Sexe
import mg.ecoledimanche.presences.domain.StatutPresence

/**
 * Convertisseurs Room.
 * - Dates civiles : chaîne ISO AAAA-MM-JJ (indépendante de l'affichage, triable comme du texte).
 * - Horodatages : millisecondes depuis l'époque Unix (un instant, jamais une date locale).
 * - Énumérations : leur nom, sans accent.
 */
class Convertisseurs {
    @TypeConverter
    fun dateVersTexte(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun texteVersDate(texte: String?): LocalDate? = texte?.let { LocalDate.parse(it) }

    @TypeConverter
    fun instantVersLong(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun longVersInstant(valeur: Long?): Instant? = valeur?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun statutVersTexte(statut: StatutPresence?): String? = statut?.name

    @TypeConverter
    fun texteVersStatut(texte: String?): StatutPresence? = texte?.let { StatutPresence.valueOf(it) }

    @TypeConverter
    fun sexeVersTexte(sexe: Sexe?): String? = sexe?.name

    @TypeConverter
    fun texteVersSexe(texte: String?): Sexe? = texte?.let { Sexe.valueOf(it) }
}
