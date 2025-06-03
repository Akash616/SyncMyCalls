package io.logger.user.repository

import android.content.Context
import android.provider.CallLog
import io.logger.model.DailyLogs
import io.logger.utility.CallTypes
import java.text.SimpleDateFormat
import java.util.*

object CallLogRepository {

    fun fetchCallLogs(
        context: Context,
        callTypes: Set<String> = emptySet(),
        phoneNumber: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        dateType: String? = null
    ): List<DailyLogs> {
        val callLogs = mutableListOf<DailyLogs>()
        val callLogList = mutableMapOf<String, MutableList<DailyLogs.Call>>()

        val selection = buildString {
            if (callTypes.isNotEmpty()) {
                val typeConditions = callTypes.map { type ->
                    when (type) {
                        "Missed" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
                        "Rejected" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.REJECTED_TYPE}"
                        "Incoming" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}"
                        "Outgoing" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}"
                        "Blocked" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.BLOCKED_TYPE}"
                        "Wifi Incoming" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE} AND ${CallLog.Calls.PHONE_ACCOUNT_ID} LIKE '%wifi%'"
                        "Wifi Outgoing" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE} AND ${CallLog.Calls.PHONE_ACCOUNT_ID} LIKE '%wifi%'"
                        "Voice Mail" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.VOICEMAIL_TYPE}"
                        "Unknown" -> "(${CallLog.Calls.NUMBER} IS NULL OR ${CallLog.Calls.NUMBER} = '')"
                        else -> "1 = 1"
                    }
                }.joinToString(" OR ")
                append("($typeConditions)")
            }
            if (phoneNumber != null && phoneNumber.isNotEmpty()) {
                if (isNotEmpty()) append(" AND ")
                append("${CallLog.Calls.NUMBER} LIKE ?")
            }
            if (startDate != null || endDate != null) {
                if (isNotEmpty()) append(" AND ")
                append("${CallLog.Calls.DATE} BETWEEN ? AND ?")
            }
        }

        val selectionArgs = mutableListOf<String>()
        if (phoneNumber != null && phoneNumber.isNotEmpty()) {
            selectionArgs.add("%$phoneNumber%")
        }
        if (startDate != null || endDate != null) {
            selectionArgs.add((startDate ?: 0).toString())
            selectionArgs.add((endDate ?: Long.MAX_VALUE).toString())
        }

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                selection.toString().takeIf { it.isNotEmpty() },
                selectionArgs.toTypedArray().takeIf { selection.isNotEmpty() },
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                val todayCalendar = Calendar.getInstance()
                val callCalendar = Calendar.getInstance()

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val number = it.getString(numberIndex) ?: ""
                    val type = it.getInt(typeIndex)
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)

                    callCalendar.timeInMillis = date
                    val isToday = todayCalendar.get(Calendar.YEAR) == callCalendar.get(Calendar.YEAR) &&
                            todayCalendar.get(Calendar.DAY_OF_YEAR) == callCalendar.get(Calendar.DAY_OF_YEAR)
                    val callTime = timeFormatter.format(Date(date))
                    val callDate = dateFormatter.format(Date(date))
                    val displayDate = if (isToday) "Today" else callDate

                    val callType = when (type) {
                        CallLog.Calls.MISSED_TYPE -> CallTypes.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallTypes.REJECTED
                        CallLog.Calls.INCOMING_TYPE -> CallTypes.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallTypes.OUTGOING
                        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallTypes.ANSWERED_EXTERNALLY
                        CallLog.Calls.BLOCKED_TYPE -> CallTypes.BLOCKED
                        CallLog.Calls.VOICEMAIL_TYPE -> CallTypes.VOICE_MAIL
                        else -> "Other"
                    }

                    val call = DailyLogs.Call(name, number, callTime, callType, duration.toString())
                    callLogList.getOrPut(displayDate) { mutableListOf() }.add(call)
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("CallLogRepository", "Permission denied for call logs", e)
            return emptyList()
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Error fetching call logs", e)
            return emptyList()
        }

        callLogList.forEach { (date, calls) ->
            callLogs.add(DailyLogs(date, calls))
        }

        return callLogs.sortedWith(compareByDescending { log ->
            if (log.date == "Today") Long.MAX_VALUE
            else try {
                SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).parse(log.date)?.time ?: 0L
            } catch (e: Exception) {
                android.util.Log.e("CallLogRepository", "Error parsing date: ${log.date}", e)
                0L
            }
        })
    }
}