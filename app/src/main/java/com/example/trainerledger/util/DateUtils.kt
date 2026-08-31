package com.example.trainerledger.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val displayFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val shortFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val fileFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    /** Material DatePicker отдаёт UTC-полночь выбранного дня. */
    fun fromUtcPicker(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return localDate(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun toUtcPicker(localStartOfDay: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localStartOfDay }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
            )
        }.timeInMillis
    }

    fun startOfDay(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return localDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return cal.timeInMillis
    }

    fun startOfMonth(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return localDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1)
    }

    fun localDate(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }.timeInMillis
    }

    fun daysInRange(from: Long, to: Long): List<Long> {
        val start = startOfDay(from)
        val end = startOfDay(to)
        val days = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        while (cal.timeInMillis <= end) {
            days += cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return days
    }

    fun formatDisplay(millis: Long): String = displayFormat.format(Date(millis))

    fun formatShort(millis: Long): String = shortFormat.format(Date(millis))

    fun formatFileDate(millis: Long): String = fileFormat.format(Date(millis))
}
