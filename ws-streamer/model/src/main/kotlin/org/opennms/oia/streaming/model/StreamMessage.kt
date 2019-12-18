package org.opennms.oia.streaming.model

enum class MessageType {
    Alarm,
    Edge,
    Event,
    Situation,
    Topology,
    Vertex
}

data class StreamMessage(val type: MessageType, val payload: Any)

// Use these to generate a response
fun alarmMessage(alarm: Alarm) = StreamMessage(MessageType.Alarm, alarm)
fun edgeMessage(edge: Edge) = StreamMessage(MessageType.Edge, edge)
fun eventMessage(event: Event) = StreamMessage(MessageType.Event, event)
fun situationMessage(situation: Situation) = StreamMessage(MessageType.Situation, situation)
fun topologyMessage(topology: Topology) = StreamMessage(MessageType.Topology, topology)
fun vertexMessage(vertex: Vertex) = StreamMessage(MessageType.Vertex, vertex)