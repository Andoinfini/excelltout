package mg.ecoledimanche.presences.camera

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import mg.ecoledimanche.presences.R

enum class ErreurCamera { INDISPONIBLE, CAPTURE }

/**
 * Aperçu CameraX natif et bouton de capture. La capture brute est écrite dans le stockage privé
 * (jamais dans une galerie publique) ; le traitement (orientation, taille) est fait ensuite par
 * [StockagePhotosPrive.traiterPhotoCapturee].
 */
@Composable
fun CameraCapture(
    stockage: StockagePhotosPrive,
    onCapture: (File) -> Unit,
    onErreur: (ErreurCamera) -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexte = LocalContext.current
    val proprietaire = LocalLifecycleOwner.current
    val executeur = remember { ContextCompat.getMainExecutor(contexte) }
    val vue = remember { PreviewView(contexte).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var fournisseur by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var face by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var deuxCameras by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    val surErreur by rememberUpdatedState(onErreur)
    val surCapture by rememberUpdatedState(onCapture)

    LaunchedEffect(Unit) {
        try {
            fournisseur = ProcessCameraProvider.getInstance(contexte).attendre(executeur)
        } catch (erreur: Exception) {
            surErreur(ErreurCamera.INDISPONIBLE)
        }
    }

    LaunchedEffect(fournisseur, face) {
        val gestionnaire = fournisseur ?: return@LaunchedEffect
        try {
            val arriere = gestionnaire.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val avant = gestionnaire.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            deuxCameras = arriere && avant
            if (!arriere && !avant) {
                surErreur(ErreurCamera.INDISPONIBLE)
                return@LaunchedEffect
            }
            val faceEffective = when {
                face == CameraSelector.LENS_FACING_BACK && !arriere -> CameraSelector.LENS_FACING_FRONT
                face == CameraSelector.LENS_FACING_FRONT && !avant -> CameraSelector.LENS_FACING_BACK
                else -> face
            }
            val selecteur = CameraSelector.Builder().requireLensFacing(faceEffective).build()
            val apercu = Preview.Builder().build().also { it.surfaceProvider = vue.surfaceProvider }
            gestionnaire.unbindAll()
            gestionnaire.bindToLifecycle(proprietaire, selecteur, apercu, capture)
        } catch (erreur: Exception) {
            surErreur(ErreurCamera.INDISPONIBLE)
        }
    }

    DisposableEffect(fournisseur) {
        onDispose { fournisseur?.unbindAll() }
    }

    val descriptionCapture = stringResource(R.string.camera_prendre)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { vue }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onFermer,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.camera_fermer), tint = Color.White)
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(modifier = Modifier.size(56.dp)) // équilibre visuel avec le bouton de bascule
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (enCours) Color.Gray else Color.White)
                    .semantics { contentDescription = descriptionCapture }
                    .clickable(enabled = !enCours, role = Role.Button) {
                        enCours = true
                        val brut = stockage.nouveauFichierBrut()
                        capture.targetRotation = vue.display?.rotation ?: Surface.ROTATION_0
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(brut).build(),
                            executeur,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(resultat: ImageCapture.OutputFileResults) {
                                    enCours = false
                                    surCapture(brut)
                                }

                                override fun onError(erreur: ImageCaptureException) {
                                    enCours = false
                                    brut.delete()
                                    surErreur(ErreurCamera.CAPTURE)
                                }
                            },
                        )
                    },
            )
            if (deuxCameras) {
                IconButton(
                    onClick = {
                        face = if (face == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Filled.FlipCameraAndroid, contentDescription = stringResource(R.string.camera_changer), tint = Color.White)
                }
            } else {
                Box(modifier = Modifier.size(56.dp))
            }
        }
    }
}

private suspend fun <T> ListenableFuture<T>.attendre(executeur: Executor): T = suspendCancellableCoroutine { suite ->
    addListener(
        {
            try {
                suite.resume(get())
            } catch (erreur: Exception) {
                suite.resumeWithException(erreur)
            }
        },
        executeur,
    )
    suite.invokeOnCancellation { cancel(false) }
}
