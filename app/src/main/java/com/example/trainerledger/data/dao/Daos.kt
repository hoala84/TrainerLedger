package com.example.trainerledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trainerledger.data.entity.ClientEntity
import com.example.trainerledger.data.entity.PaymentEntity
import com.example.trainerledger.data.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients")
    suspend fun getAll(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeById(id: Long): Flow<ClientEntity?>

    @Insert
    suspend fun insert(client: ClientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Update
    suspend fun update(client: ClientEntity)

    @Query("UPDATE clients SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Delete
    suspend fun delete(client: ClientEntity)

    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY `date` DESC, id DESC")
    fun observeByClient(clientId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE `date` BETWEEN :from AND :to ORDER BY `date` ASC, id ASC")
    suspend fun getInPeriod(from: Long, to: Long): List<PaymentEntity>

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("UPDATE payments SET autoWorkoutId = :workoutId WHERE id = :paymentId")
    suspend fun setAutoWorkoutId(paymentId: Long, workoutId: Long?)

    @Query("UPDATE payments SET autoWorkoutId = NULL WHERE autoWorkoutId = :workoutId")
    suspend fun clearAutoWorkout(workoutId: Long)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts")
    suspend fun getAll(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE clientId = :clientId ORDER BY `date` DESC, id DESC")
    fun observeByClient(clientId: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE `date` BETWEEN :from AND :to ORDER BY `date` ASC, id ASC")
    suspend fun getInPeriod(from: Long, to: Long): List<WorkoutEntity>

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}
