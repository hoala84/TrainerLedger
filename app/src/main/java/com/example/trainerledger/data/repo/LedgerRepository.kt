package com.example.trainerledger.data.repo

import androidx.room.withTransaction
import com.example.trainerledger.data.backup.BackupFile
import com.example.trainerledger.data.backup.ClientBackup
import com.example.trainerledger.data.backup.PaymentBackup
import com.example.trainerledger.data.backup.WorkoutBackup
import com.example.trainerledger.data.db.AppDatabase
import com.example.trainerledger.data.entity.ClientEntity
import com.example.trainerledger.data.entity.PaymentEntity
import com.example.trainerledger.data.entity.WorkoutEntity
import com.example.trainerledger.data.mapper.EntityMappers.toDomain
import com.example.trainerledger.data.mapper.EntityMappers.toEntity
import com.example.trainerledger.domain.model.Client
import com.example.trainerledger.domain.model.ClientPeriodStats
import com.example.trainerledger.domain.model.ClientRow
import com.example.trainerledger.domain.model.ClientSortOrder
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.PeriodStats
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType
import com.example.trainerledger.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

class LedgerRepository(private val db: AppDatabase) {
    private val clients = db.clientDao()
    private val payments = db.paymentDao()
    private val workouts = db.workoutDao()

    fun observeClientRows(sort: Flow<ClientSortOrder>): Flow<List<ClientRow>> {
        return combine(
            clients.observeAll(),
            payments.observeAll(),
            workouts.observeAll(),
            sort,
        ) { clientList, paymentList, workoutList, order ->
            val rows = clientList.map { entity ->
                val clientPayments = paymentList.filter { it.clientId == entity.id }
                val clientWorkouts = workoutList.filter { it.clientId == entity.id }
                ClientRow(
                    client = entity.toDomain(),
                    remainingWorkouts = remaining(clientPayments, clientWorkouts),
                    debtWorkouts = clientWorkouts.count { it.type == WorkoutType.DEBT.name && it.settledByPaymentId == null },
                )
            }
            when (order) {
                ClientSortOrder.BY_UPDATED -> rows.sortedByDescending { it.client.updatedAt }
                ClientSortOrder.ALPHABETICAL -> rows.sortedWith(
                    compareBy(
                        { it.client.lastName.lowercase(Locale.getDefault()) },
                        { it.client.firstName.lowercase(Locale.getDefault()) },
                    ),
                )
            }
        }
    }

    fun observeClient(id: Long): Flow<Client?> = clients.observeById(id).map { it?.toDomain() }

    fun observePayments(clientId: Long): Flow<List<Payment>> =
        payments.observeByClient(clientId).map { list ->
            list.map { it.toDomain() }.sortedWith(compareByDescending<Payment> { it.date }.thenByDescending { it.id })
        }

    fun observeWorkouts(clientId: Long): Flow<List<Workout>> =
        workouts.observeByClient(clientId).map { list ->
            list.map { it.toDomain() }.sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
        }

    fun observeDataRevision(): Flow<Unit> = combine(
        clients.observeAll(),
        payments.observeAll(),
        workouts.observeAll(),
    ) { _, _, _ -> }

    fun observeRemaining(clientId: Long): Flow<Int> = combine(
        payments.observeByClient(clientId),
        workouts.observeByClient(clientId),
    ) { paymentList, workoutList -> remaining(paymentList, workoutList) }

