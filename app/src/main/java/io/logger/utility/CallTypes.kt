package io.logger.utility

object CallTypes {
    const val INCOMING = "Incoming"
    const val OUTGOING = "OutGoing"
    const val ANSWERED_EXTERNALLY = "Answered Externally"
    const val MISSED = "Missed"
    const val REJECTED = "Rejected"
    const val BLOCKED = "Blocked"
    const val WIFI_INCOMING = "Wifi Incoming"
    const val WIFI_OUTGOING = "Wifi Outgoing"
    const val VOICE_MAIL = "Voice Mail"
    const val UNKNOWN = "Unknown"
    const val OTHER = "Other"

    val ALL_TYPES = setOf(
        INCOMING, OUTGOING, ANSWERED_EXTERNALLY, MISSED, REJECTED, BLOCKED,
        WIFI_INCOMING, WIFI_OUTGOING, VOICE_MAIL, UNKNOWN, OTHER
    )
}