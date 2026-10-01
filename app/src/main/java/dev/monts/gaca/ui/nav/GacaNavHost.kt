package dev.monts.gaca.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.monts.gaca.di.AppContainer
import dev.monts.gaca.ui.auth.LoginScreen
import dev.monts.gaca.ui.auth.LoginViewModel
import dev.monts.gaca.ui.chat.ChatListScreen
import dev.monts.gaca.ui.chat.ChatListViewModel
import dev.monts.gaca.ui.chatthread.ChatThreadScreen
import dev.monts.gaca.ui.chatthread.ChatThreadViewModel
import dev.monts.gaca.ui.crewdetail.CrewDetailScreen
import dev.monts.gaca.ui.crewdetail.CrewDetailViewModel
import dev.monts.gaca.ui.daily.DailyScreen
import dev.monts.gaca.ui.daily.DailyViewModel
import dev.monts.gaca.ui.detail.AlertDetailScreen
import dev.monts.gaca.ui.detail.AlertDetailViewModel
import dev.monts.gaca.ui.inbox.InboxScreen
import dev.monts.gaca.ui.inbox.InboxViewModel
import dev.monts.gaca.ui.newdelegation.NewDelegationScreen
import dev.monts.gaca.ui.newdelegation.NewDelegationViewModel
import dev.monts.gaca.ui.settings.SettingsScreen
import dev.monts.gaca.ui.settings.SettingsViewModel
import dev.monts.gaca.ui.skills.SkillsScreen
import dev.monts.gaca.ui.skills.SkillsViewModel
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.TextSecondary

private const val ROUTE_LOGIN = "login"
private const val ROUTE_DAILY = "daily"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_CHAT_LIST = "chat"
private const val ROUTE_SKILLS = "skills"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CREW_DETAIL = "crew/{name}"
private const val ROUTE_DETAIL = "detail/{alertId}"
private const val ROUTE_NEW_DELEGATION = "new-delegation"
private const val ROUTE_CHAT_THREAD = "chat-thread/{sessionId}?prefill={prefill}"

/** The three daily destinations — Settings stays a top-corner icon everywhere
 * else, it's not one of these (per BUILD_NEXT.md Slice 4). */
private data class BottomNavTab(val route: String, val label: String, val icon: ImageVector)

private val bottomNavTabs = listOf(
    BottomNavTab(ROUTE_DAILY, "Today", Icons.Filled.WbSunny),
    BottomNavTab(ROUTE_INBOX, "Inbox", Icons.Filled.Inbox),
    BottomNavTab(ROUTE_CHAT_LIST, "Chat", Icons.AutoMirrored.Filled.Chat),
    BottomNavTab(ROUTE_SKILLS, "Skills", Icons.Filled.AutoAwesome),
)

@Composable
fun GacaNavHost(
    container: AppContainer,
    pendingAlertId: String? = null,
    onPendingAlertConsumed: () -> Unit = {},
    pendingSessionId: String? = null,
    onPendingSessionConsumed: () -> Unit = {},
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

    // Same idea for a chat-reply notification: open that thread once we're signed in.
    LaunchedEffect(pendingSessionId, authState.user) {
        if (pendingSessionId != null && authState.user != null) {
            navController.navigate("chat-thread/$pendingSessionId")
            onPendingSessionConsumed()
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

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
        composable(ROUTE_DAILY) {
            val vm = viewModel<DailyViewModel>(
                factory = viewModelFactory { initializer { DailyViewModel(container.briefingRepository) } },
            )
            TabbedScreen(navController, currentRoute) {
                DailyScreen(viewModel = vm, onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
            }
        }
        composable(ROUTE_INBOX) {
            val vm = viewModel<InboxViewModel>(
                factory = viewModelFactory {
                    initializer { InboxViewModel(container.alertsRepository, container.authRepository) }
                },
            )
            TabbedScreen(navController, currentRoute) {
                InboxScreen(
                    viewModel = vm,
                    onOpenAlert = { id -> navController.navigate("detail/$id") },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onNewDelegation = { navController.navigate(ROUTE_NEW_DELEGATION) },
                )
            }
        }
        composable(ROUTE_CHAT_LIST) {
            val vm = viewModel<ChatListViewModel>(
                factory = viewModelFactory {
                    initializer { ChatListViewModel(container.sessionsRepository, container.authRepository) }
                },
            )
            TabbedScreen(navController, currentRoute) {
                ChatListScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenThread = { sessionId -> navController.navigate("chat-thread/$sessionId") },
                )
            }
        }
        composable(ROUTE_SKILLS) {
            val vm = viewModel<SkillsViewModel>(
                factory = viewModelFactory {
                    initializer { SkillsViewModel(container.authRepository, container.skillsRepository, container.sessionsRepository) }
                },
            )
            TabbedScreen(navController, currentRoute) {
                SkillsScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenThread = { sessionId, prefill ->
                        navController.navigate("chat-thread/$sessionId?prefill=${android.net.Uri.encode(prefill)}")
                    },
                )
            }
        }
        composable(
            ROUTE_CHAT_THREAD,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("prefill") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val prefill = backStackEntry.arguments?.getString("prefill")
            val vm = viewModel<ChatThreadViewModel>(
                factory = viewModelFactory {
                    initializer {
                        ChatThreadViewModel(
                            sessionId,
                            container.sessionsRepository,
                            container.authRepository,
                            container.alertsRepository,
                            initialMessageText = prefill,
                        )
                    }
                },
            )
            ChatThreadScreen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenAlert = { alertId -> navController.navigate("detail/$alertId") },
                onOpenCrew = { name -> navController.navigate("crew/${android.net.Uri.encode(name)}") },
            )
        }
        composable(
            ROUTE_CREW_DETAIL,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            val crewName = backStackEntry.arguments?.getString("name").orEmpty()
            val vm = viewModel<CrewDetailViewModel>(
                factory = viewModelFactory {
                    initializer { CrewDetailViewModel(crewName, container.crewProfileRepository, container.sessionsRepository) }
                },
            )
            CrewDetailScreen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenThread = { sessionId, prefill ->
                    navController.navigate("chat-thread/$sessionId?prefill=${android.net.Uri.encode(prefill)}")
                },
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
            AlertDetailScreen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { sessionId -> navController.navigate("chat-thread/$sessionId") },
            )
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
            SettingsScreen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenCrew = { name -> navController.navigate("crew/${android.net.Uri.encode(name)}") },
            )
        }
    }
}

/** Wraps one of the three daily destinations with the shared bottom nav bar —
 * Settings, the alert detail drill-down, new-delegation, and the chat thread
 * all render full-screen instead, same as the design canvas shows them. */
@Composable
private fun TabbedScreen(navController: NavHostController, currentRoute: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        GacaBottomNavBar(navController, currentRoute)
    }
}

@Composable
private fun GacaBottomNavBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = OnAccent) {
        bottomNavTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HeaderAccent,
                    selectedTextColor = HeaderAccent,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = ChipBackground,
                ),
            )
        }
    }
}
