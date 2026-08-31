package com.example.trainerledger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trainerledger.ui.backup.BackupViewModel
import com.example.trainerledger.ui.client.ClientDetailScreen
import com.example.trainerledger.ui.clients.ClientsScreen
import com.example.trainerledger.ui.components.ConfirmDialog
import com.example.trainerledger.ui.stats.StatsScreen
import com.example.trainerledger.ui.stats.StatsViewModel
import com.example.trainerledger.util.DateUtils
import kotlinx.coroutines.launch

@Composable
fun TrainerLedgerApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == "clients" || route == "stats"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backupViewModel: BackupViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupViewModel.exportBackup(uri) { ok ->
            scope.launch {
                snackbarHostState.showSnackbar(if (ok) "Копия сохранена" else "Не удалось сохранить копию")
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        confirmRestore = uri
    }

    val exportExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        statsViewModel.exportExcel(uri) { ok ->
            scope.launch {
                snackbarHostState.showSnackbar(if (ok) "Excel сохранён" else "Не удалось сохранить Excel")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "clients",
                        onClick = { navController.navigateBottom("clients") },
                        icon = { Icon(Icons.Outlined.People, contentDescription = null) },
                        label = { Text("Клиенты") },
                    )
                    NavigationBarItem(
                        selected = route == "stats",
                        onClick = { navController.navigateBottom("stats") },
                        icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        label = { Text("Статистика") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "clients",
            modifier = Modifier.padding(padding),
        ) {
            composable("clients") {
                ClientsScreen(
                    onClientClick = { id -> navController.navigate("client/$id") },
                    onExportBackup = {
                        exportBackup.launch("trainer-ledger-${DateUtils.formatFileDate(System.currentTimeMillis())}.json")
                    },
                    onImportBackup = {
                        importBackup.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    },
                )
            }
            composable(
                route = "client/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.LongType }),
            ) {
                ClientDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("stats") {
                StatsScreen(
                    onExportExcel = {
                        val filter = statsViewModel.filter.value
                        val name = "trener-${DateUtils.formatFileDate(filter.from)}-${DateUtils.formatFileDate(filter.to)}.xlsx"
                        exportExcel.launch(name)
                    },
                    viewModel = statsViewModel,
                )
            }
        }
    }

    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = "Восстановить данные?",
            text = "Текущие записи будут заменены содержимым файла. Это нужно при переустановке или смене телефона.",
            confirmLabel = "Восстановить",
            onConfirm = {
                val restoreUri = uri
                confirmRestore = null
                backupViewModel.importBackup(restoreUri) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) "Данные восстановлены" else "Не удалось прочитать файл")
                    }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

private fun androidx.navigation.NavHostController.navigateBottom(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
