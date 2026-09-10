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
    version = 3,
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
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE clients ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE clients ADD COLUMN birthDate TEXT")
                        db.execSQL("ALTER TABLE clients ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                    }
                })
                .addMigrations(object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE workouts ADD COLUMN settledByPaymentId INTEGER")
                    }
                })
                .build()
        }
    }
}
