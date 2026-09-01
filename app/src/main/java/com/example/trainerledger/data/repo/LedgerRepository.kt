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
                    debtWorkouts = clientWorkouts.count { it.type == WorkoutType.DEBT.name },
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

    suspend fun addClient(lastName: String, firstName: String): Long {
        return clients.insert(
            ClientEntity(
                lastName = lastName.trim(),
                firstName = firstName.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateClient(client: Client) {
        clients.update(client.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteClient(client: Client) {
        clients.delete(client.toEntity())
    }

    suspend fun addPayment(clientId: Long, date: Long, amount: Double, workoutCount: Int) {
        val count = workoutCount.coerceAtLeast(0)
        db.withTransaction {
            val paymentId = payments.insert(
                PaymentEntity(
                    clientId = clientId,
                    date = DateUtils.startOfDay(date),
                    amount = amount,
                    workoutCount = count,
                ),
            )
            if (count == 1) {
                val workoutId = workouts.insert(
                    WorkoutEntity(
                        clientId = clientId,
                        date = DateUtils.startOfDay(date),
                        comment = "По оплате",
                        type = WorkoutType.PAID.name,
                    ),
                )
                payments.setAutoWorkoutId(paymentId, workoutId)
            }
            clients.touch(clientId, System.currentTimeMillis())
        }
    }

    suspend fun updatePayment(payment: Payment) {
        db.withTransaction {
            val day = DateUtils.startOfDay(payment.date)
            val count = payment.workoutCount.coerceAtLeast(0)
            val existing = payments.getById(payment.id) ?: return@withTransaction
            var autoId = existing.autoWorkoutId
            if (count == 1) {
                if (autoId != null) {
                    val auto = workouts.getById(autoId)
                    if (auto != null) {
                        workouts.update(auto.copy(date = day))
                    } else {
                        autoId = workouts.insert(
                            WorkoutEntity(
                                clientId = payment.clientId,
                                date = day,
                                comment = "По оплате",
                                type = WorkoutType.PAID.name,
                            ),
                        )
                    }
                } else {
                    autoId = workouts.insert(
                        WorkoutEntity(
                            clientId = payment.clientId,
                            date = day,
                            comment = "По оплате",
                            type = WorkoutType.PAID.name,
                        ),
                    )
                }
            } else if (autoId != null) {
                workouts.deleteById(autoId)
                autoId = null
            }
            payments.update(
                existing.copy(
                    date = day,
                    amount = payment.amount,
                    workoutCount = count,
                    autoWorkoutId = autoId,
                ),
            )
            clients.touch(payment.clientId, System.currentTimeMillis())
        }
    }

    suspend fun deletePayment(payment: Payment) {
        db.withTransaction {
            payment.autoWorkoutId?.let { workouts.deleteById(it) }
            payments.delete(payment.toEntity())
            clients.touch(payment.clientId, System.currentTimeMillis())
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
        workouts.update(workout.copy(date = DateUtils.startOfDay(workout.date), comment = workout.comment.trim()).toEntity())
        clients.touch(workout.clientId, System.currentTimeMillis())
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
        val perClient = scopedClients.map { client ->
            val cPayments = periodPayments.filter { it.clientId == client.id }
            val cWorkouts = periodWorkouts.filter { it.clientId == client.id }
            ClientPeriodStats(
                client = client,
                completedWorkouts = cWorkouts.size,
                giftWorkouts = cWorkouts.count { it.type == WorkoutType.GIFT },
                debtWorkouts = cWorkouts.count { it.type == WorkoutType.DEBT },
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

    suspend fun exportSnapshot(): BackupFile {
        return BackupFile(
            exportedAt = System.currentTimeMillis(),
            clients = clients.getAll().map {
                ClientBackup(it.id, it.lastName, it.firstName, it.updatedAt)
            },
            payments = payments.getAll().map {
                PaymentBackup(it.id, it.clientId, it.date, it.amount, it.workoutCount, it.autoWorkoutId)
            },
            workouts = workouts.getAll().map {
                WorkoutBackup(it.id, it.clientId, it.date, it.comment, it.type)
            },
        )
    }

    suspend fun restore(backup: BackupFile) {
        db.withTransaction {
            workouts.deleteAll()
            payments.deleteAll()
            clients.deleteAll()
            clients.upsertAll(
                backup.clients.map {
                    ClientEntity(it.id, it.lastName, it.firstName, it.updatedAt)
                },
            )
            workouts.upsertAll(
                backup.workouts.map {
                    WorkoutEntity(it.id, it.clientId, it.date, it.comment, it.type)
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
        val paid = paymentList.sumOf { it.workoutCount }
        val used = workoutList.count { it.type == WorkoutType.PAID.name }
        return paid - used
    }
}

data class ExcelData(
    val clients: List<Client>,
    val payments: List<Payment>,
    val workouts: List<Workout>,
)
