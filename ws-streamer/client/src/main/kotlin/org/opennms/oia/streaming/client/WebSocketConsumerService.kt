package org.opennms.oia.streaming.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.collect.Maps
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph

import org.java_websocket.handshake.ServerHandshake
import org.opennms.integration.api.v1.model.*
import org.opennms.oia.streaming.client.api.Consumer
import org.opennms.oia.streaming.client.api.ConsumerService
import org.opennms.oia.streaming.client.api.model.Edge
import org.opennms.oia.streaming.client.api.model.Situation
import org.opennms.oia.streaming.client.api.model.Vertex
import org.opennms.oia.streaming.model.*
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

import java.net.URI
import java.nio.ByteBuffer
import java.util.*

import java.util.concurrent.CopyOnWriteArrayList

class WebSocketConsumerService : ConsumerService {

    private val client: WebSocketClient = WebSocketClient(URI(WEB_SOCKET_SERVER))

    private val consumers = CopyOnWriteArrayList<Consumer>()

    private val mapper = jacksonObjectMapper()

    private val vertices = mutableMapOf<String, Vertex>()

    private val edges = mutableMapOf<String, Edge>()

    // TODO: Need to get this logging in android
    private val log = LoggerFactory.getLogger(WebSocketConsumerService::class.java)

    private var graph: Graph<Vertex, Edge> = SparseMultigraph()

    private val initialAlarms = mutableListOf<org.opennms.oia.streaming.client.api.model.Alarm>()

    private val initialSituations = mutableListOf<Situation>()

    override fun accept(consumer: Consumer) {
        log.info("Adding consumer.")
        consumers.add(consumer)

        // TODO: only supporting a single consumer
        //  If multiple consumers were to be supported, the first consumer subscribes - with
        //  subsequent consumers refreshing the topology. The server side would need to support
        //  a topology refresh operation.

        if (client.isOpen) {
            if (consumers.size == 1) {
                client.send(mapper.writeValueAsString(StreamRequest(
                    RequestAction.SUBSCRIBE/*, FILTER_CRITERIA*/)))
            } else {
                consumer.accept(graph, initialAlarms, initialSituations)
            }
        }
     }

    override fun dismiss(consumer: Consumer?) {
        log.info("Removing consumer.")
        consumers.remove(consumer)
    }

    override fun start() {
        log.info("Starting...")

        log.info("Attempting to connect.")
        try {
            // Initiates the web socket connection; does not block.
            client.connect()
        } catch (e: InterruptedException) {
            log.info("Interrupted while attempting to connect")
        }
    }

    override fun stop() {
        log.info("Stopping...")

        if (client.isOpen) {
            log.info("Closing client.")
            client.close()
        }
    }

    inner class WebSocketClient(serverURI: URI) :
        org.java_websocket.client.WebSocketClient(serverURI) {

        override fun onOpen(handshakedata: ServerHandshake) {
            log.info("open: status '${handshakedata.httpStatus}'")

            if (consumers.isNotEmpty()) {
                client.send(mapper.writeValueAsString(StreamRequest(
                    RequestAction.SUBSCRIBE/*, FILTER_CRITERIA*/)))
            }
        }

        override fun onMessage(message: String) {
            log.info("message: $message")

            val response = mapper.readValue<StreamMessage>(message)

            when(response.type) {
                MessageType.Alarm -> processAlarm(response)
                MessageType.AlarmDelete -> processAlarmDelete(response)
                MessageType.Edge -> processEdge(response)
                MessageType.EdgeDelete -> processEdgeDelete(response)
                MessageType.Event -> processEvent(response)
                MessageType.Topology -> processTopology(response)
                MessageType.Node -> processNode(response)
                else -> log.warn("Unsupported message type '${response.type}'")
            }
        }

        override fun onMessage(bytes: ByteBuffer?) {
            requireNotNull(bytes)
            onMessage(String(bytes.array()))
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            log.info("close: code: '$code', reason '$reason', remote: '$remote'.")
        }

        override fun onError(ex: Exception) {
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            log.info("error: $sw")
        }
    }

