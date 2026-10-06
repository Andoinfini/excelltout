package mg.ecoledimanche.presences.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClairPrimaire = Color(0xFF1F4E79)
private val ClairSurface = Color(0xFFF8F9FB)

private val SchemaClair = lightColorScheme(
    primary = ClairPrimaire,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0B2A47),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B0),
    onSecondaryContainer = Color(0xFF2B1A00),
    background = ClairSurface,
    onBackground = Color(0xFF1A1C1E),
    surface = ClairSurface,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E6EB),
    onSurfaceVariant = Color(0xFF414B55),
    outline = Color(0xFF6F7A85),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val SchemaSombre = darkColorScheme(
    primary = Color(0xFFA5C8EE),
    onPrimary = Color(0xFF0B2A47),
    primaryContainer = Color(0xFF254B72),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFFFFC66B),
    onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFE2B0),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E4E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE2E4E8),
    surfaceVariant = Color(0xFF3B434B),
    onSurfaceVariant = Color(0xFFC1C8D0),
    outline = Color(0xFF8B949E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Thème sobre de l'application : couleurs fixes (pas de couleurs dynamiques) pour des statuts toujours lisibles. */
@Composable
fun EcoleDimancheTheme(
    sombre: Boolean = isSystemInDarkTheme(),
    contenu: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (sombre) SchemaSombre else SchemaClair,
        content = contenu,
    )
}
