package mg.ecoledimanche.presences.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import mg.ecoledimanche.presences.camera.StockagePhotosPrive

val LocalStockagePhotos = staticCompositionLocalOf<StockagePhotosPrive> {
    error("LocalStockagePhotos n'est pas fourni : voir MainActivity")
}

/**
 * Photo d'un enfant. Tant que l'image n'est pas chargée, ou si le fichier est absent ou illisible,
 * un visuel neutre est affiché : l'application ne plante jamais à cause d'une photo.
 */
@Composable
fun PhotoEnfant(
    chemin: String?,
    description: String?,
    modifier: Modifier = Modifier,
    coteMaxPx: Int = 480,
    forme: Shape = RoundedCornerShape(12.dp),
    echelle: ContentScale = ContentScale.Crop,
) {
    val stockage = LocalStockagePhotos.current
    val image by produceState<ImageBitmap?>(initialValue = null, chemin, coteMaxPx) {
        value = if (chemin == null) null else ChargeurImages.charger(stockage, chemin, coteMaxPx)
    }
    Box(
        modifier = modifier
            .clip(forme)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = echelle,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.55f),
            )
        }
    }
}
