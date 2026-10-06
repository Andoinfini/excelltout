package mg.ecoledimanche.presences.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Accès au temps isolé et injectable : le téléphone est hors ligne, son horloge est la seule
 * référence. Les tests utilisent une horloge contrôlée pour simuler un dimanche à 09 h 59 / 10 h 00.
 *
 * Le fuseau est relu à chaque appel : il peut changer pendant que l'application tourne.
 */
interface AppClock {
    fun instant(): Instant
    fun zone(): ZoneId
}

object SystemAppClock : AppClock {
    override fun instant(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Date civile locale actuelle (ne pas utiliser `LocalDate.ofInstant` : API 34+ seulement). */
fun AppClock.aujourdhui(): LocalDate = instant().atZone(zone()).toLocalDate()
