package mg.ecoledimanche.presences.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import mg.ecoledimanche.presences.data.repository.EnfantRepository
import mg.ecoledimanche.presences.data.repository.PresenceRepository
import mg.ecoledimanche.presences.domain.DonneesEnfant
import mg.ecoledimanche.presences.domain.Sexe

val MADAGASCAR: ZoneId = ZoneId.of("Indian/Antananarivo")

fun instantLocal(date: String, heure: Int = 12, minute: Int = 0, seconde: Int = 0, zone: ZoneId = MADAGASCAR): Instant =
    LocalDateTime.of(LocalDate.parse(date), LocalTime.of(heure, minute, seconde)).atZone(zone).toInstant()

fun donnees(nom: String, prenom: String, naissance: String = "2017-03-15") = DonneesEnfant(
    nom = nom,
    prenom = prenom,
    dateNaissance = LocalDate.parse(naissance),
    sexe = Sexe.FILLE,
    adresse = null,
    nomPere = null,
    pereMembre = null,
    nomMere = null,
    mereMembre = null,
    nombreFreresSoeurs = 0,
    nombreFreresSoeursMembres = 0,
    dateArrivee = null,
)

/** Assemble les vrais repositories sur le faux magasin et une horloge contrôlée. */
class Environnement(debut: Instant) {
    val magasin = FauxMagasin()
    val photos = FauxStockagePhotos()
    val horloge = HorlogeTest(debut)

    val enfants = EnfantRepository(magasin.enfantDao, magasin.seanceDao, magasin.transaction, photos, horloge)
    val presences = PresenceRepository(magasin.enfantDao, magasin.seanceDao, magasin.presenceDao, magasin.transaction, horloge)

    fun allerA(date: String, heure: Int, minute: Int = 0, seconde: Int = 0, zone: ZoneId = horloge.fuseau) {
        horloge.maintenant = instantLocal(date, heure, minute, seconde, zone)
        horloge.fuseau = zone
    }

    suspend fun inscrire(nom: String, prenom: String): Long = enfants.ajouter(donnees(nom, prenom), photos.nouvelleTemporaire())
}
