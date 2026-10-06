package mg.ecoledimanche.presences.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mg.ecoledimanche.presences.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie que le schéma exporté (app/schemas/.../1.json, généré à la compilation) correspond bien
 * aux entités, et que toutes les migrations déclarées dans [AppDatabase.MIGRATIONS] aboutissent à
 * un schéma valide. À chaque nouvelle version de la base : ajouter ici un test de migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationRoomTest {
    @get:Rule
    val aide = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun schemaVersion1_estValide() {
        aide.createDatabase(NOM, 1).close()
        aide.runMigrationsAndValidate(NOM, 1, true, *AppDatabase.MIGRATIONS).close()
    }

    private companion object {
        const val NOM = "test_migration.db"
    }
}
