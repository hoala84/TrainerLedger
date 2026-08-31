package com.example.trainerledger.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val clients: List<ClientBackup>,
    val payments: List<PaymentBackup>,
    val workouts: List<WorkoutBackup>,
)

@Serializable
data class ClientBackup(
    val id: Long,
    val lastName: String,
    val firstName: String,
    val updatedAt: Long,
)

@Serializable
data class PaymentBackup(
    val id: Long,
    val clientId: Long,
    val date: Long,
    val amount: Double,
    val workoutCount: Int,
    val autoWorkoutId: Long? = null,
)

@Serializable
data class WorkoutBackup(
    val id: Long,
    val clientId: Long,
    val date: Long,
    val comment: String,
    val type: String,
)
