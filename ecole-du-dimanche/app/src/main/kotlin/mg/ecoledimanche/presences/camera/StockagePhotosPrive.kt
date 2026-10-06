package mg.ecoledimanche.presences.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mg.ecoledimanche.presences.data.repository.StockagePhotos

/**
 * Stockage des photos dans le répertoire PRIVÉ de l'application (jamais le cache, jamais une
 * galerie publique, aucune permission de stockage).
 *
 * - `photos/`     : photos définitives. La base ne retient que le chemin RELATIF (`photos/<uuid>.jpg`).
 * - `photos_tmp/` : photos brutes et photos validées en attente du formulaire (brouillon).
 *
 * Toutes les opérations de fichiers s'exécutent hors du thread principal.
 */
class StockagePhotosPrive(racine: File) : StockagePhotos {
    private val racine: File = racine.canonicalFile
    private val dossierPhotos = File(this.racine, DOSSIER_PHOTOS)
    private val dossierTemporaire = File(this.racine, DOSSIER_TEMPORAIRE)

    /** Fichier correspondant à un chemin relatif, ou nul si le chemin sort du répertoire privé. */
    fun resoudre(cheminRelatif: String): File? {
        val fichier = try {
            File(racine, cheminRelatif).canonicalFile
        } catch (e: IOException) {
            return null
        }
        return fichier.takeIf { it.path.startsWith(racine.path + File.separator) }
    }

    /** Nouveau fichier pour la capture brute de CameraX (supprimé après traitement). */
    fun nouveauFichierBrut(): File {
        dossierTemporaire.mkdirs()
        return File(dossierTemporaire, "brut_${UUID.randomUUID()}.jpg")
    }

    /**
     * Transforme la capture brute en photo temporaire exploitable : orientation corrigée,
     * redimensionnée (grand côté <= [CalculsImage.COTE_MAX]) et recompressée en JPEG.
     * Le fichier brut est supprimé dans tous les cas.
     * @return le chemin relatif de la photo temporaire.
     */
    suspend fun traiterPhotoCapturee(brut: File): String = withContext(Dispatchers.IO) {
        try {
            dossierTemporaire.mkdirs()
            val cible = File(dossierTemporaire, "${UUID.randomUUID()}.jpg")
            ecrireRedimensionnee(brut, cible)
            relatif(cible)
        } finally {
            brut.delete()
        }
    }

    override suspend fun promouvoirTemporaire(cheminTemporaire: String): String = withContext(Dispatchers.IO) {
        val source = resoudre(cheminTemporaire)?.takeIf { it.isFile && dans(it, dossierTemporaire) }
            ?: throw IOException("Photo temporaire introuvable : $cheminTemporaire")
        if (!decodable(source)) throw IOException("Photo temporaire illisible : $cheminTemporaire")

        dossierPhotos.mkdirs()
        val destination = File(dossierPhotos, "${UUID.randomUUID()}.jpg")
        val partiel = File(dossierPhotos, destination.name + EXTENSION_PARTIELLE)
        try {
            source.copyTo(partiel, overwrite = false)
            if (!partiel.renameTo(destination)) throw IOException("Écriture de la photo impossible")
            // Le fichier copié garde parfois l'ancienne date : on la rafraîchit pour que le
            // nettoyage des orphelins ne le prenne jamais pour un fichier ancien.
            destination.setLastModified(System.currentTimeMillis())
            if (!decodable(destination)) throw IOException("La photo enregistrée est illisible")
        } catch (erreur: Throwable) {
            partiel.delete()
            destination.delete()
            throw erreur
        }
        relatif(destination)
    }

    override suspend fun supprimer(chemin: String) {
        withContext(Dispatchers.IO) {
            resoudre(chemin)
                ?.takeIf { dans(it, dossierPhotos) || dans(it, dossierTemporaire) }
                ?.delete()
        }
    }

    override suspend fun existe(chemin: String): Boolean = withContext(Dispatchers.IO) {
        resoudre(chemin)?.let { it.isFile && decodable(it) } ?: false
    }

    /**
     * Retire les fichiers orphelins : photos définitives que plus aucune fiche ne référence
     * (et assez anciennes), brouillons abandonnés, restes d'écritures interrompues.
     * Un fichier encore référencé ou récent n'est jamais supprimé.
     */
    suspend fun nettoyer(cheminsReferences: Set<String>, maintenant: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            val definitifs = lister(dossierPhotos)
            PolitiqueNettoyage.orphelinesASupprimer(definitifs, cheminsReferences, maintenant)
                .forEach { resoudre(it.chemin)?.delete() }
            PolitiqueNettoyage.temporairesASupprimer(lister(dossierTemporaire), maintenant)
                .forEach { resoudre(it.chemin)?.delete() }
        }
    }

    private fun lister(dossier: File): List<FichierStocke> =
        dossier.listFiles { fichier -> fichier.isFile }
            ?.map { FichierStocke(relatif(it), it.lastModified()) }
            .orEmpty()

    private fun relatif(fichier: File): String = fichier.toRelativeString(racine).replace(File.separatorChar, '/')

    private fun dans(fichier: File, dossier: File): Boolean = fichier.path.startsWith(dossier.path + File.separator)

    private fun decodable(fichier: File): Boolean {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fichier.path, bornes)
        return bornes.outWidth > 0 && bornes.outHeight > 0
    }

    private fun ecrireRedimensionnee(source: File, cible: File) {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) throw IOException("Image illisible")

        val options = BitmapFactory.Options().apply {
            inSampleSize = CalculsImage.echantillonnage(bornes.outWidth, bornes.outHeight)
        }
        val decodee = BitmapFactory.decodeFile(source.path, options) ?: throw IOException("Décodage de l'image impossible")
        var finale: Bitmap = decodee
        try {
            val matrice = matriceOrientation(lireOrientation(source))
            val (largeurCible, _) = CalculsImage.dimensionsCibles(decodee.width, decodee.height)
            if (largeurCible != decodee.width) {
                val facteur = largeurCible.toFloat() / decodee.width
                matrice.postScale(facteur, facteur)
            }
            finale = Bitmap.createBitmap(decodee, 0, 0, decodee.width, decodee.height, matrice, true)

            val partiel = File(cible.parentFile, cible.name + EXTENSION_PARTIELLE)
            try {
                FileOutputStream(partiel).use { sortie ->
                    if (!finale.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, sortie)) {
                        throw IOException("Compression de l'image impossible")
                    }
                }
                if (!partiel.renameTo(cible)) throw IOException("Écriture de l'image impossible")
            } finally {
                partiel.delete()
            }
        } finally {
            if (finale !== decodee) finale.recycle()
            decodee.recycle()
        }
    }

    private fun lireOrientation(fichier: File): Int = try {
        ExifInterface(fichier.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun matriceOrientation(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    companion object {
        const val DOSSIER_PHOTOS = "photos"
        const val DOSSIER_TEMPORAIRE = "photos_tmp"
        private const val QUALITE_JPEG = 85
        private const val EXTENSION_PARTIELLE = ".part"
    }
}
