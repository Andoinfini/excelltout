package mg.ecoledimanche.presences.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import mg.ecoledimanche.presences.data.repository.ExecuteurTransaction

@Database(
    entities = [EnfantEntity::class, SeanceEntity::class, PresenceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun enfantDao(): EnfantDao
    abstract fun seanceDao(): SeanceDao
    abstract fun presenceDao(): PresenceDao

    companion object {
        const val NOM_FICHIER = "ecole_dimanche.db"

        /**
         * Migrations explicites. Toute évolution du schéma incrémente `version` et ajoute ici une
         * migration : aucune migration destructive n'est autorisée, les données doivent survivre.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun creer(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOM_FICHIER)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Exécute un bloc dans une transaction Room (validée en bloc ou annulée en bloc). */
class ExecuteurTransactionRoom(private val base: RoomDatabase) : ExecuteurTransaction {
    override suspend fun <T> executer(bloc: suspend () -> T): T = base.withTransaction { bloc() }
}
