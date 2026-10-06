package mg.ecoledimanche.presences.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Affichage des dates et heures en français (jamais utilisé pour le stockage). */
object FormatsFr {
    private val dateCourte: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRENCH)
    private val dateLongue: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.FRENCH)

    /** JJ/MM/AAAA. */
    fun dateCourte(date: LocalDate): String = dateCourte.format(date)

    /** « dimanche 11 octobre 2026 ». */
    fun dateLongue(date: LocalDate): String = dateLongue.format(date)

    /** « DIMANCHE 11 OCTOBRE 2026 ». */
    fun dateLongueMajuscules(date: LocalDate): String = dateLongue(date).uppercase(Locale.FRENCH)

    /** « 10 h » ou « 10 h 30 ». */
    fun heure(heure: LocalTime): String =
        if (heure.minute == 0) "${heure.hour} h" else "${heure.hour} h ${heure.minute.toString().padStart(2, '0')}"
}
