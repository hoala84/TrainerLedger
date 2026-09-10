package com.example.trainerledger.ui.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.trainerledger.TrainerApplication
import com.example.trainerledger.domain.model.Client
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repo = (application as TrainerApplication).repository
    val clientId: Long = checkNotNull(savedStateHandle["clientId"])

    val client: StateFlow<Client?> = repo.observeClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val remaining: StateFlow<Int> = repo.observeRemaining(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val payments: StateFlow<List<Payment>> = repo.observePayments(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workouts: StateFlow<List<Workout>> = repo.observeWorkouts(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun saveClient(client: Client) {
        repo.saveClient(client)
    }

    fun deleteClient(onDone: () -> Unit) {
        val current = client.value ?: return
        viewModelScope.launch {
            repo.deleteClient(current)
            onDone()
        }
    }

    suspend fun savePayment(existing: Payment?, date: Long, amount: Double, workoutCount: Int, debtCount: Int) {
        repo.savePayment(
            (existing ?: Payment(clientId = clientId, date = date, amount = amount, workoutCount = workoutCount))
                .copy(date = date, amount = amount, workoutCount = workoutCount), debtCount,
        )
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch { repo.deletePayment(payment) }
    }

    fun saveWorkout(existing: Workout?, date: Long, comment: String, type: WorkoutType) {
        viewModelScope.launch {
            if (existing == null) {
                repo.addWorkout(clientId, date, comment, type)
            } else {
                repo.updateWorkout(existing.copy(date = date, comment = comment, type = type))
            }
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch { repo.deleteWorkout(workout) }
    }
}
