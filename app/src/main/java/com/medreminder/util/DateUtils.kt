package com.medreminder.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

object DateUtils {
    // Formatters are created per-call so they always reflect the current Locale.
    private fun dateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private fun dateTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    private fun timeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())


    fun getStartOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Ensure we don't return a future date
            if (timeInMillis > System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, -1)
            }
        }
        return cal.timeInMillis
    }

    fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.getDefault(), "%d:%02d %s", h, minute, amPm)
    }

    fun formatDate(millis: Long): String =
        dateFormatter().format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun formatDateTime(millis: Long): String =
        dateTimeFormatter().format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun formatTimeOnly(millis: Long): String =
        timeFormatter().format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun isToday(millis: Long): Boolean {
        val today = Calendar.getInstance()
        val check = Calendar.getInstance().apply { timeInMillis = millis }
        return today.get(Calendar.YEAR) == check.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == check.get(Calendar.DAY_OF_YEAR)
    }

    fun daysAgo(days: Int): Long {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return getStartOfDay(cal.timeInMillis)
    }

    fun getDayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> ""
    }

    fun getDayFullName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SUNDAY -> "Sunday"
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> ""
    }
}
