package mg.ecoledimanche.presences.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mg.ecoledimanche.presences.data.local.AppDatabase
import mg.ecoledimanche.presences.data.local.ExecuteurTransactionRoom
import mg.ecoledimanche.presences.data.local.PresenceEntity
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.data.repository.PresenceRepository
import mg.ecoledimanche.presences.domain.CompteursAssiduite
import mg.ecoledimanche.presences.domain.StatutPresence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Intégrité de la VRAIE base Room (SQLite de l'appareil) : clés, contraintes, requêtes et
 * transactions. À exécuter sur appareil ou émulateur : `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class BaseRoomInstrumenteeTest {
    private lateinit var contexte: Context
    private lateinit var base: AppDatabase
    private lateinit var horloge: HorlogeControlee
    private lateinit var stockage: StockageFactice
    private lateinit var enfants: EnfantRepository
    private lateinit var presences: PresenceRepository
    private val dimanche = LocalDate.parse("2026-10-11")

    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, AppDatabase::class.java).build()
        branche(base)
    }

    private fun branche(db: AppDatabase) {
        horloge = HorlogeControlee(java.time.Instant.EPOCH)
        horloge.allerA("2026-10-06", 14)
        stockage = StockageFactice()
        val transaction = ExecuteurTransactionRoom(db)
        enfants = EnfantRepository(db.enfantDao(), db.seanceDao(), transaction, stockage, horloge)
        presences = PresenceRepository(db.enfantDao(), db.seanceDao(), db.presenceDao(), transaction, horloge)
    }

    @After
    fun fermer() {
        base.close()
    }

    private suspend fun inscrire(nom: String, prenom: String): Long =
        enfants.ajouter(donneesEnfant(nom, prenom), stockage.nouvelleTemporaire())

    @Test
    fun scenarioPrincipal_surLaVraieBase() = runTest {
        val sarah = inscrire("Rakoto", "Sarah")
        val david = inscrire("Rakoto", "David")
        val nathan = inscrire("Rakoto", "Nathan")

        horloge.allerA("2026-10-11", 9, 0)
        presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        presences.pointer(david, dimanche, StatutPresence.EN_RETARD)

        horloge.allerA("2026-10-11", 9, 59, 59)
        presences.rattraper()
        assertEquals(
            StatutPresence.NON_ENREGISTRE,
            presences.observerDimanche(dimanche).first().elements.single { it.enfant.id == nathan }.statut,
        )

        horloge.allerA("2026-10-11", 10, 0)
        assertEquals(1, presences.rattraper())
        assertEquals(0, presences.rattraper()) // idempotent
        val statuts = presences.observerDimanche(dimanche).first().elements.associate { it.enfant.id to it.statut }
        assertEquals(StatutPresence.PRESENT, statuts[sarah])
        assertEquals(StatutPresence.EN_RETARD, statuts[david])
        assertEquals(StatutPresence.ABSENT, statuts[nathan])

        val compteurs = presences.observerAssiduite(false).first().associate { it.enfant.id to it.compteurs }
        assertEquals(CompteursAssiduite(1, 0, 0), compteurs[sarah])
        assertEquals(CompteursAssiduite(0, 1, 0), compteurs[david])
        assertEquals(CompteursAssiduite(0, 0, 1), compteurs[nathan])
        assertEquals(100 * 10, compteurs[david]!!.tauxDixiemes)
        assertEquals(0, compteurs[nathan]!!.tauxDixiemes)
    }

    @Test
    fun rattrapageDeTroisDimanches_sansSeanceExistante() = runTest {
        val id = inscrire("Rakoto", "Sarah")
        horloge.allerA("2026-11-01", 15, 0)
        assertEquals(4, presences.rattraper())
        assertEquals(CompteursAssiduite(0, 0, 4), presences.observerAssiduite(false).first().single { it.enfant.id == id }.compteurs)
    }

    @Test
    fun clePrimaireComposite_refuseUneDeuxiemePresence() = runTest {
        val id = inscrire("Rakoto", "Sarah")
        horloge.allerA("2026-10-11", 9, 0)
        presences.pointer(id, dimanche, StatutPresence.PRESENT)
        val maintenant = horloge.instant()
        val dao = base.presenceDao()
        assertEquals(-1L, dao.insererSiAbsente(PresenceEntity(id, dimanche, StatutPresence.ABSENT, maintenant, maintenant)))
        assertEquals(StatutPresence.PRESENT, dao.trouver(id, dimanche)!!.statut) // jamais écrasé
        val ligneBrute = base.openHelper.writableDatabase
        try {
            ligneBrute.execSQL("INSERT INTO presences VALUES ($id, '2026-10-11', 'ABSENT', 1, 1)")
            fail("la clé primaire (enfantId, dateDimanche) aurait dû refuser le doublon")
        } catch (attendu: SQLiteConstraintException) {
            // attendu
        }
    }

    @Test
    fun clesEtrangeres_interdisentPresenceOrpheline_etSuppressionAvecHistorique() = runTest {
        val id = inscrire("Rakoto", "Sarah")
        horloge.allerA("2026-10-11", 9, 0)
        presences.pointer(id, dimanche, StatutPresence.PRESENT)
        val brute = base.openHelper.writableDatabase
        for (sql in listOf(
            "INSERT INTO presences VALUES (9999, '2026-10-11', 'PRESENT', 1, 1)", // enfant inexistant
            "INSERT INTO presences VALUES ($id, '2030-01-06', 'PRESENT', 1, 1)", // séance inexistante
            "DELETE FROM enfants WHERE id = $id", // enfant avec historique
            "DELETE FROM seances WHERE dateDimanche = '2026-10-11'", // séance avec historique
        )) {
            try {
                brute.execSQL(sql)
                fail("devrait être refusé : $sql")
            } catch (attendu: SQLiteConstraintException) {
                // attendu : PRAGMA foreign_keys actif et RESTRICT
            }
        }
        assertNotNull(base.enfantDao().trouver(id))
        assertNotNull(base.presenceDao().trouver(id, dimanche))
    }

    @Test
    fun archivage_conserveLHistorique_etExclutLesProchainsDimanches() = runTest {
        val sarah = inscrire("Rakoto", "Sarah")
        horloge.allerA("2026-10-11", 9, 0)
        presences.pointer(sarah, dimanche, StatutPresence.PRESENT)
        horloge.allerA("2026-10-13", 18, 0)
        presences.rattraper()
        assertTrue(enfants.archiver(sarah))
        horloge.allerA("2026-10-26", 8, 0)
        presences.rattraper()
        assertNull(base.presenceDao().trouver(sarah, LocalDate.parse("2026-10-18")))
        assertEquals(StatutPresence.PRESENT, base.presenceDao().trouver(sarah, dimanche)!!.statut)
        assertEquals(CompteursAssiduite(1, 0, 0), presences.observerAssiduite(true).first().single().compteurs)
    }

    @Test
    fun donneesConserveesApresFermetureEtRelance_baseSurDisque() = runTest {
        base.close()
        contexte.deleteDatabase("test_relance.db")
        var db = Room.databaseBuilder(contexte, AppDatabase::class.java, "test_relance.db").build()
        branche(db)
        val id = inscrire("Rakoto", "Sarah")
        horloge.allerA("2026-10-11", 9, 0)
        presences.pointer(id, dimanche, StatutPresence.EN_RETARD)
        db.close()

        db = Room.databaseBuilder(contexte, AppDatabase::class.java, "test_relance.db").build()
        branche(db)
        try {
            val retrouve = db.enfantDao().trouver(id)
            assertNotNull(retrouve)
            assertEquals("Sarah", retrouve!!.prenom)
            assertEquals(dimanche, retrouve.dateDebutSuivi)
            assertEquals(StatutPresence.EN_RETARD, db.presenceDao().trouver(id, dimanche)!!.statut)
        } finally {
            db.close()
            contexte.deleteDatabase("test_relance.db")
        }
        base = Room.inMemoryDatabaseBuilder(contexte, AppDatabase::class.java).build() // pour @After
    }

    @Test
    fun deuxHomonymes_ontDesIdentifiantsDistincts() = runTest {
        val a = inscrire("Rakoto", "Sarah")
        val b = inscrire("Rakoto", "Sarah")
        assertTrue(a != b)
        assertEquals(2, base.enfantDao().observerSelonArchivage(false).first().size)
    }
}
