package org.whoismacy.mainconsumapp.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object DateTimeUtil {

    fun now(): LocalDateTime {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    fun nowString(): String {
        return now().toString()
    }

    fun formatDisplay(dateTimeString: String): String {
        return try {
            val dateTime = LocalDateTime.parse(dateTimeString)
            val day = dateTime.dayOfMonth.toString().padStart(2, '0')
            val month = dateTime.monthNumber.toString().padStart(2, '0')
            val year = dateTime.year
            val hour = dateTime.hour
            val minute = dateTime.minute.toString().padStart(2, '0')
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour

            "$day/$month/$year  $displayHour:$minute $amPm"
        } catch (e: Exception) {
            dateTimeString
        }
    }

    fun formatDate(dateTimeString: String): String {
        return try {
            val dateTime = LocalDateTime.parse(dateTimeString)
            val day = dateTime.dayOfMonth.toString().padStart(2, '0')
            val month = dateTime.monthNumber.toString().padStart(2, '0')
            val year = dateTime.year
            "$day/$month/$year"
        } catch (e: Exception) {
            dateTimeString
        }
    }
}
