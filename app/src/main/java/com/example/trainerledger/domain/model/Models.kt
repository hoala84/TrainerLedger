package com.example.trainerledger.domain.model

/** Тип проведённой тренировки */
enum class WorkoutType {
    /** Оплаченная — списывается с баланса клиента */
    PAID,
    /** В долг — не списывает баланс */
    DEBT,
    /** Подарочная — бесплатная */
    GIFT,
}

/** Вариант сортировки списка клиентов */
enum class ClientSortOrder {
    BY_UPDATED,
    ALPHABETICAL,
}

/** Строка списка клиентов с остатком занятий */
data class ClientRow(
    val client: Client,
    val remainingWorkouts: Int,
    val debtWorkouts: Int,
)

/** Клиент тренера */
data class Client(
    val id: Long = 0,
    val lastName: String,
    val firstName: String,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Отображаемое имя: фамилия, затем имя */
    val displayName: String get() = "$lastName $firstName"
}

/** Оплата за пакет тренировок */
data class Payment(
    val id: Long = 0,
    val clientId: Long,
    val date: Long,
    val amount: Double,
    val workoutCount: Int,
    /** ID автоматически созданной тренировки (если оплата за 1 занятие) */
    val autoWorkoutId: Long? = null,
)

/** Проведённая тренировка */
data class Workout(
    val id: Long = 0,
    val clientId: Long,
    val date: Long,
    val comment: String = "",
    val type: WorkoutType = WorkoutType.PAID,
)

/** Сводка по клиенту за период */
data class ClientPeriodStats(
    val client: Client,
    val completedWorkouts: Int,
    val giftWorkouts: Int,
    val debtWorkouts: Int,
    val income: Double,
)

/** Общая статистика за период */
data class PeriodStats(
    val totalWorkouts: Int,
    val giftWorkouts: Int,
    val debtWorkouts: Int,
    val totalIncome: Double,
    val perClient: List<ClientPeriodStats>,
)
