package mg.ecoledimanche.presences.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Fabrique de ViewModel sans bibliothèque d'injection : `viewModel(factory = fabrique { MonVm(...) })`. */
inline fun <reified VM : ViewModel> fabrique(crossinline creer: CreationExtras.() -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { creer() } }
