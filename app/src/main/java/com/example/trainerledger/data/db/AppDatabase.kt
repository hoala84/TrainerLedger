package com.example.trainerledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.trainerledger.data.dao.ClientDao
import com.example.trainerledger.data.dao.PaymentDao
import com.example.trainerledger.data.dao.WorkoutDao
import com.example.trainerledger.data.entity.ClientEntity
import com.example.trainerledger.data.entity.PaymentEntity
import com.example.trainerledger.data.entity.WorkoutEntity

@Database(
    entities = [ClientEntity::class, PaymentEntity::class, WorkoutEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun paymentDao(): PaymentDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "trainer_ledger.db"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
