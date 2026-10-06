package mg.ecoledimanche.presences.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Les ViewModels lancent leurs coroutines sur le dispatcher « Main » : on le remplace par un
 * dispatcher de test, dont `advanceUntilIdle()` exécute le travail en attente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TestAvecMain {
    protected val dispatcher = StandardTestDispatcher()

    @Before
    fun installerMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun retirerMain() {
        Dispatchers.resetMain()
    }
}
