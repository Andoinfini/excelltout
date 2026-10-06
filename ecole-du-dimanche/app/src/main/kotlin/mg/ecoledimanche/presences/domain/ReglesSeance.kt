package mg.ecoledimanche.presences.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Réglage unique de la V1 : heure locale de clôture de la séance du dimanche. */
object ConfigurationSeance {
    val HEURE_CLOTURE: LocalTime = LocalTime.of(10, 0)
}

/** Situation d'une séance par rapport à l'horloge. */
enum class PhaseSeance {
    /** Dimanche futur : prévu, pointage désactivé, aucune absence. */
    A_VENIR,

    /** Dimanche du jour avant la clôture : pointage ouvert. */
    EN_COURS,

    /** Clôture atteinte (ou déjà matérialisée) : les non pointés sont absents. */
    CLOTUREE,
}

/** Ce que les règles ont besoin de savoir d'une séance déjà enregistrée. */
data class EtatSeance(
    val cloturee: Boolean,
    val cloturePrevueAt: Instant,
)

object Dimanches {
    fun estDimanche(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SUNDAY

    /** Premier dimanche strictement postérieur à [date]. */
    fun suivantStrict(date: LocalDate): LocalDate = date.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

    /** [date] si c'est un dimanche, sinon le prochain dimanche. */
    fun courantOuSuivant(date: LocalDate): LocalDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    /** Dimanche précédant strictement [date]. */
    fun precedentStrict(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))

    /** Tous les dimanches de l'intervalle [debut, fin], bornes incluses, du plus ancien au plus récent. */
    fun entre(debut: LocalDate, fin: LocalDate): List<LocalDate> {
        val resultat = ArrayList<LocalDate>()
        var courant = courantOuSuivant(debut)
        while (!courant.isAfter(fin)) {
            resultat.add(courant)
            courant = courant.plusWeeks(1)
        }
        return resultat
    }

    /**
     * Dimanche affiché par défaut : celui du jour si l'on est dimanche (même après la clôture),
     * sinon le prochain dimanche, pour préparer le pointage.
     */
    fun parDefaut(aujourdhui: LocalDate): LocalDate = courantOuSuivant(aujourdhui)
}

object RegleSeance {
    /** Instant de clôture (10 h locale) d'un dimanche pour un fuseau donné. */
    fun instantCloture(dimanche: LocalDate, zone: ZoneId): Instant =
        dimanche.atTime(ConfigurationSeance.HEURE_CLOTURE).atZone(zone).toInstant()

    /**
     * Phase d'une séance.
     *
     * - Une séance déjà marquée clôturée le reste : ni un changement de fuseau ni un retour
     *   en arrière de l'horloge ne la rouvrent.
     * - L'échéance utilisée est celle enregistrée à la création de la séance ; à défaut, celle
     *   calculée avec le fuseau actuel.
     */
    fun phase(
        dimanche: LocalDate,
        seance: EtatSeance?,
        maintenant: Instant,
        zone: ZoneId,
    ): PhaseSeance {
        if (seance?.cloturee == true) return PhaseSeance.CLOTUREE
        val echeance = seance?.cloturePrevueAt ?: instantCloture(dimanche, zone)
        if (!maintenant.isBefore(echeance)) return PhaseSeance.CLOTUREE
        val aujourdhui = maintenant.atZone(zone).toLocalDate()
        return if (dimanche.isAfter(aujourdhui)) PhaseSeance.A_VENIR else PhaseSeance.EN_COURS
    }

    /**
     * Prochain instant de clôture strictement postérieur à [maintenant] : le dimanche du jour s'il
     * n'est pas encore 10 h, sinon le dimanche suivant. Sert à planifier le travail d'arrière-plan.
     */
    fun prochaineCloture(maintenant: Instant, zone: ZoneId): Instant {
        val aujourdhui = maintenant.atZone(zone).toLocalDate()
        val candidat = instantCloture(Dimanches.courantOuSuivant(aujourdhui), zone)
        return if (candidat.isAfter(maintenant)) candidat else instantCloture(Dimanches.suivantStrict(aujourdhui), zone)
    }
}

/** Instants auxquels un écran affiché doit se réactualiser tout seul (sans action de l'utilisateur). */
object Frontieres {
    /** Filet de sécurité : l'horloge ou le fuseau peuvent changer, on ne dort jamais plus longtemps. */
    val ATTENTE_MAX: Duration = Duration.ofMinutes(15)

    /** Délai avant la prochaine clôture, le prochain minuit (changement de « jour »), ou [ATTENTE_MAX]. */
    fun delaiAvantProchaine(maintenant: Instant, zone: ZoneId): Duration {
        val minuit = maintenant.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val prochaine = minOf(minuit, RegleSeance.prochaineCloture(maintenant, zone), maintenant.plus(ATTENTE_MAX))
        return Duration.between(maintenant, prochaine).plusMillis(MARGE_MS)
    }

    private const val MARGE_MS = 250L
}

object RegleSuivi {
    /**
     * Début de suivi d'un enfant ajouté « maintenant » :
     * - un dimanche avant la clôture : ce dimanche ;
     * - un dimanche à partir de la clôture, ou du lundi au samedi : le dimanche suivant.
     *
     * [phaseDuJour] est la phase de la séance d'aujourd'hui (utile seulement un dimanche).
     */
    fun dateDebutSuivi(aujourdhui: LocalDate, phaseDuJour: PhaseSeance): LocalDate =
        if (Dimanches.estDimanche(aujourdhui) && phaseDuJour == PhaseSeance.EN_COURS) {
            aujourdhui
        } else {
            Dimanches.suivantStrict(aujourdhui)
        }

    /** Version sans séance enregistrée, calculée uniquement depuis l'horloge. */
    fun dateDebutSuivi(maintenant: Instant, zone: ZoneId): LocalDate {
        val aujourdhui = maintenant.atZone(zone).toLocalDate()
        val phase = RegleSeance.phase(aujourdhui, null, maintenant, zone)
        return dateDebutSuivi(aujourdhui, phase)
    }

    /**
     * Un enfant est admissible à un dimanche si celui-ci est postérieur ou égal au début de suivi
     * et, s'il y a une fin de suivi, antérieur ou égal à celle-ci (borne incluse).
     */
    fun estAdmissible(dimanche: LocalDate, debutSuivi: LocalDate, finSuivi: LocalDate?): Boolean =
        !dimanche.isBefore(debutSuivi) && (finSuivi == null || !dimanche.isAfter(finSuivi))
}
