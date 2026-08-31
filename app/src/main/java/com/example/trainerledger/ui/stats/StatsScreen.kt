package com.example.trainerledger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trainerledger.ui.components.DateRangePickerDialog
import com.example.trainerledger.util.DateUtils
import com.example.trainerledger.util.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onExportExcel: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val filter by viewModel.filter.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val clients by viewModel.clients.collectAsState()
    var showRange by remember { mutableStateOf(false) }
    var showClients by remember { mutableStateOf(false) }

    val selectedName = clients.firstOrNull { it.id == filter.clientId }?.displayName ?: "Все клиенты"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                actions = {
                    IconButton(onClick = onExportExcel) {
                        Icon(Icons.Outlined.GridOn, contentDescription = "Выгрузить в Excel")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = true,
                        onClick = { showRange = true },
                        label = {
                            Text("${DateUtils.formatShort(filter.from)} — ${DateUtils.formatShort(filter.to)}")
                        },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    )
                    FilterChip(
                        selected = filter.clientId != null,
                        onClick = { showClients = true },
                        label = { Text(selectedName) },
                    )
                    DropdownMenu(expanded = showClients, onDismissRequest = { showClients = false }) {
                        DropdownMenuItem(
                            text = { Text("Все клиенты") },
                            onClick = {
                                viewModel.setClient(null)
                                showClients = false
                            },
                        )
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.displayName) },
                                onClick = {
                                    viewModel.setClient(client.id)
                                    showClients = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                val current = stats
                if (current == null) {
                    Text("Загрузка…")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Проведено тренировок", current.totalWorkouts.toString())
                        StatCard("Из них подарочных", current.giftWorkouts.toString())
                        StatCard("Из них в долг", current.debtWorkouts.toString())
                        StatCard("Приход денег", MoneyUtils.format(current.totalIncome))
                    }
                }
            }
            if (filter.clientId == null) {
                item {
                    Text("По клиентам", style = MaterialTheme.typography.titleMedium)
                }
                items(stats?.perClient.orEmpty(), key = { it.client.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(row.client.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("Тренировки: ${row.completedWorkouts} (подарки: ${row.giftWorkouts}, долг: ${row.debtWorkouts})")
                            Text("Приход: ${MoneyUtils.format(row.income)}")
                        }
                    }
                }
            }
            item {
                TextButton(onClick = onExportExcel, modifier = Modifier.fillMaxWidth()) {
                    Text("Выгрузить период в Excel")
                }
            }
        }
    }

    if (showRange) {
        DateRangePickerDialog(
            from = filter.from,
            to = filter.to,
            onDismiss = { showRange = false },
            onConfirm = { from, to ->
                viewModel.setPeriod(from, to)
                showRange = false
            },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
