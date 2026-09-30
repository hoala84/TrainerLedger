package com.example.trainerledger.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import com.example.trainerledger.ui.components.ClientDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trainerledger.domain.model.ClientRow
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.ui.components.PaymentDialog
import com.example.trainerledger.ui.components.WorkoutDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class QuickAction { PAYMENT, WORKOUT }
private enum class ClientListSort { NAME, WORKOUTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onClientClick: (Long) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    viewModel: ClientsViewModel = viewModel(),
) {
    val clients by viewModel.clients.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var clientSort by remember { mutableStateOf(ClientListSort.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var showAlphabetSort by remember { mutableStateOf(false) }
    var remainingRange by remember { mutableStateOf(0f..10f) }
    var choosing by remember { mutableStateOf<QuickAction?>(null) }
    var editing by remember { mutableStateOf<QuickAction?>(null) }
    var selected by remember { mutableStateOf<ClientRow?>(null) }
    var workouts by remember { mutableStateOf(emptyList<Workout>()) }
    val sortedClients = remember(clients, query, clientSort, ascending, remainingRange) {
        val needle = query.trim().lowercase()
        val remainingFrom = remainingRange.start.roundToInt()
        val remainingTo = remainingRange.endInclusive.roundToInt()
        val filtered = clients.filter {
            (needle.isBlank() || it.client.displayName.lowercase().contains(needle)) &&
                (clientSort != ClientListSort.WORKOUTS || it.remainingWorkouts in remainingFrom..remainingTo)
        }
        val comparator = when (clientSort) {
            ClientListSort.NAME -> compareBy<ClientRow> { it.client.displayName.lowercase() }
            ClientListSort.WORKOUTS -> compareBy { it.remainingWorkouts }
        }
        if (ascending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клиенты") },
                actions = {
                    IconButton(onClick = onExportBackup) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Сохранить копию")
                    }
                    IconButton(onClick = onImportBackup) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "Восстановить")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Добавить клиента") },
            )
        },
    ) { padding ->
        if (clients.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Пока нет клиентов", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Добавьте первого клиента кнопкой «+»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { choosing = QuickAction.PAYMENT },
                            enabled = clients.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Добавить оплату") }
                        OutlinedButton(
                            onClick = { choosing = QuickAction.WORKOUT },
                            enabled = clients.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Добавить тренировку") }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск по фамилии и имени") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            FilterChip(
                                selected = clientSort == ClientListSort.NAME,
                                onClick = { showAlphabetSort = true },
                                label = { Text("Алфавит: " + if (ascending) "А–Я" else "Я–А") },
                            )
                            DropdownMenu(
                                expanded = showAlphabetSort,
                                onDismissRequest = { showAlphabetSort = false },
                            ) {
                                DropdownMenuItem(text = { Text("От А до Я") }, onClick = {
                                    clientSort = ClientListSort.NAME
                                    ascending = true
                                    showAlphabetSort = false
                                })
                                DropdownMenuItem(text = { Text("От Я до А") }, onClick = {
                                    clientSort = ClientListSort.NAME
                                    ascending = false
                                    showAlphabetSort = false
                                })
                            }
                        }
                        Box {
                            FilterChip(
                                selected = clientSort == ClientListSort.WORKOUTS,
                                onClick = {
                                    clientSort = ClientListSort.WORKOUTS
                                    ascending = true
                                },
                                label = {
                                    Text(
                                        "Тренировки: ${remainingRange.start.roundToInt()}–" +
                                            remainingRange.endInclusive.roundToInt(),
                                    )
                                },
                            )
                        }
                    }
                }
                if (clientSort == ClientListSort.WORKOUTS) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Осталось тренировок: от ${remainingRange.start.roundToInt()} " +
                                    "до ${remainingRange.endInclusive.roundToInt()}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            RangeSlider(
                                value = remainingRange,
                                onValueChange = { remainingRange = it },
                                valueRange = 0f..10f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (sortedClients.isEmpty()) {
                    item { Text("Клиенты не найдены", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                }
                items(sortedClients, key = { it.client.id }) { row ->
                    ClientCard(row = row, onClick = { onClientClick(row.client.id) })
                }
            }
        }
    }

    if (showAdd) {
        ClientDialog(
            title = "Новый клиент",
            onDismiss = { showAdd = false },
            onSave = viewModel::saveClient,
        )
    }

    choosing?.let { action ->
        ClientPickerDialog(clients, { choosing = null }) { row ->
            selected = row
            choosing = null
            scope.launch {
                workouts = viewModel.workouts(row.client.id)
                editing = action
            }
        }
    }
    if (editing == QuickAction.WORKOUT && selected != null) {
        WorkoutDialog(null, { editing = null }) { date, comment, type ->
            viewModel.addWorkout(selected!!.client.id, date, comment, type)
            editing = null
        }
    }
    if (editing == QuickAction.PAYMENT && selected != null) {
        PaymentDialog(null, workouts, { editing = null }) { date, amount, count, debt ->
            viewModel.addPayment(selected!!.client.id, date, amount, count, debt)
        }
    }
}

@Composable
private fun ClientPickerDialog(
    clients: List<ClientRow>,
    onDismiss: () -> Unit,
    onSelect: (ClientRow) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(clients, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) clients else clients.filter { it.client.displayName.lowercase().contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите клиента") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Фамилия или имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.client.id }) { row ->
                        Text(
                            row.client.displayName,
                            Modifier.fillMaxWidth().clickable { onSelect(row) }.padding(vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ClientCard(row: ClientRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.client.displayName, style = MaterialTheme.typography.titleMedium)
                if (row.debtWorkouts > 0) {
                    Text(
                        "В долг: ${row.debtWorkouts}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            AssistChip(
                onClick = onClick,
                label = { Text("осталось ${row.remainingWorkouts}") },
            )
        }
    }
}
