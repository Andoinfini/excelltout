package mg.ecoledimanche.presences.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.domain.StatutPresence

/**
 * Apparence d'un statut. La couleur ne suffit jamais : chaque statut a aussi une icône et un libellé.
 * Contrastes vérifiés (texte blanc sur vert/rouge/ardoise >= 4,5:1 ; texte sombre sur gris clair).
 */
@Immutable
class StyleStatut(
    val fond: Color,
    val contenu: Color,
    val icone: ImageVector,
    val libelle: Int,
)

object CouleursStatut {
    val Vert = Color(0xFF2E7D32)
    val Rouge = Color(0xFFC62828)
    val GrisClair = Color(0xFFE3E6EA)
    val NeutreFonce = Color(0xFF455A64)
}

private val StyleNonEnregistre = StyleStatut(CouleursStatut.GrisClair, Color(0xFF1F2933), Icons.Filled.QuestionMark, R.string.statut_non_enregistre)
private val StylePresent = StyleStatut(CouleursStatut.Vert, Color.White, Icons.Filled.CheckCircle, R.string.statut_present)
private val StyleEnRetard = StyleStatut(CouleursStatut.Rouge, Color.White, Icons.Filled.Schedule, R.string.statut_en_retard)
private val StyleAbsent = StyleStatut(CouleursStatut.NeutreFonce, Color.White, Icons.Filled.Block, R.string.statut_absent)

fun StatutPresence.style(): StyleStatut = when (this) {
    StatutPresence.NON_ENREGISTRE -> StyleNonEnregistre
    StatutPresence.PRESENT -> StylePresent
    StatutPresence.EN_RETARD -> StyleEnRetard
    StatutPresence.ABSENT -> StyleAbsent
}
