package mg.ecoledimanche.presences.camera

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.ui.components.EcranChargement

private enum class Refus { AUCUN, TEMPORAIRE, DEFINITIF }

/**
 * Affiche [contenu] (la caméra) uniquement quand la permission CAMERA est accordée.
 *
 * - Déjà accordée : la caméra s'ouvre directement, sans aucune demande.
 * - Sinon : une seule demande automatique à l'ouverture de l'écran (pas de boucle à chaque ouverture).
 * - Refus : explication en français avec « Réessayer » (refus simple), « Ouvrir les paramètres »
 *   (refus définitif) et « Annuler l'ajout ». Le reste de l'application reste accessible.
 * - Au retour des paramètres, l'autorisation est relue.
 */
@Composable
fun PorteCamera(onAnnuler: () -> Unit, contenu: @Composable () -> Unit) {
    val contexte = LocalContext.current
    var accordee by remember { mutableStateOf(permissionAccordee(contexte)) }
    var refus by rememberSaveable { mutableStateOf(Refus.AUCUN) }
    var dejaDemande by rememberSaveable { mutableStateOf(false) }

    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accorde ->
        accordee = accorde
        refus = when {
            accorde -> Refus.AUCUN
            // « Ne plus demander » (ou refus déjà définitif) : le système ne propose plus la fenêtre.
            contexte.trouverActivite()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true -> Refus.TEMPORAIRE
            else -> Refus.DEFINITIF
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accordee = permissionAccordee(contexte)
        if (accordee) refus = Refus.AUCUN
    }

    LaunchedEffect(Unit) {
        if (!accordee && !dejaDemande) {
            dejaDemande = true
            lanceur.launch(Manifest.permission.CAMERA)
        }
    }

    when {
        accordee -> contenu()
        refus != Refus.AUCUN -> ExplicationPermissionCamera(
            refusDefinitif = refus == Refus.DEFINITIF,
            onReessayer = { lanceur.launch(Manifest.permission.CAMERA) },
            onOuvrirParametres = { ouvrirParametres(contexte) },
            onAnnuler = onAnnuler,
        )
        else -> EcranChargement(stringResource(R.string.camera_chargement))
    }
}

private fun permissionAccordee(contexte: Context): Boolean =
    ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.trouverActivite(): Activity? {
    var courant: Context? = this
    while (courant is ContextWrapper) {
        if (courant is Activity) return courant
        courant = courant.baseContext
    }
    return null
}

private fun ouvrirParametres(contexte: Context) {
    val intention = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexte.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        contexte.startActivity(intention)
    } catch (erreur: ActivityNotFoundException) {
        // Aucun écran de paramètres disponible : l'utilisateur peut annuler l'ajout.
    }
}
