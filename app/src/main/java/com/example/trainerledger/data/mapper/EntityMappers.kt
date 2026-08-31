package com.example.trainerledger.data.mapper

import com.example.trainerledger.data.entity.ClientEntity
import com.example.trainerledger.data.entity.PaymentEntity
import com.example.trainerledger.data.entity.WorkoutEntity
import com.example.trainerledger.domain.model.Client
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType

/** Преобразование между Room-сущностями и доменными моделями */
object EntityMappers {

    fun ClientEntity.toDomain() = Client(id, lastName, firstName, updatedAt)

    fun Client.toEntity() = ClientEntity(id, lastName, firstName, updatedAt)

    fun PaymentEntity.toDomain() = Payment(id, clientId, date, amount, workoutCount, autoWorkoutId)

    fun Payment.toEntity() = PaymentEntity(id, clientId, date, amount, workoutCount, autoWorkoutId)

    fun WorkoutEntity.toDomain() = Workout(
        id = id,
        clientId = clientId,
        date = date,
        comment = comment,
        type = WorkoutType.valueOf(type),
    )

    fun Workout.toEntity() = WorkoutEntity(id, clientId, date, comment, type.name)
}
