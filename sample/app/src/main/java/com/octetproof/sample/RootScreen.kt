package com.octetproof.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * The app shell: bottom tabs (Generate · Verify · optional Device) over a
 * NavHost, with pushed routes for the proof detail, dev menu, and the Sensors
 * diagnostics screen. Mirrors the iOS RootView.
 */
@Composable
fun RootScreen(vm: SampleViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = route == "generate" || route == "verify" || route == "device"

    Scaffold(
        bottomBar = {
            if (topLevel) NavigationBar {
                NavigationBarItem(
                    selected = route == "generate",
                    onClick = { nav.selectTab("generate") },
                    icon = { Icon(Icons.Filled.LocationOn, null) },
                    label = { Text("Generate") },
                )
                NavigationBarItem(
                    selected = route == "verify",
                    onClick = { nav.selectTab("verify") },
                    icon = { Icon(Icons.Filled.CheckCircle, null) },
                    label = { Text("Verify") },
                )
                if (vm.settings.showDeviceTab) {
                    NavigationBarItem(
                        selected = route == "device",
                        onClick = { nav.selectTab("device") },
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = { Text("Device") },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "generate", modifier = Modifier.padding(pad)) {
            composable("generate") { GenerateScreen(vm, nav) }
            composable("verify") { VerifyScreen(vm, nav) }
            composable("device") { DeviceInfoScreen(vm) }
            composable("devSettings") { DevSettingsScreen(vm, nav) }
            composable("sensors") { SensorsScreen(nav) }
            composable("sensorMap") { SensorMapScreen(nav) }
            composable(
                "verifyDetail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                val id = backStack.arguments?.getString("id")
                val proof = vm.store.proofs.firstOrNull { it.id == id }
                if (proof != null) VerifyDetailScreen(vm, proof, nav)
                else Text("Proof not found", Modifier.padding(16.dp))
            }
        }
    }
}

/** Bottom-tab navigation — single-top + restore, so tabs don't stack. */
private fun NavController.selectTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
