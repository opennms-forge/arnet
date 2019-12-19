package org.opennms.oia.streaming.model

import org.opennms.integration.api.v1.model.Alarm
import org.opennms.integration.api.v1.model.InMemoryEvent
import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.TopologyEdge

enum class MessageType {
    Alarm,
    AlarmDelete,
    Edge,
    EdgeDelete,
    Event,
    Topology,
    Node
}

data class StreamMessage(val type: MessageType, val payload: Any)

// Use these to generate a response
fun alarmMessage(alarm: Alarm) = StreamMessage(MessageType.Alarm, alarm)
fun alarmDeleteMessage(reductionKey: String, isSituation: Boolean) = StreamMessage(MessageType.AlarmDelete, AlarmDelete(reductionKey, isSituation))
fun edgeMessage(edge: TopologyEdge) = StreamMessage(MessageType.Edge, edge)
fun edgeDeleteMessage(edge: TopologyEdge) = StreamMessage(MessageType.EdgeDelete, edge)
fun eventMessage(event: InMemoryEvent) = StreamMessage(MessageType.Event, event)
fun topologyMessage(topology: Topology) = StreamMessage(MessageType.Topology, topology)
fun nodeMessage(node: Node) = StreamMessage(MessageType.Node, node)
