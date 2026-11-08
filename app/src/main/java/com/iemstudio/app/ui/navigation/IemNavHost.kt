package com.iemstudio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iemstudio.app.IemController
import com.iemstudio.app.ui.screens.AudioOutputScreen
import com.iemstudio.app.ui.screens.EqStudioScreen
import com.iemstudio.app.ui.screens.HomeScreen
import com.iemstudio.app.ui.screens.SettingsScreen
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.SurfaceLow
import com.iemstudio.app.ui.theme.TextSecondary

enum class IemDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Outlined.Home),
    Eq("eq", "EQ", Icons.Outlined.GraphicEq),
    Output("output", "Output", Icons.Outlined.Tune),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun IemApp(
    controller: IemController,
    onRequestBtPermissions: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = SurfaceLow) {
                IemDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Amber,
                            selectedTextColor = Amber,
                            indicatorColor = Amber.copy(alpha = 0.16f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = IemDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(IemDestination.Home.route) {
                HomeScreen(controller, onRequestBtPermissions)
            }
            composable(IemDestination.Eq.route) {
                EqStudioScreen(controller)
            }
            composable(IemDestination.Output.route) {
                AudioOutputScreen(controller)
            }
            composable(IemDestination.Settings.route) {
                SettingsScreen(controller)
            }
        }
    }
}