    fun processAlarm(message: StreamMessage) {
        val alarm = message.deserializePayload<Alarm>()
        log.info("Processing alarm ${alarm.reductionKey}")
        consumers.forEach { c ->
            try {
                if (alarm.isSituation) {
                    c.acceptSituation(convertSituation(alarm))
                } else {
                    c.acceptAlarm(convertAlarm(alarm))
                }
            } catch (er: Error) {
                log.warn("Consumer unable to process alarm ${alarm.reductionKey} : $er")
            }
        }
    }

    fun processAlarmDelete(message: StreamMessage) {
        val alarm = message.deserializePayload<AlarmDelete>()
        log.info("Processing alarm deletion ${alarm.reductionKey}")
        consumers.forEach { c ->
            try {
                if (alarm.isSituation) {
                    c.acceptDeleteSituation(alarm.reductionKey)
                } else {
                    c.acceptDeletedAlarm(alarm.reductionKey)
                }
            } catch (er: Error) {
                log.info("Consumer unable to process alarm delete ${alarm.reductionKey} : $er")
            }
        }
    }

    fun processEdge(message: StreamMessage) {
        val edge = message.deserializePayload<TopologyEdge>()
        log.info("Processing edge ${edge.id}")
        val ve = convertTopologyEdge(edge)

        consumers.forEach { consumer ->
            try {
                if (edges.containsKey(ve.edge.id)) {
                    log.debug("Update to edge ${ve.edge.id}")
                    // TODO: is this supported?
                } else {
                    log.debug("New edge ${ve.edge.id}")
                    edges.put(ve.edge.id, ve.edge)
                    if (!vertices.containsKey(ve.vertices.src.id)) {
                        vertices.put(ve.vertices.src.id, ve.vertices.src)
                    }
                    if (!vertices.containsKey(ve.vertices.dst.id)) {
                        vertices.put(ve.vertices.dst.id, ve.vertices.dst)
                    }
                    consumer.acceptEdge(ve.edge)
                }
            } catch (er : Error) {
                log.warn("Consumer unable to process edge ${edge.id} : $er")
            }
        }
    }

    fun processEdgeDelete(message: StreamMessage) {
        val edge = message.deserializePayload<TopologyEdge>()
        log.info("Processing edge delete ${edge.id}")

        consumers.forEach { consumer ->
            try {
                if (!edges.containsKey(edge.id)) {
                    log.debug("Edge ${edge.id} does not exist")
                } else {
                    log.info("Deleting edge ${edge.id}")
                    edges.remove(edge.id)
                    consumer.acceptDeletedEdge(edge.id)
                }
            } catch (er : Error) {
                log.warn("Consumer unable to process edge delete ${edge.id} : $er")
            }
        }
    }

    fun processEvent(message: StreamMessage) {
        val event = message.deserializePayload<InMemoryEvent>()
        log.info("Processing event ${event.uei}")
        consumers.forEach {
            try {
                it.acceptEvent(convertEvent(event))
            } catch (e: Error) {
                log.warn("Consumer unable to process event ${event.uei} : $e")
            }
        }
    }

