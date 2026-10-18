package com.opendroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.chat.ChatScreen
import com.opendroid.ui.settings.AgentsScreen
import com.opendroid.ui.settings.PermissionsScreen
import com.opendroid.ui.settings.ProviderEditScreen
import com.opendroid.ui.settings.ProvidersScreen
import com.opendroid.ui.settings.SettingsScreen
import com.opendroid.ui.settings.ToolsScreen
import com.opendroid.ui.theme.OpenDroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenDroidTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val vm: OpenDroidViewModel = hiltViewModel()

    NavHost(navController = nav, startDestination = "chat") {
        composable("chat") {
            ChatScreen(vm, onOpenSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(vm, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
        }
        composable("providers") {
            ProvidersScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("provider/$it") })
        }
        composable("provider/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            ProviderEditScreen(vm, id, onBack = { nav.popBackStack() })
        }
        composable("agents") {
            AgentsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("permissions") {
            PermissionsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("tools") {
            ToolsScreen(onBack = { nav.popBackStack() })
        }
    }
}
