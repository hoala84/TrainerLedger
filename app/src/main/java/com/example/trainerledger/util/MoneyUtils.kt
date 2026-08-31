package com.example.trainerledger.util

import java.util.Locale

object MoneyUtils {
    fun format(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.getDefault(), "%,d ₽", amount.toLong())
        } else {
            String.format(Locale.getDefault(), "%,.2f ₽", amount)
        }
    }

    fun parse(raw: String): Double? {
        val normalized = raw.trim().replace(" ", "").replace(',', '.')
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()
    }
}
