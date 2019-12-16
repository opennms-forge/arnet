package org.opennms.arnet.model

import java.util.*

data class Event(
    val UEI: String,
    val description: String,
    val time: Date,
    val vertexId: String
)