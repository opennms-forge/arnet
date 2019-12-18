package org.opennms.oia.streaming.model

enum class Type {
    Node,
    Port,
    Segment
}

data class Vertex(
    val id: String,
    val label: String,
    val type: Type
)