    suspend fun saveClient(client: Client) {
        val cleaned = client.copy(
            lastName = client.lastName.trim().replace(Regex("\\s+"), " "),
            firstName = client.firstName.trim().replace(Regex("\\s+"), " "),
            comment = client.comment.trim(), phone = client.phone.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        require(cleaned.lastName.isNotBlank() && cleaned.firstName.isNotBlank()) { "Укажите имя и фамилию" }
        require(cleaned.birthDate == null || com.example.trainerledger.util.ClientDetails.isValidBirthDate(cleaned.birthDate)) {
            "Укажите корректную дату рождения, не позднее сегодняшней"
        }
        db.withTransaction {
            val existing = clients.getAll()
            val original = existing.find { it.id == cleaned.id }
            val key = com.example.trainerledger.util.ClientDetails.nameKey(cleaned.lastName, cleaned.firstName)
            val nameChanged = original == null ||
                com.example.trainerledger.util.ClientDetails.nameKey(original.lastName, original.firstName) != key
            require(!nameChanged || existing.none {
                it.id != cleaned.id && com.example.trainerledger.util.ClientDetails.nameKey(it.lastName, it.firstName) == key
            }) { "Клиент с таким именем и фамилией уже существует" }
            if (cleaned.id == 0L) clients.insert(cleaned.toEntity()) else clients.update(cleaned.toEntity())
        }
    }

    suspend fun deleteClient(client: Client) {
        clients.delete(client.toEntity())
    }

    suspend fun savePayment(payment: Payment, debtCount: Int) {
        require(payment.amount.isFinite() && payment.amount >= 0) { "Укажите корректную сумму" }
        db.withTransaction {
            val existing = if (payment.id == 0L) null else
                requireNotNull(payments.getById(payment.id)) { "Оплата уже удалена" }
            require(existing == null || existing.clientId == payment.clientId) { "Нельзя перенести оплату другому клиенту" }
            val all = workouts.getAll().filter { it.clientId == payment.clientId }
            val selected = com.example.trainerledger.domain.model.DebtAccounting.selectDebts(
                all.map { it.toDomain() }, payment.id, payment.workoutCount, debtCount,
            ).toSet()
            val day = DateUtils.startOfDay(payment.date)
            var saved = payment.copy(date = day, autoWorkoutId = existing?.autoWorkoutId).toEntity()
            if (existing == null) saved = saved.copy(id = payments.insert(saved))
            all.forEach { workout ->
                val linked = when {
                    workout.id in selected -> saved.id
                    workout.settledByPaymentId == saved.id -> null
                    else -> workout.settledByPaymentId
                }
                if (linked != workout.settledByPaymentId) workouts.update(workout.copy(settledByPaymentId = linked))
            }
            var autoId = saved.autoWorkoutId?.takeIf { id ->
                all.any { it.id == id && it.type == WorkoutType.PAID.name }
            }
            if (payment.workoutCount == 1 && debtCount == 0) {
                val auto = autoId?.let { workouts.getById(it) }
                if (auto != null) workouts.update(auto.copy(date = day))
                else if (existing == null || existing.workoutCount != 1 ||
                    all.any { it.settledByPaymentId == existing.id }) {
                    autoId = workouts.insert(WorkoutEntity(clientId = payment.clientId, date = day,
                        comment = "По оплате", type = WorkoutType.PAID.name))
                }
            } else if (autoId != null) {
                workouts.deleteById(autoId)
                autoId = null
            }
            payments.update(saved.copy(autoWorkoutId = autoId))
            clients.touch(payment.clientId, System.currentTimeMillis())
        }
    }

    suspend fun deletePayment(payment: Payment) {
        db.withTransaction {
            val current = payments.getById(payment.id) ?: return@withTransaction
            workouts.getAll().filter { it.settledByPaymentId == current.id }.forEach {
                workouts.update(it.copy(settledByPaymentId = null))
            }
            current.autoWorkoutId?.let { id ->
                if (workouts.getById(id)?.type == WorkoutType.PAID.name) workouts.deleteById(id)
            }
            payments.delete(current)
            clients.touch(current.clientId, System.currentTimeMillis())
        }
    }

    suspend fun addWorkout(clientId: Long, date: Long, comment: String, type: WorkoutType) {
        workouts.insert(
            WorkoutEntity(
                clientId = clientId,
                date = DateUtils.startOfDay(date),
                comment = comment.trim(),
                type = type.name,
            ),
        )
        clients.touch(clientId, System.currentTimeMillis())
    }

    suspend fun updateWorkout(workout: Workout) {
        db.withTransaction {
            val current = workouts.getById(workout.id) ?: return@withTransaction
            if (current.type != workout.type.name) payments.clearAutoWorkout(workout.id)
            workouts.update(workout.copy(date = DateUtils.startOfDay(workout.date), comment = workout.comment.trim(),
                settledByPaymentId = if (workout.type == WorkoutType.DEBT) current.settledByPaymentId else null).toEntity())
            clients.touch(workout.clientId, System.currentTimeMillis())
        }
    }

    suspend fun deleteWorkout(workout: Workout) {
        db.withTransaction {
            payments.clearAutoWorkout(workout.id)
            workouts.delete(workout.toEntity())
            clients.touch(workout.clientId, System.currentTimeMillis())
        }
    }

    suspend fun periodStats(from: Long, to: Long, clientId: Long?): PeriodStats {
        val start = DateUtils.startOfDay(from)
        val end = DateUtils.endOfDay(to)
        val allClients = clients.getAll().map { it.toDomain() }
            .sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
        val periodPayments = payments.getInPeriod(start, end).map { it.toDomain() }
        val periodWorkouts = workouts.getInPeriod(start, end).map { it.toDomain() }
        val scopedClients = if (clientId == null) allClients else allClients.filter { it.id == clientId }
        val perClient = scopedClients.mapNotNull { client ->
            val cPayments = periodPayments.filter { it.clientId == client.id }
            val cWorkouts = periodWorkouts.filter { it.clientId == client.id }
            if (cPayments.isEmpty() && cWorkouts.isEmpty()) return@mapNotNull null
            ClientPeriodStats(
                client = client,
                completedWorkouts = cWorkouts.size,
                giftWorkouts = cWorkouts.count { it.type == WorkoutType.GIFT },
                debtWorkouts = cWorkouts.count { it.type == WorkoutType.DEBT && it.settledByPaymentId == null },
                income = cPayments.sumOf { it.amount },
            )
        }
        return PeriodStats(
            totalWorkouts = perClient.sumOf { it.completedWorkouts },
            giftWorkouts = perClient.sumOf { it.giftWorkouts },
            debtWorkouts = perClient.sumOf { it.debtWorkouts },
            totalIncome = perClient.sumOf { it.income },
            perClient = perClient,
        )
    }

    suspend fun exportSnapshot(): BackupFile = db.withTransaction {
        BackupFile(
            exportedAt = System.currentTimeMillis(),
            clients = clients.getAll().map {
                ClientBackup(it.id, it.lastName, it.firstName, it.updatedAt, it.comment, it.birthDate, it.phone)
            },
            payments = payments.getAll().map {
                PaymentBackup(it.id, it.clientId, it.date, it.amount, it.workoutCount, it.autoWorkoutId)
            },
            workouts = workouts.getAll().map {
                WorkoutBackup(it.id, it.clientId, it.date, it.comment, it.type, it.settledByPaymentId)
            },
        )
    }

    suspend fun restore(backup: BackupFile) {
        val paymentMap = backup.payments.associateBy { it.id }
        backup.workouts.filter { it.settledByPaymentId != null }.forEach {
            val payment = paymentMap[it.settledByPaymentId]
            require(payment != null && payment.clientId == it.clientId && it.type == WorkoutType.DEBT.name) {
                "Некорректная связь погашения долга в резервной копии"
            }
        }
        backup.workouts.filter { it.settledByPaymentId != null }.groupBy { it.settledByPaymentId }.forEach { (id, debts) ->
            require(debts.size <= paymentMap.getValue(id!!).workoutCount) { "Число погашений превышает оплату" }
        }
        db.withTransaction {
            workouts.deleteAll()
            payments.deleteAll()
            clients.deleteAll()
            clients.upsertAll(
                backup.clients.map {
                    ClientEntity(it.id, it.lastName, it.firstName, it.updatedAt, it.comment, it.birthDate, it.phone)
                },
            )
            workouts.upsertAll(
                backup.workouts.map {
                    WorkoutEntity(it.id, it.clientId, it.date, it.comment, it.type, it.settledByPaymentId)
                },
            )
            payments.upsertAll(
                backup.payments.map {
                    PaymentEntity(it.id, it.clientId, it.date, it.amount, it.workoutCount, it.autoWorkoutId)
                },
            )
        }
    }

    suspend fun excelData(from: Long, to: Long): ExcelData {
        val start = DateUtils.startOfDay(from)
        val end = DateUtils.endOfDay(to)
        return ExcelData(
            clients = clients.getAll().map { it.toDomain() },
            payments = payments.getInPeriod(start, end).map { it.toDomain() },
            workouts = workouts.getInPeriod(start, end).map { it.toDomain() },
        )
    }

    private fun remaining(paymentList: List<PaymentEntity>, workoutList: List<WorkoutEntity>): Int {
        return com.example.trainerledger.domain.model.DebtAccounting.remaining(
            paymentList.map { it.toDomain() }, workoutList.map { it.toDomain() },
        )
    }
}

data class ExcelData(
    val clients: List<Client>,
    val payments: List<Payment>,
    val workouts: List<Workout>,
)
