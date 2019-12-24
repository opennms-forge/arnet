package org.opennms.oia.streaming.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.collect.Maps
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph
import org.java_websocket.client.WebSocketClient
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class WebSocketConsumerService(websocketUri: String) : ConsumerService {

    private val client: WebSocketClient = StreamerClient(URI(websocketUri))

    private val consumers = CopyOnWriteArrayList<Consumer>()

    private val mapper = jacksonObjectMapper()

    private val vertices = mutableMapOf<String, Vertex>()

    private val edges = mutableMapOf<String, Edge>()

    private val log = LoggerFactory.getLogger(WebSocketConsumerService::class.java)

    private var graph: Graph<Vertex, Edge> = SparseMultigraph()

    private val initialAlarms = mutableListOf<org.opennms.oia.streaming.client.api.model.Alarm>()

    private val initialSituations = mutableListOf<Situation>()

    private val initialized = AtomicBoolean(false)

    private val cacheLock = ReentrantLock()

    override fun accept(consumer: Consumer) {
        log.info("Adding consumer.")
        consumers.add(consumer)

        // TODO: a "refresh topology" operation is needed to guarantee that up-to-date information
        //  is provided to the consumer.  As the code is now, (potentially) stale data is provided.
        //  E.g. the data is "fresh" at the time of server connect, but likely not at the time
        //  the consumer connects, which can be quite some time later...
        if (initialized.get()) {
            consumer.accept(graph, initialAlarms, initialSituations)
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

            // TODO: an error occurs on the server when unsubscribing...
//            log.info("Sending unsubscribe.")
//            client.send(mapper.writeValueAsString(unsubscribeRequest()))

            log.info("Closing client.")
            client.close()
        }
    }

    inner class StreamerClient(serverURI: URI) :
        org.java_websocket.client.WebSocketClient(serverURI) {

        override fun onOpen(handshakedata: ServerHandshake) {
            log.info("open: status '${handshakedata.httpStatus}'")
            client.send(mapper.writeValueAsString(subscribeRequest()))
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

        cacheLock.withLock {
            // TODO: support edge update?
            if (edges.containsKey(ve.edge.id)) {
                log.debug("Update to edge ${ve.edge.id}")
                return
            }

            log.info("New edge ${ve.edge.id}")
            edges[ve.edge.id] = ve.edge

            // TODO: support vertex update?
            if (!vertices.containsKey(ve.vertices.src.id)) {
                vertices[ve.vertices.src.id] = ve.vertices.src
            }
            if (!vertices.containsKey(ve.vertices.dst.id)) {
                vertices[ve.vertices.dst.id] = ve.vertices.dst
            }
        }

        consumers.forEach {
            try {
                it.acceptEdge(ve.edge)
            } catch (er : Error) {
                log.warn("Consumer unable to process edge ${edge.id} : $er")
            }
        }
    }

    fun processEdgeDelete(message: StreamMessage) {
        val edge = message.deserializePayload<TopologyEdge>()
        log.info("Processing edge delete ${edge.id}")
        val ve = convertTopologyEdge(edge)

        cacheLock.withLock {
            if (!edges.containsKey(ve.edge.id)) {
                log.debug("Edge ${ve.edge.id} does not exist")
                return
            }

            log.info("Deleting edge ${ve.edge.id}")
            edges.remove(ve.edge.id)
        }

        consumers.forEach {
            try {
                it.acceptDeletedEdge(ve.edge.id)
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

        cacheLock.withLock {
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
                edges[ve.edge.id] = ve.edge
                if (!verticesTopologyEdge.containsKey(ve.vertices.src.id)) {
                    verticesTopologyEdge[ve.vertices.src.id] = ve.vertices.src
                }
                if (!verticesTopologyEdge.containsKey(ve.vertices.dst.id)) {
                    verticesTopologyEdge[ve.vertices.dst.id] = ve.vertices.dst
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

            log.info("Graph contains ${graph.vertexCount} vertices, " +
                    "${graph.edgeCount} edges, ${alarms?.size} alarms, " +
                    "and ${situations?.size} situations")

            consumers.forEach {
                try {
                    it.accept(graph, initialAlarms, initialSituations)
                } catch (e: Error) {
                    log.warn("Consumer unable to process topology : $e")
                }
            }
        }

        initialized.set(true)
    }

    fun processNode(message: StreamMessage) {
        val node = message.deserializePayload<Node>()
        log.info("Processing node ${node.id}")
        val convNode = convertNode(node)

        cacheLock.withLock {
            // TODO: support node update?
            if (vertices.containsKey(convNode.id)) {
                log.debug("Update to node ${convNode.id}")
                return
            }

            log.info("New vertex ${convNode.id}")
            vertices.put(convNode.id, convNode)
        }

        consumers.forEach {
            try {
                it.acceptVertex(convNode)
            } catch (e: Error) {
                log.warn("Consumer unable to process node ${node.id} : $e")
            }
        }
    }

    private fun convertTopologyEdge(topologyEdge : TopologyEdge) : EdgeVertex {

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

    private fun convertAlarm(alarm: Alarm) = object : org.opennms.oia.streaming.client.api.model.Alarm {
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

    private fun convertEvent(event: InMemoryEvent) = object : org.opennms.oia.streaming.client.api.model.Event {
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

    private fun convertSituation(situation: Alarm) = object : Situation {
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

    private fun convertNode(node: Node) : Vertex {
        return VertexImpl(id = node.id.toString(), label = node.label, type = Vertex.Type.Node)
    }

    private fun convertPort(port: TopologyPort) : Vertex {
        // TODO: can "label" be generated?
        return VertexImpl(id = port.id.toString(), label = "", type = Vertex.Type.Port)
    }

    private fun convertSegment(segment: TopologySegment) : Vertex {
        // TODO: can "label" be generated?
        return VertexImpl(id = segment.id.toString(), label = "", type = Vertex.Type.Segment)
    }

    // For unit testing...
    protected fun numEdges() : Int {
        return edges.size
    }

    // For unit testing...
    protected fun numVertices() : Int {
        return vertices.size
    }
    
    private companion object {
        /**
         * The (optional) filter criteria used for subscribing to ARNet streaming service.
         * Location(s) can be specified in the filter criteria.
         * This should be used to render a portion of a large network.
         */
        private val FILTER_CRITERIA = FilterCriteria(mutableSetOf(""))
    }
}
