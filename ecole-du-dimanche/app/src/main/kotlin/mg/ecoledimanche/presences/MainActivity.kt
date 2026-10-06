package mg.ecoledimanche.presences

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import java.util.Locale
import mg.ecoledimanche.presences.ui.RacineApp
import mg.ecoledimanche.presences.ui.components.LocalStockagePhotos
import mg.ecoledimanche.presences.ui.theme.EcoleDimancheTheme

class MainActivity : ComponentActivity() {
    /**
     * L'interface est entièrement en français : on impose la langue pour que les composants du
     * système (calendrier, boutons) le soient aussi, même sur un téléphone réglé dans une autre langue.
     */
    override fun attachBaseContext(nouveauContexte: Context) {
        val configuration = Configuration(nouveauContexte.resources.configuration)
        configuration.setLocale(Locale.FRENCH)
        super.attachBaseContext(nouveauContexte.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val conteneur = (application as EcoleDimancheApp).conteneur
        setContent {
            EcoleDimancheTheme {
                CompositionLocalProvider(LocalStockagePhotos provides conteneur.stockagePhotos) {
                    RacineApp(conteneur)
                }
            }
        }
    }
}
