package com.example.trainerledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Таблица клиентов */
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lastName: String,
    val firstName: String,
    val updatedAt: Long,
    val comment: String = "",
    /** Дата рождения: yyyy-MM-dd, без часового пояса. */
    val birthDate: String? = null,
    val phone: String = "",
)

/** Таблица оплат */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clientId"), Index("date")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val date: Long,
    val amount: Double,
    val workoutCount: Int,
    val autoWorkoutId: Long? = null,
)

/** Таблица тренировок */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clientId"), Index("date")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val date: Long,
    val comment: String,
    /** Хранится как строка enum WorkoutType */
    val type: String,
    val settledByPaymentId: Long? = null,
)
