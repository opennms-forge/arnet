package org.opennms.arnet

import android.util.Log
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.collect.Maps
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph

import org.java_websocket.handshake.ServerHandshake
import org.opennms.arnet.api.Consumer
import org.opennms.arnet.api.ConsumerService
import org.opennms.arnet.api.model.Edge
import org.opennms.arnet.api.model.Vertex
import org.opennms.integration.api.v1.model.*
import org.opennms.oia.streaming.model.*
import java.io.PrintWriter
import java.io.StringWriter

import java.net.URI
import java.nio.ByteBuffer
import java.util.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class WebSocketConsumerService : ConsumerService {

    private var client: WebSocketClient = WebSocketClient(URI(WEB_SOCKET_SERVER))

    private val consumers = CopyOnWriteArrayList<Consumer>()

    private val mapper = jacksonObjectMapper()

    private val oiaMapper = oiaDeserializer

    private val vertices = mutableMapOf<String, Vertex>()

    private val edges = mutableMapOf<String, Edge>()

    override fun accept(consumer: Consumer) {
        Log.i(TAG, "Adding consumer.")
        consumers.add(consumer)
    }

    override fun dismiss(consumer: Consumer?) {
        Log.i(TAG, "Removing consumer.")
        consumers.remove(consumer)
    }

    override fun start() {
        Log.i(TAG, "Attempting to connect...")

        // TODO: this would be better with a retry mechanism...
        try {
            client.connectBlocking(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while attempting to connect")
        }

        if (client.isOpen) {
            Log.i(TAG, "Connected.")
        } else {
            Log.e(TAG, "Not Connected!")
        }
    }

    override fun stop() {
        Log.i(TAG, "Disconnecting...")

        if (client.isOpen) {
            Log.i(TAG, "Closing client.")
            client.close()
        }
    }

    inner class WebSocketClient(serverURI: URI) :
        org.java_websocket.client.WebSocketClient(serverURI) {

        override fun onOpen(handshakedata: ServerHandshake) {
            Log.i(TAG, "open: status '${handshakedata.httpStatus}'")
            send(mapper.writeValueAsString(StreamRequest(RequestAction.SUBSCRIBE/*, FILTER_CRITERIA*/)))
        }

        override fun onMessage(message: String) {
            Log.i(TAG, "message: $message")

            val response = mapper.readValue<StreamMessage>(message)

            when(response.type) {
                MessageType.Alarm -> processAlarm(response)
                MessageType.AlarmDelete -> processAlarmDelete(response)
                MessageType.Edge -> processEdge(response)
                MessageType.EdgeDelete -> processEdgeDelete(response)
                MessageType.Event -> processEvent(response)
                MessageType.Topology -> processTopology(response)
                MessageType.Node -> processNode(response)
                else -> Log.e(TAG, "Unsupported message type '${response.type}'")
            }
        }

        override fun onMessage(bytes: ByteBuffer?) {
            requireNotNull(bytes)
            onMessage(String(bytes.array()))
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            Log.i(TAG, "close: code: '$code', reason '$reason', remote: '$remote'.")
        }

        override fun onError(ex: Exception) {
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            Log.e(TAG, "error: $sw")
        }
    }

    fun processAlarm(message: StreamMessage) {
        val alarm = oiaMapper.convertValue<Alarm>(message.payload)
        Log.i(TAG, "Processing alarm ${alarm.reductionKey}")
        consumers.forEach { c ->
            try {
                if (alarm.isSituation) {
                    c.acceptSituation(convertSituation(alarm))
                } else {
                    c.acceptAlarm(convertAlarm(alarm))
                }
            } catch (er: Error) {
                Log.e(TAG, "Consumer unable to process alarm ${alarm.reductionKey} : $er")
            }
        }
    }

    fun processAlarmDelete(message: StreamMessage) {
        val alarm = oiaMapper.convertValue<AlarmDelete>(message.payload)
        Log.i(TAG, "Processing alarm deletion ${alarm.reductionKey}")
        consumers.forEach { c ->
            try {
                if (alarm.isSituation) {
                    c.acceptDeleteSituation(alarm.reductionKey)
                } else {
                    c.acceptDeletedAlarm(alarm.reductionKey)
                }
            } catch (er: Error) {
                Log.e(TAG, "Consumer unable to process alarm delete ${alarm.reductionKey} : $er")
            }
        }
    }

    fun processEdge(message: StreamMessage) {
        val edge = oiaMapper.convertValue<TopologyEdge>(message.payload)
        Log.i(TAG, "Processing edge ${edge.id}")
        val ve = convertTopologyEdge(edge)

        consumers.forEach { consumer ->
            try {
                if (vertices.containsKey(ve.vertices.src.id)) {
                    Log.d(TAG, "Update to vertex ${ve.vertices.src.id}")
                    // TODO: is this supported?
                } else {
                    Log.d(TAG, "New vertex ${ve.vertices.src.id}")
                    vertices.put(ve.vertices.src.id, ve.vertices.src)
                    consumer.acceptVertex(ve.vertices.src)
                }

                if (vertices.containsKey(ve.vertices.dst.id)) {
                    Log.d(TAG, "Update to vertex ${ve.vertices.dst.id}")
                    // TODO: is this supported?
                } else {
                    Log.d(TAG, "New vertex ${ve.vertices.dst.id}")
                    vertices.put(ve.vertices.dst.id, ve.vertices.dst)
                    consumer.acceptVertex(ve.vertices.dst)
                }

                if (edges.containsKey(ve.edge.id)) {
                    Log.d(TAG, "Update to edge ${ve.edge.id}")
                    // TODO: is this supported?
                } else {
                    Log.d(TAG, "New edge ${ve.edge.id}")
                    edges.put(ve.edge.id, ve.edge)
                    consumer.acceptEdge(ve.edge)
                }
            } catch (er : Error) {
                Log.e(TAG, "Consumer unable to process edge ${edge.id} : $er")
            }
        }
    }

    fun processEdgeDelete(message: StreamMessage) {
        val edge = oiaMapper.convertValue<TopologyEdge>(message.payload)
        Log.i(TAG, "Processing edge delete ${edge.id}")

        consumers.forEach { consumer ->
            try {
                if (!edges.containsKey(edge.id)) {
                    Log.d(TAG, "Edge ${edge.id} does not exist")
                } else {
                    Log.i(TAG, "Deleting edge ${edge.id}")
                    edges.remove(edge.id)
                    consumer.acceptDeletedEdge(edge.id)
                }
            } catch (er : Error) {
                Log.e(TAG, "Consumer unable to process edge delete ${edge.id} : $er")
            }
        }
    }

    fun processEvent(message: StreamMessage) {
        val event = oiaMapper.convertValue<InMemoryEvent>(message.payload)
        Log.i(TAG, "Processing event ${event.uei}")
        consumers.forEach {
            try {
                it.acceptEvent(convertEvent(event))
            } catch (e: Error) {
                Log.e(TAG, "Consumer unable to process event ${event.uei} : $e")
            }
        }
    }

    fun processTopology(message: StreamMessage) {
        Log.i(TAG, "Processing topology")
        val topology = oiaMapper.convertValue<Topology>(message.payload)
        val graph: Graph<Vertex, Edge> = SparseMultigraph()

        edges.clear()
        vertices.clear()

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

        Log.i(TAG, "Graph contains ${graph.vertexCount} vertices " +
                "and ${graph.edgeCount} edges. There are ${topology.alarms?.size} alarms " +
                "and situations")

        consumers.forEach {
            try {
                it.accept(graph,
                    topology.alarms?.filter { !it.isSituation }?.map { convertAlarm(it) },
                    topology.alarms?.filter { it.isSituation }?.map { convertSituation(it) })
            } catch (e: Error) {
                Log.e(TAG, "Consumer unable to process topology : $e")
            }
        }
    }

    fun processNode(message: StreamMessage) {
        val node = oiaMapper.convertValue<Node>(message.payload)
        Log.i(TAG, "Processing node ${node.id}")
        val convNode = convertNode(node)
        consumers.forEach {
            try {
                if (vertices.containsKey(convNode.id)) {
                    Log.d(TAG, "Update to node ${convNode.id}")
                    // TODO: is this supported?
                } else {
                    Log.d(TAG, "New vertex ${convNode.id}")
                    vertices.put(convNode.id, convNode)
                    it.acceptVertex(convNode)
                }
            } catch (e: Error) {
                Log.e(TAG, "Consumer unable to process node ${node.id} : $e")
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


    fun convertAlarm(alarm: Alarm) : org.opennms.arnet.api.model.Alarm {
        return object : org.opennms.arnet.api.model.Alarm {

            override fun getReductionKey(): String {
                return alarm.reductionKey
            }

            override fun getSeverity(): org.opennms.arnet.api.model.Alarm.Severity {
                return org.opennms.arnet.api.model.Alarm.Severity.valueOf(
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
    }

    fun convertEvent(event: InMemoryEvent) : org.opennms.arnet.api.model.Event {
        return object : org.opennms.arnet.api.model.Event {
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
    }

    fun convertSituation(situation: Alarm) : org.opennms.arnet.api.model.Situation {
        return object : org.opennms.arnet.api.model.Situation {
            override fun getReductionKey(): String {
                return situation.reductionKey
            }

            override fun getSeverity(): org.opennms.arnet.api.model.Alarm.Severity {
                return org.opennms.arnet.api.model.Alarm.Severity.valueOf(
                    situation.severity.name)
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

            override fun getRelatedAlarms(): MutableSet<org.opennms.arnet.api.model.Alarm> {
                return situation.relatedAlarms.map { a -> convertAlarm(a) }.toMutableSet()
            }
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

    companion object {

        private val TAG = "WebSocketConsumerService"

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
