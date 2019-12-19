package org.opennms.oia.streaming.model

import com.fasterxml.jackson.annotation.JsonInclude
import org.opennms.integration.api.v1.model.Alarm
import org.opennms.integration.api.v1.model.TopologyEdge

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Topology(
    val nodes: Set<DeserializableNode>? = null,
    val edges: Set<TopologyEdge>? = null,
    val alarms: Set<Alarm>? = null
)