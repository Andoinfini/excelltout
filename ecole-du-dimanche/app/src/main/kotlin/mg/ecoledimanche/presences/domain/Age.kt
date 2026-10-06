package mg.ecoledimanche.presences.domain

import java.time.LocalDate
import java.time.Period

/**
 * L'âge n'est jamais saisi ni stocké : il est recalculé depuis la date de naissance.
 *
 * Pour une naissance un 29 février, `java.time.Period` fait tomber l'anniversaire le
 * 1er mars des années non bissextiles (le 28 février, l'enfant n'a pas encore l'âge).
 */
object Age {
    fun enAnnees(dateNaissance: LocalDate, aujourdhui: LocalDate): Int =
        Period.between(dateNaissance, aujourdhui).years.coerceAtLeast(0)
}
