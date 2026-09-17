package digital.tonima.noisnapista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowWidthSizeClass
import dagger.hilt.android.AndroidEntryPoint
import digital.tonima.noisnapista.feature.tracker.bridge.DebugDetailNavKey
import digital.tonima.noisnapista.feature.tracker.bridge.DebugNavKey
import digital.tonima.noisnapista.feature.tracker.bridge.HistoryNavKey
import digital.tonima.noisnapista.feature.tracker.bridge.TrackerNavKey
import digital.tonima.noisnapista.feature.tracker.impl.DebugDetailScreen
import digital.tonima.noisnapista.feature.tracker.impl.DebugListScreen
import digital.tonima.noisnapista.feature.tracker.impl.DebugViewModel
import digital.tonima.noisnapista.feature.tracker.impl.HistoryScreen
import digital.tonima.noisnapista.feature.tracker.impl.HistoryViewModel
import digital.tonima.noisnapista.feature.tracker.impl.TrackerScreen
import digital.tonima.noisnapista.feature.map.bridge.MapNavKey
import digital.tonima.noisnapista.feature.map.impl.MapScreen
import digital.tonima.noisnapista.feature.map.impl.MapViewModel
import digital.tonima.noisnapista.feature.onboarding.OnboardingScreen
import digital.tonima.noisnapista.feature.onboarding.OnboardingViewModel
import digital.tonima.noisnapista.feature.ranking.bridge.RankingNavKey
import digital.tonima.noisnapista.feature.ranking.impl.RankingScreen
import digital.tonima.noisnapista.feature.ranking.impl.RankingViewModel
import digital.tonima.noisnapista.ui.theme.NóisNaPistaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NóisNaPistaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                    val hasCompletedOnboarding by onboardingViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()

                    when (hasCompletedOnboarding) {
                        // Ainda lendo o DataStore — não renderiza nada por um frame em vez de
                        // arriscar mostrar a onboarding pra quem já passou por ela antes.
                        null -> Unit
                        false -> OnboardingScreen(onFinish = onboardingViewModel::onOnboardingFinished)
                        true -> MainContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    val backStack = rememberNavBackStack(TrackerNavKey)
    val currentKey = backStack.lastOrNull() ?: TrackerNavKey
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                icon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = stringResource(R.string.nav_home)) },
                label = { Text(stringResource(R.string.nav_home)) },
                selected = currentKey is TrackerNavKey,
                onClick = {
                    if (currentKey !is TrackerNavKey) {
                        backStack.clear()
                        backStack.add(TrackerNavKey)
                    }
                }
            )
            item(
                icon = { Icon(Icons.Rounded.Map, contentDescription = stringResource(R.string.nav_map)) },
                label = { Text(stringResource(R.string.nav_map)) },
                selected = currentKey is MapNavKey,
                onClick = {
                    if (currentKey !is MapNavKey) {
                        backStack.clear()
                        backStack.add(MapNavKey)
                    }
                }
            )
            item(
                icon = { Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.nav_history)) },
                label = { Text(stringResource(R.string.nav_history)) },
                selected = currentKey is HistoryNavKey,
                onClick = {
                    if (currentKey !is HistoryNavKey) {
                        backStack.clear()
                        backStack.add(HistoryNavKey)
                    }
                }
            )
            item(
                icon = { Icon(Icons.Rounded.Leaderboard, contentDescription = stringResource(R.string.nav_ranking)) },
                label = { Text(stringResource(R.string.nav_ranking)) },
                selected = currentKey is RankingNavKey,
                onClick = {
                    if (currentKey !is RankingNavKey) {
                        backStack.clear()
                        backStack.add(RankingNavKey)
                    }
                }
            )
            // Debug-only: a detailed per-detection view for classifying captured
            // sensor data (ML labeling). Must never appear in a release build.
            if (BuildConfig.DEBUG) {
                item(
                    icon = { Icon(Icons.Rounded.BugReport, contentDescription = stringResource(R.string.nav_debug)) },
                    label = { Text(stringResource(R.string.nav_debug)) },
                    selected = currentKey is DebugNavKey || currentKey is DebugDetailNavKey,
                    onClick = {
                        if (currentKey !is DebugNavKey) {
                            backStack.clear()
                            backStack.add(DebugNavKey)
                        }
                    }
                )
            }
        }
    ) {
        if (isExpanded && currentKey is TrackerNavKey) {
            Row(modifier = Modifier.fillMaxSize()) {
                TrackerScreen(
                    viewModel = hiltViewModel(),
                    onNavigateToMap = {
                        backStack.clear()
                        backStack.add(MapNavKey)
                    },
                    modifier = Modifier.weight(1f)
                )
                MapScreen(
                    viewModel = hiltViewModel<MapViewModel>(),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = { key: NavKey ->
                    when (key) {
                        is TrackerNavKey -> NavEntry(key) {
                            TrackerScreen(
                                viewModel = hiltViewModel(),
                                onNavigateToMap = {
                                    backStack.clear()
                                    backStack.add(MapNavKey)
                                }
                            )
                        }
                        is MapNavKey -> NavEntry(key) {
                            MapScreen(viewModel = hiltViewModel<MapViewModel>())
                        }
                        is HistoryNavKey -> NavEntry(key) {
                            HistoryScreen(viewModel = hiltViewModel<HistoryViewModel>())
                        }
                        is RankingNavKey -> NavEntry(key) {
                            RankingScreen(viewModel = hiltViewModel<RankingViewModel>())
                        }
                        is DebugNavKey -> NavEntry(key) {
                            DebugListScreen(
                                viewModel = hiltViewModel<DebugViewModel>(),
                                onOpenDetail = { potholeId ->
                                    backStack.add(DebugDetailNavKey(potholeId))
                                }
                            )
                        }
                        is DebugDetailNavKey -> NavEntry(key) {
                            DebugDetailScreen(
                                viewModel = hiltViewModel<DebugViewModel>(),
                                potholeId = key.potholeId,
                                onBack = { backStack.removeLastOrNull() }
                            )
                        }
                        else -> NavEntry(key) {
                            // Fallback
                        }
                    }
                }
            )
        }
    }
}
