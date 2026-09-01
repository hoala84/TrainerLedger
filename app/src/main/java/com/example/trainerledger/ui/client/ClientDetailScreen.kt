package com.example.trainerledger.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType
import com.example.trainerledger.ui.clients.ClientNameDialog
import com.example.trainerledger.ui.components.ConfirmDialog
import com.example.trainerledger.ui.components.DateField
import com.example.trainerledger.util.DateUtils
import com.example.trainerledger.util.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    onBack: () -> Unit,
    viewModel: ClientDetailViewModel = viewModel(),
) {
    val client by viewModel.client.collectAsState()
    val remaining by viewModel.remaining.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val workouts by viewModel.workouts.collectAsState()

    var menu by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var deleteClient by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var paymentEditor by remember { mutableStateOf<Payment?>(null) }
    var showWorkout by remember { mutableStateOf(false) }
    var workoutEditor by remember { mutableStateOf<Workout?>(null) }
    var pendingDeletePayment by remember { mutableStateOf<Payment?>(null) }
    var pendingDeleteWorkout by remember { mutableStateOf<Workout?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(client?.displayName ?: "Клиент")
                        Text(
                            "Осталось тренировок: $remaining",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Изменить имя") },
                            onClick = {
                                menu = false
                                editName = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить клиента") },
                            onClick = {
                                menu = false
                                deleteClient = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        paymentEditor = null
                        showPayment = true
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Оплата") }
                OutlinedButton(
                    onClick = {
                        workoutEditor = null
                        showWorkout = true
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Тренировка") }
            }

            Text("Оплаты", style = MaterialTheme.typography.titleMedium)
            if (payments.isEmpty()) {
                Text("Пока нет оплат", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            payments.forEach { payment ->
                LedgerItemCard(
                    title = MoneyUtils.format(payment.amount),
                    subtitle = "${DateUtils.formatDisplay(payment.date)} · ${paymentWorkoutsLabel(payment.workoutCount)}" +
                        if (payment.autoWorkoutId != null) " · авто-тренировка" else "",
                    onEdit = {
                        paymentEditor = payment
                        showPayment = true
                    },
                    onDelete = { pendingDeletePayment = payment },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Тренировки", style = MaterialTheme.typography.titleMedium)
            if (workouts.isEmpty()) {
                Text("Пока нет тренировок", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            workouts.forEach { workout ->
                LedgerItemCard(
                    title = DateUtils.formatDisplay(workout.date),
                    subtitle = workout.comment.ifBlank { "Без комментария" },
                    chip = workout.type,
                    onEdit = {
                        workoutEditor = workout
                        showWorkout = true
                    },
                    onDelete = { pendingDeleteWorkout = workout },
                )
            }
        }
    }

    if (editName && client != null) {
        ClientNameDialog(
            title = "Имя клиента",
            initialLastName = client!!.lastName,
            initialFirstName = client!!.firstName,
            onDismiss = { editName = false },
            onConfirm = { last, first ->
                viewModel.updateName(last, first)
                editName = false
            },
        )
    }
    if (deleteClient) {
        ConfirmDialog(
            title = "Удалить клиента?",
            text = "Будут удалены все оплаты и тренировки этого клиента.",
            onConfirm = {
                deleteClient = false
                viewModel.deleteClient(onBack)
            },
            onDismiss = { deleteClient = false },
        )
    }
    if (showPayment) {
        PaymentDialog(
            existing = paymentEditor,
            initialCount = paymentEditor?.workoutCount
                ?: if (workouts.any { it.type == WorkoutType.DEBT }) 0 else 1,
            onDismiss = { showPayment = false },
            onSave = { date, amount, count ->
                viewModel.savePayment(paymentEditor, date, amount, count)
                showPayment = false
            },
        )
    }
    if (showWorkout) {
        WorkoutDialog(
            existing = workoutEditor,
            onDismiss = { showWorkout = false },
            onSave = { date, comment, type ->
                viewModel.saveWorkout(workoutEditor, date, comment, type)
                showWorkout = false
            },
        )
    }
    pendingDeletePayment?.let { payment ->
        ConfirmDialog(
            title = "Удалить оплату?",
            text = "Если занятие было создано автоматически, оно тоже удалится.",
            onConfirm = {
                viewModel.deletePayment(payment)
                pendingDeletePayment = null
            },
            onDismiss = { pendingDeletePayment = null },
        )
    }
    pendingDeleteWorkout?.let { workout ->
        ConfirmDialog(
            title = "Удалить тренировку?",
            text = "Запись о занятии будет удалена. Остаток оплаченных тренировок пересчитается.",
            onConfirm = {
                viewModel.deleteWorkout(workout)
                pendingDeleteWorkout = null
            },
            onDismiss = { pendingDeleteWorkout = null },
        )
    }
}

@Composable
private fun LedgerItemCard(
    title: String,
    subtitle: String,
    chip: WorkoutType? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (chip != null) {
                        AssistChip(onClick = {}, label = { Text(workoutTypeLabel(chip)) })
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Изменить")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    existing: com.example.trainerledger.domain.model.Payment?,
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Long, Double, Int) -> Unit,
) {
    var date by remember { mutableLongStateOf(existing?.date ?: DateUtils.startOfDay()) }
    var amount by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var count by remember { mutableStateOf(initialCount.toString()) }
    val amountValue = MoneyUtils.parse(amount)
    val countValue = count.toIntOrNull()
    val valid = amountValue != null && amountValue >= 0 && countValue != null && countValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая оплата" else "Оплата") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "Дата", dateMillis = date, onDateChange = { date = it })
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сумма") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it.filter(Char::isDigit) },
                    label = { Text("Количество тренировок") },
                    supportingText = { Text("Можно указать 0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    paymentCountHint(countValue),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(date, amountValue!!, countValue!!) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun WorkoutDialog(
    existing: Workout?,
    onDismiss: () -> Unit,
    onSave: (Long, String, WorkoutType) -> Unit,
) {
    var date by remember { mutableLongStateOf(existing?.date ?: DateUtils.startOfDay()) }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: WorkoutType.PAID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая тренировка" else "Тренировка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "Дата", dateMillis = date, onDateChange = { date = it })
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Тип занятия", style = MaterialTheme.typography.labelLarge)
                WorkoutType.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = type == option,
                                onClick = { type = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == option, onClick = { type = option })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(workoutTypeLabel(option))
                            Text(workoutTypeHint(option), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, comment, type) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun paymentWorkoutsLabel(count: Int): String =
    if (count == 0) "без начисления занятий" else "$count тр."

private fun paymentCountHint(count: Int?): String = when {
    count == null -> "Укажите число занятий. Можно 0."
    count == 0 -> "Только сумма, новое занятие не создаётся. Так закрывают оплату за тренировку, которая уже была внесена в долг."
    count == 1 -> "Оплата за одну тренировку: занятие добавится автоматически на дату оплаты."
    else -> "Указанное число занятий начислится в пакет (остаток). Новая тренировка сама не появится."
}

private fun workoutTypeLabel(type: WorkoutType): String = when (type) {
    WorkoutType.PAID -> "Оплаченная"
    WorkoutType.DEBT -> "В долг"
    WorkoutType.GIFT -> "Подарочная"
}

private fun workoutTypeHint(type: WorkoutType): String = when (type) {
    WorkoutType.PAID -> "Списывается с оплаченного пакета"
    WorkoutType.DEBT -> "Проведена в долг, пакет не списывается"
    WorkoutType.GIFT -> "Бесплатная, в статистике отдельно"
}
