package mg.ecoledimanche.presences.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mg.ecoledimanche.presences.R
import mg.ecoledimanche.presences.di.ConteneurApp

private val ArgumentId = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })

@Composable
fun NavigationApp(conteneur: ConteneurApp) {
    val navigation = rememberNavController()
    val entree by navigation.currentBackStackEntryAsState()
    val route = entree?.destination?.route

    // Pas de marges système ici : la barre basse gère elle-même la barre de navigation Android,
    // et chaque écran gère ses propres marges (barre d'état, clavier).
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (route != null && route in Routes.ONGLETS) BarreNavigation(route, navigation) },
    ) { marges ->
        NavHost(navController = navigation, startDestination = Routes.DIMANCHE, modifier = Modifier.padding(marges)) {
            composable(Routes.DIMANCHE) {
                DimancheRoute(conteneur, onAjouter = { navigation.navigate(Routes.AJOUTER) { launchSingleTop = true } })
            }
            composable(Routes.ENFANTS) {
                EnfantsRoute(
                    conteneur,
                    onOuvrir = { navigation.navigate(Routes.fiche(it)) },
                    onAjouter = { navigation.navigate(Routes.AJOUTER) { launchSingleTop = true } },
                )
            }
            composable(Routes.ASSIDUITE) {
                AssiduiteRoute(conteneur, onOuvrirHistorique = { navigation.navigate(Routes.historique(it)) })
            }
            composable(Routes.AJOUTER) {
                AjoutRoute(
                    conteneur,
                    onTermine = { id ->
                        navigation.navigate(Routes.fiche(id)) { popUpTo(Routes.AJOUTER) { inclusive = true } }
                    },
                    onAbandon = { navigation.popBackStack() },
                )
            }
            composable(Routes.FICHE, arguments = ArgumentId) { retour ->
                val id = retour.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                FicheRoute(
                    conteneur,
                    enfantId = id,
                    onRetour = { navigation.popBackStack() },
                    onModifier = { navigation.navigate(Routes.modifier(id)) },
                    onChangerPhoto = { navigation.navigate(Routes.photo(id)) },
                    onHistorique = { navigation.navigate(Routes.historique(id)) },
                )
            }
            composable(Routes.MODIFIER, arguments = ArgumentId) { retour ->
                val id = retour.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                ModifierRoute(conteneur, enfantId = id, onTermine = { navigation.popBackStack() }, onRetour = { navigation.popBackStack() })
            }
            composable(Routes.PHOTO, arguments = ArgumentId) { retour ->
                val id = retour.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                ChangerPhotoRoute(conteneur, enfantId = id, onTermine = { navigation.popBackStack() }, onAnnuler = { navigation.popBackStack() })
            }
            composable(Routes.HISTORIQUE, arguments = ArgumentId) { retour ->
                val id = retour.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                HistoriqueRoute(conteneur, enfantId = id, onRetour = { navigation.popBackStack() })
            }
        }
    }
}

/**
 * Quatre accès : Dimanche, Enfants, Ajouter, Assiduité. « Ajouter » est une action centrale
 * (jamais sélectionnée) qui démarre le parcours caméra puis formulaire : pas d'onglet vide.
 */
@Composable
private fun BarreNavigation(routeCourante: String?, navigation: NavHostController) {
    NavigationBar {
        ElementOnglet(Routes.DIMANCHE, R.string.nav_dimanche, Icons.Filled.Today, routeCourante, navigation)
        ElementOnglet(Routes.ENFANTS, R.string.nav_enfants, Icons.Filled.Groups, routeCourante, navigation)
        NavigationBarItem(
            selected = false,
            onClick = { navigation.navigate(Routes.AJOUTER) { launchSingleTop = true } },
            icon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            },
            label = { Text(stringResource(R.string.nav_ajouter)) },
            colors = NavigationBarItemDefaults.colors(unselectedTextColor = MaterialTheme.colorScheme.primary),
        )
        ElementOnglet(Routes.ASSIDUITE, R.string.nav_assiduite, Icons.Filled.Insights, routeCourante, navigation)
    }
}

@Composable
private fun RowScope.ElementOnglet(
    route: String,
    libelle: Int,
    icone: ImageVector,
    routeCourante: String?,
    navigation: NavHostController,
) {
    NavigationBarItem(
        selected = routeCourante == route,
        onClick = {
            navigation.navigate(route) {
                popUpTo(navigation.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icone, contentDescription = null) },
        label = { Text(stringResource(libelle)) },
    )
}
