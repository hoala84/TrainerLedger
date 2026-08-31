package com.example.trainerledger.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trainerledger.domain.model.ClientRow
import com.example.trainerledger.domain.model.ClientSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onClientClick: (Long) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    viewModel: ClientsViewModel = viewModel(),
) {
    val clients by viewModel.clients.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клиенты") },
                actions = {
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Сортировка")
                    }
                    DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                        DropdownMenuItem(
                            text = { Text("По дате изменений") },
                            onClick = {
                                viewModel.setSort(ClientSortOrder.BY_UPDATED)
                                showSort = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("По алфавиту") },
                            onClick = {
                                viewModel.setSort(ClientSortOrder.ALPHABETICAL)
                                showSort = false
                            },
                        )
                    }
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
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить клиента")
            }
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
                    Text(
                        text = if (sort == ClientSortOrder.BY_UPDATED) {
                            "Сортировка: по дате изменений"
                        } else {
                            "Сортировка: фамилия, имя"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                items(clients, key = { it.client.id }) { row ->
                    ClientCard(row = row, onClick = { onClientClick(row.client.id) })
                }
            }
        }
    }

    if (showAdd) {
        ClientNameDialog(
            title = "Новый клиент",
            initialLastName = "",
            initialFirstName = "",
            onDismiss = { showAdd = false },
            onConfirm = { last, first ->
                viewModel.addClient(last, first)
                showAdd = false
            },
        )
    }
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

@Composable
fun ClientNameDialog(
    title: String,
    initialLastName: String,
    initialFirstName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var lastName by remember { mutableStateOf(initialLastName) }
    var firstName by remember { mutableStateOf(initialFirstName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Фамилия") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = lastName.isNotBlank() && firstName.isNotBlank(),
                onClick = { onConfirm(lastName, firstName) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
