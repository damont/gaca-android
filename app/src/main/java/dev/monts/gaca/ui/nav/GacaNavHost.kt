package dev.monts.gaca.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.monts.gaca.di.AppContainer
import dev.monts.gaca.ui.auth.LoginScreen
import dev.monts.gaca.ui.auth.LoginViewModel
import dev.monts.gaca.ui.detail.AlertDetailScreen
import dev.monts.gaca.ui.detail.AlertDetailViewModel
import dev.monts.gaca.ui.inbox.InboxScreen
import dev.monts.gaca.ui.inbox.InboxViewModel
import dev.monts.gaca.ui.newdelegation.NewDelegationScreen
import dev.monts.gaca.ui.newdelegation.NewDelegationViewModel
import dev.monts.gaca.ui.settings.SettingsScreen
import dev.monts.gaca.ui.settings.SettingsViewModel
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.PageBackground

private const val ROUTE_LOGIN = "login"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_DETAIL = "detail/{alertId}"
private const val ROUTE_NEW_DELEGATION = "new-delegation"

@Composable
fun GacaNavHost(
    container: AppContainer,
    pendingAlertId: String? = null,
    onPendingAlertConsumed: () -> Unit = {},
) {
    val authState by container.authRepository.state.collectAsState()

    LaunchedEffect(Unit) { container.authRepository.restoreSession() }

    // Keep this device's FCM token current whenever a session becomes active —
    // both a fresh sign-in and an already-authenticated restore at app start.
    LaunchedEffect(authState.user != null) {
        if (authState.user != null) {
            container.pushRepository.registerCurrentToken()
        }
    }

    if (authState.loading) {
        Box(Modifier.fillMaxSize().background(PageBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HeaderAccent)
        }
        return
    }

    val navController = rememberNavController()
    var hasSeenUser by remember { mutableStateOf(authState.user != null) }

    LaunchedEffect(authState.user) {
        if (authState.user != null) {
            // Fresh sign-in while sitting on the login screen — hasSeenUser was
            // still false, so this is the transition into a session, not the
            // one restoreSession() already resolved before NavHost's
            // startDestination was picked.
            if (!hasSeenUser) {
                navController.navigate(ROUTE_INBOX) { popUpTo(0) { inclusive = true } }
            }
            hasSeenUser = true
        } else if (hasSeenUser) {
            hasSeenUser = false
            navController.navigate(ROUTE_LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    // A notification tap delivers the alert id here (from MainActivity's
    // intent extras, both the cold-start and already-running/singleTask
    // cases). Only act on it once we're actually signed in and past the
    // splash — jumping straight to a detail screen the moment it's safe to.
    LaunchedEffect(pendingAlertId, authState.user) {
        if (pendingAlertId != null && authState.user != null) {
            navController.navigate("detail/$pendingAlertId")
            onPendingAlertConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.user != null) ROUTE_INBOX else ROUTE_LOGIN,
    ) {
        composable(ROUTE_LOGIN) {
            val vm = viewModel<LoginViewModel>(
                factory = viewModelFactory { initializer { LoginViewModel(container.authRepository) } },
            )
            LoginScreen(vm)
        }
        composable(ROUTE_INBOX) {
            val vm = viewModel<InboxViewModel>(
                factory = viewModelFactory {
                    initializer { InboxViewModel(container.alertsRepository, container.authRepository) }
                },
            )
            InboxScreen(
                viewModel = vm,
                onOpenAlert = { id -> navController.navigate("detail/$id") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNewDelegation = { navController.navigate(ROUTE_NEW_DELEGATION) },
            )
        }
        composable(
            ROUTE_DETAIL,
            arguments = listOf(navArgument("alertId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getString("alertId").orEmpty()
            val vm = viewModel<AlertDetailViewModel>(
                factory = viewModelFactory {
                    initializer { AlertDetailViewModel(alertId, container.alertsRepository, container.authRepository) }
                },
            )
            AlertDetailScreen(vm, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_NEW_DELEGATION) {
            val vm = viewModel<NewDelegationViewModel>(
                factory = viewModelFactory {
                    initializer { NewDelegationViewModel(container.alertsRepository, container.authRepository) }
                },
            )
            NewDelegationScreen(vm, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            val vm = viewModel<SettingsViewModel>(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settingsStore, container.authRepository, container.pushRepository) }
                },
            )
            SettingsScreen(vm, onBack = { navController.popBackStack() })
        }
    }
}
