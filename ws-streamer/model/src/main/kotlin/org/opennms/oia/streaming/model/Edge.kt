package org.opennms.oia.streaming.model

data class Edge(
    val id: String,
    val sourceVertex: Vertex,
    val targetVertex: Vertex,
    val protocol: String
)