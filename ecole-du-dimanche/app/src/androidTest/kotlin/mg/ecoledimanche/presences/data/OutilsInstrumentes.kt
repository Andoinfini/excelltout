package mg.ecoledimanche.presences.data

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import mg.ecoledimanche.presences.data.repository.StockagePhotos
import mg.ecoledimanche.presences.domain.AppClock
import mg.ecoledimanche.presences.domain.DonneesEnfant
import mg.ecoledimanche.presences.domain.Sexe

/** Horloge contrôlée pour les tests instrumentés. */
class HorlogeControlee(var maintenant: Instant, var fuseau: ZoneId = ZoneId.of("Indian/Antananarivo")) : AppClock {
    override fun instant(): Instant = maintenant
    override fun zone(): ZoneId = fuseau

    fun allerA(date: String, heure: Int, minute: Int = 0, seconde: Int = 0) {
        maintenant = LocalDateTime.of(LocalDate.parse(date), LocalTime.of(heure, minute, seconde)).atZone(fuseau).toInstant()
    }
}

/** Stockage de photos factice : les tests de base de données n'ont pas besoin de vrais fichiers. */
class StockageFactice : StockagePhotos {
    val fichiers = LinkedHashSet<String>()
    private var numero = 0

    fun nouvelleTemporaire(): String = "photos_tmp/t${++numero}.jpg".also { fichiers.add(it) }

    override suspend fun promouvoirTemporaire(cheminTemporaire: String): String {
        if (cheminTemporaire !in fichiers) throw IOException("introuvable")
        return "photos/f${++numero}.jpg".also { fichiers.add(it) }
    }

    override suspend fun supprimer(chemin: String) {
        fichiers.remove(chemin)
    }

    override suspend fun existe(chemin: String): Boolean = chemin in fichiers
}

fun donneesEnfant(nom: String, prenom: String) = DonneesEnfant(
    nom = nom,
    prenom = prenom,
    dateNaissance = LocalDate.parse("2017-03-15"),
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
