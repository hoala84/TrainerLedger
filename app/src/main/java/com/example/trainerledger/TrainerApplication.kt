package com.example.trainerledger

import android.app.Application
import com.example.trainerledger.data.db.AppDatabase
import com.example.trainerledger.data.repo.LedgerRepository

class TrainerApplication : Application() {
    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = LedgerRepository(AppDatabase.create(this))
    }
}