    fun processTopology(message: StreamMessage) {
        log.info("Processing topology")
        val topology = message.deserializePayload<Topology>()

        edges.clear()
        vertices.clear()
        initialAlarms.clear()
        initialSituations.clear()
        graph = SparseMultigraph<Vertex, Edge>()

        // Handles node that appear directly in Topology
        val verticesNodes = topology.nodes
            ?.map { n -> convertNode(n) }
            ?.map { it.id to it }
            ?.toMap()
            ?.toMutableMap()

        // Handles node, port, and segment in each TopologyEdge
        val verticesTopologyEdge = mutableMapOf<String, Vertex>()
        topology.edges?.forEach {
            val ve = convertTopologyEdge(it)
            edges.put(ve.edge.id, ve.edge)
            if (!verticesTopologyEdge.containsKey(ve.vertices.src.id)) {
                verticesTopologyEdge.put(ve.vertices.src.id, ve.vertices.src)
            }
            if (!verticesTopologyEdge.containsKey(ve.vertices.dst.id)) {
                verticesTopologyEdge.put(ve.vertices.dst.id, ve.vertices.dst)
            }
        }

        // A Map of all vertices in the topology
        if (verticesNodes != null) {
            vertices.putAll(verticesNodes)
        }
        vertices.putAll(verticesTopologyEdge)

        if (verticesNodes != null) {
            // Contains all "island" nodes (a node without edges)
            val diff = Maps.difference(verticesTopologyEdge, verticesNodes).entriesOnlyOnRight()

            // Add "island" nodes (vertices) to the graph
            diff.forEach { graph.addVertex(it.value) }
        }

        // Add nodes and edges to graph
        edges.values.forEach { graph.addEdge(it, it.sourceVertex, it.targetVertex) }

        val alarms = topology.alarms?.filter { !it.isSituation }?.map { convertAlarm(it) }
        if (alarms != null) {
            initialAlarms.addAll(alarms)
        }

        val situations = topology.alarms?.filter { it.isSituation }?.map { convertSituation(it) }
        if (situations != null) {
            initialSituations.addAll(situations)
        }

        log.info("Graph contains ${graph.vertexCount} vertices " +
                "and ${graph.edgeCount} edges. There are ${alarms?.size} alarms " +
                "and situations ${situations?.size}")

        consumers.forEach {
            try {
                it.accept(graph, initialAlarms, initialSituations)
            } catch (e: Error) {
                log.warn("Consumer unable to process topology : $e")
            }
        }
    }

    fun processNode(message: StreamMessage) {
        val node = message.deserializePayload<Node>()
        log.info("Processing node ${node.id}")
        val convNode = convertNode(node)
        consumers.forEach {
            try {
                if (vertices.containsKey(convNode.id)) {
                    log.debug("Update to node ${convNode.id}")
                    // TODO: is this supported?
                } else {
                    log.debug("New vertex ${convNode.id}")
                    vertices.put(convNode.id, convNode)
                    it.acceptVertex(convNode)
                }
            } catch (e: Error) {
                log.warn("Consumer unable to process node ${node.id} : $e")
            }
        }
    }

    fun convertTopologyEdge(topologyEdge : TopologyEdge) : EdgeVertex {

        lateinit var srcVertex: Vertex
        lateinit var dstVertex: Vertex

        topologyEdge.visitEndpoints(object : TopologyEdge.EndpointVisitor {
            override fun visitSource(node: Node?) {
                if (node != null) {
                    srcVertex = convertNode(node)
                }
            }

            override fun visitSource(port: TopologyPort?) {
                if (port != null) {
                    srcVertex = convertPort(port)
                }
            }

            override fun visitSource(segment: TopologySegment?) {
                if (segment != null) {
                    srcVertex = convertSegment(segment)
                }
            }

            override fun visitTarget(node: Node?) {
                if (node != null) {
                    dstVertex = convertNode(node)
                }
            }

            override fun visitTarget(port: TopologyPort?) {
                if (port != null) {
                    dstVertex = convertPort(port)
                }
            }

            override fun visitTarget(segment: TopologySegment?) {
                if (segment != null) {
                    dstVertex = convertSegment(segment)
                }
            }
        })

        val edge = EdgeImpl(id = topologyEdge.id + "-" + topologyEdge.protocol.name,
            src = srcVertex, dst = dstVertex, proto = topologyEdge.protocol.name)

        return EdgeVertex(edge, VertexPair(srcVertex, dstVertex))
    }

