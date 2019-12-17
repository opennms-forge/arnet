package org.opennms.arnet.model

import java.util.*

enum class Severity {
    INDETERMINATE,
    CLEARED,
    NORMAL,
    WARNING,
    MINOR,
    MAJOR,
    CRITICAL
}

data class Alarm(
    val reductionKey: String,
    val severity: Severity,
    val description: String,
    val lastUpdated: Date,
    val vertexId: String
)