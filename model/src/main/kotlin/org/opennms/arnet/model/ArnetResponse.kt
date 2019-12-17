package org.opennms.arnet.model

enum class ResponseType {
    Alarm,
    Edge,
    Event,
    Situation,
    Topology,
    Vertex
}

data class ArnetResponse(val type: ResponseType, val payload: Any)

// Use these to generate a response
fun alarmResponse(alarm: Alarm) = ArnetResponse(ResponseType.Alarm, alarm)
fun edgeResponse(edge: Edge) = ArnetResponse(ResponseType.Edge, edge)
fun eventResponse(event: Event) = ArnetResponse(ResponseType.Event, event)
fun situationResponse(situation: Situation) = ArnetResponse(ResponseType.Situation, situation)
fun topologyResponse(topology: Topology) = ArnetResponse(ResponseType.Topology, topology)
fun vertexResponse(vertex: Vertex) = ArnetResponse(ResponseType.Vertex, vertex)