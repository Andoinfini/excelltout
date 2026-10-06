package mg.ecoledimanche.presences.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mg.ecoledimanche.presences.camera.CalculsImage
import mg.ecoledimanche.presences.camera.StockagePhotosPrive

/**
 * Chargement des photos hors du thread principal, avec décodage réduit à la taille affichée et
 * cache mémoire : la grille reste fluide même avec de nombreuses photos.
 * Un fichier absent, illisible ou trop gros pour la mémoire donne `null` (jamais d'exception).
 */
object ChargeurImages {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    suspend fun charger(stockage: StockagePhotosPrive, chemin: String, coteMax: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val fichier = stockage.resoudre(chemin)?.takeIf { it.isFile } ?: return@withContext null
                // La date de modification fait partie de la clé : une photo remplacée n'est jamais servie périmée.
                val cle = "$chemin|${fichier.lastModified()}|$coteMax"
                cache.get(cle)?.let { return@withContext it.asImageBitmap() }

                val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(fichier.path, bornes)
                if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = CalculsImage.echantillonnage(bornes.outWidth, bornes.outHeight, coteMax)
                }
                val bitmap = BitmapFactory.decodeFile(fichier.path, options) ?: return@withContext null
                cache.put(cle, bitmap)
                bitmap.asImageBitmap()
            } catch (erreur: OutOfMemoryError) {
                null
            } catch (erreur: Exception) {
                null
            }
        }
}
