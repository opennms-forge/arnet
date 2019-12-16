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

interface AlarmFields {
    val reductionKey: String
    val severity: Severity
    val description: String
    val lastUpdated: Date
    val vertexId: String
}

data class Alarm(
    override val reductionKey: String,
    override val severity: Severity,
    override val description: String,
    override val lastUpdated: Date,
    override val vertexId: String
) : AlarmFields