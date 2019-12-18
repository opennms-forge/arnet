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
    EventDelete,
    Situation,
    SituationDelete,
    Topology,
    Node,
    NodeDelete
}

data class StreamMessage(val type: MessageType, val payload: Any)

// Use these to generate a response
fun alarmMessage(alarm: Alarm) = StreamMessage(MessageType.Alarm, alarm)
fun alarmDeleteMessage(reductionKey: String) = StreamMessage(MessageType.AlarmDelete, AlarmDelete(reductionKey))
fun edgeMessage(edge: TopologyEdge) = StreamMessage(MessageType.Edge, edge)
fun eventMessage(event: InMemoryEvent) = StreamMessage(MessageType.Event, event)
fun situationMessage(situation: Alarm) = StreamMessage(MessageType.Situation, situation)
fun topologyMessage(topology: Topology) = StreamMessage(MessageType.Topology, topology)
fun nodeMessage(node: Node) = StreamMessage(MessageType.Node, node)