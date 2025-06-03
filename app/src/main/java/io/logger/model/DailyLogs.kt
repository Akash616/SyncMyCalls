package io.logger.model

data class DailyLogs(
    val date: String,
    val calls: List<Call>
) {
    data class Call(
        val name: String,
        val number: String,
        val time: String,
        val callType: String,
        val duration: String
    )
}
