package com.example.util

import java.util.Calendar
import java.util.Locale

data class ReportDateParts(
    val timestampMillis: Long,
    val dayName: String,
    val dateLabel: String,
    val timeLabel: String,
    val yearMonth: String,
    val dayOfMonth: Int,
    val hour: Int,
    val minute: Int
)

object DateTimeUtils {
    val INDONESIAN_DAYS = listOf(
        "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
    )

    val INDONESIAN_MONTHS = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    fun extractReportDateParts(millis: Long = System.currentTimeMillis()): ReportDateParts {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val dayOfWeekIdx = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
        val dayName = INDONESIAN_DAYS[dayOfWeekIdx]
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val dateLabel = "$dayOfMonth ${INDONESIAN_MONTHS[monthIdx]} $year"
        val timeLabel = String.format(Locale.US, "%02d:%02d", hour, minute)
        val yearMonth = String.format(Locale.US, "%04d-%02d", year, monthIdx + 1)

        return ReportDateParts(
            timestampMillis = millis,
            dayName = dayName,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            yearMonth = yearMonth,
            dayOfMonth = dayOfMonth,
            hour = hour,
            minute = minute
        )
    }

    fun updateDateKeepingTime(currentMillis: Long, selectedUtcDateMillis: Long): Long {
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedUtcDateMillis
        }
        val merged = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return merged.timeInMillis
    }

    fun updateTimeKeepingDate(currentMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun toUtcMidnightMillis(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis
    }

    fun formatMonthYearLabel(year: Int, month1Based: Int): String {
        val idx = (month1Based - 1).coerceIn(0, 11)
        return "${INDONESIAN_MONTHS[idx]} $year"
    }

    fun formatYearMonthKey(year: Int, month1Based: Int): String {
        return String.format(Locale.US, "%04d-%02d", year, month1Based.coerceIn(1, 12))
    }

    fun formatChatTime(millis: Long?): String {
        if (millis == null || millis <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.US,
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    fun generateReadableTeamCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = (1..4).map { chars.random() }.joinToString("")
        return "BPS-$suffix"
    }
}