    data class EdgeImpl(private val id: String,
                    private val src: Vertex,
                    private val dst: Vertex,
                    private val proto : String) : Edge {
        override fun getId() = id
        override fun getSourceVertex() = src
        override fun getTargetVertex() = dst
        override fun getProtocol() = proto
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EdgeImpl

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    data class VertexImpl(private val id: String,
                      private val label: String,
                      private val type : Vertex.Type) : Vertex {
        override fun getId() = id
        override fun getLabel() = label
        override fun getType() = type
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VertexImpl

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    data class EdgeVertex(val edge: Edge, val vertices: VertexPair)

    data class VertexPair(val src: Vertex, val dst: Vertex)

    fun convertAlarm(alarm: Alarm) = object : org.opennms.oia.streaming.client.api.model.Alarm {
            override fun getReductionKey(): String {
                return alarm.reductionKey
            }

            override fun getSeverity(): org.opennms.oia.streaming.client.api.model.Alarm.Severity {
                return org.opennms.oia.streaming.client.api.model.Alarm.Severity.valueOf(
                    alarm.severity.name
                )
            }

            override fun getDescription(): String {
                return alarm.description
            }

            override fun getLastUpdated(): Date {
                return alarm.lastEventTime
            }

            override fun getVertexId(): String {
                return alarm.node.id.toString()
            }
        }

    fun convertEvent(event: InMemoryEvent) = object : org.opennms.oia.streaming.client.api.model.Event {
        override fun getUEI(): String {
            return event.uei
        }

        override fun getDescription(): String {
            // TODO: can this be generated?
            return ""
        }

        override fun getTime(): Date {
            // TODO: can this be generated?
            return Date()
        }

        override fun getVertexId(): String {
            return event.nodeId.toString()
        }
    }

    fun convertSituation(situation: Alarm) = object : Situation {
        override fun getReductionKey(): String {
            return situation.reductionKey
        }

        override fun getSeverity(): org.opennms.oia.streaming.client.api.model.Alarm.Severity {
            return org.opennms.oia.streaming.client.api.model.Alarm.Severity.valueOf(
                situation.severity.name
            )
        }

        override fun getDescription(): String {
            return situation.description
        }

        override fun getLastUpdated(): Date {
            return situation.lastEventTime
        }

        override fun getVertexId(): String {
            return situation.node.id.toString()
        }

        override fun getRelatedAlarms(): MutableSet<org.opennms.oia.streaming.client.api.model.Alarm> {
            return situation.relatedAlarms.map { a -> convertAlarm(a) }.toMutableSet()
        }
    }

    fun convertNode(node: Node) : Vertex {
        return VertexImpl(id = node.id.toString(), label = node.label, type = Vertex.Type.Node)
    }

    fun convertPort(port: TopologyPort) : Vertex {
        // TODO: can "label" be generated?
        return VertexImpl(id = port.id.toString(), label = "", type = Vertex.Type.Port)
    }

    fun convertSegment(segment: TopologySegment) : Vertex {
        // TODO: can "label" be generated?
        return VertexImpl(id = segment.id.toString(), label = "", type = Vertex.Type.Segment)
    }

    // For unit testing...
    fun addConsumer(consumer: Consumer) {
        consumers.add(consumer)
    }

    // For unit testing...
    fun numEdges() : Int {
        return edges.size
    }

    // For unit testing...
    fun numVertices() : Int {
        return vertices.size
    }
    
    private companion object {

        /**
         * The WebSocket server IP and port.
         * Remember to update this accordingly!
         */
//        private val WEB_SOCKET_SERVER = "ws://172.20.50.148:8081"

        // Matt's system...
        private val WEB_SOCKET_SERVER = "ws://172.20.50.109:8080"

        /**
         * The (optional) filter criteria used for subscribing to ARNet streaming service.
         * Location(s) can be specified in the filter criteria.
         * This should be used to render a portion of a large network.
         */
        private val FILTER_CRITERIA = FilterCriteria(mutableSetOf(""))
    }
}
