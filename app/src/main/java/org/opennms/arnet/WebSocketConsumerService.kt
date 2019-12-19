package org.opennms.arnet

import android.util.Log
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.util.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class WebSocketConsumerService : ConsumerService {

    private var client: WebSocketClient = WebSocketClient(URI(WEB_SOCKET_SERVER))

    private val consumers = CopyOnWriteArrayList<Consumer>()

    private val mapper = jacksonObjectMapper()

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
            send(mapper.writeValueAsString(StreamRequest(RequestAction.SUBSCRIBE, FILTER_CRITERIA)))
        }

        override fun onMessage(message: String) {
            Log.d(TAG, "message: $message")

            val response : StreamMessage = mapper.readValue(message, StreamMessage::class.java)

            when(response.type) {
                MessageType.Alarm -> processAlarm(response)
                MessageType.Edge -> processEdge(response)
                MessageType.Event -> processEvent(response)
                MessageType.Topology -> processTopology(response)
                MessageType.Node -> processNode(response)
                else -> Log.e(TAG, "Unsupported message type '${response.type}'")
            }
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

    fun processAlarm(response: StreamMessage) {
        val alarm = mapper.convertValue<Alarm>(response.payload)
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

    fun processEdge(response: StreamMessage) {
        val edge = mapper.convertValue<TopologyEdge>(response.payload)
        Log.i(TAG, "Processing edge ${edge.id}")
        val ve = convertTopologyEdge(edge)

        consumers.forEach { consumer ->
            try {
                if (vertices.containsKey(ve.vertices.src.id)) {
                    Log.d(TAG, "Update to vertex ${ve.vertices.src.id}")
                    // TODO: currently not supported...
                } else {
                    Log.d(TAG, "New vertex ${ve.vertices.src.id}")
                    vertices.put(ve.vertices.src.id, ve.vertices.src)
                    consumer.acceptVertex(ve.vertices.src)
                }

                if (vertices.containsKey(ve.vertices.dst.id)) {
                    Log.d(TAG, "Update to vertex ${ve.vertices.dst.id}")
                    // TODO: currently not supported...
                } else {
                    Log.d(TAG, "New vertex ${ve.vertices.dst.id}")
                    vertices.put(ve.vertices.dst.id, ve.vertices.dst)
                    consumer.acceptVertex(ve.vertices.dst)
                }

                if (edges.containsKey(ve.edge.id)) {
                    Log.d(TAG, "Update to edge ${ve.edge.id}")
                    // TODO: currently not supported...
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

    fun processEvent(response: StreamMessage) {
        val event = mapper.convertValue<InMemoryEvent>(response.payload)
        Log.i(TAG, "Processing event ${event.uei}")
        consumers.forEach {
            try {
                it.acceptEvent(convertEvent(event))
            } catch (e: Error) {
                Log.e(TAG, "Consumer unable to process event ${event.uei} : $e")
            }
        }
    }

    fun processTopology(response: StreamMessage) {
        Log.i(TAG, "Processing topology")
        val topology = mapper.convertValue<Topology>(response.payload)
        val graph: Graph<Vertex, Edge> = SparseMultigraph()

        edges.clear()
        vertices.clear()

        // Handles node in Topology
        // Nodes appear directly in Topology and are contained in TopologyEdge - reason being: a
        // node can "dangle" (e.g. no edges)
        topology.nodes
            ?.map { n -> convertNode(n) }
            ?.map { it.id to it }
            ?.toMap()
            ?.toMutableMap()
            ?.let { nodesDirect -> vertices.putAll(nodesDirect) }

        // Handles node, port, and segment in each TopologyEdge
        // Handles edges
        topology.edges?.forEach {
            val ve = convertTopologyEdge(it)
            edges.put(ve.edge.id, ve.edge)
            vertices.put(ve.vertices.src.id, ve.vertices.src)
            vertices.put(ve.vertices.dst.id, ve.vertices.dst)
        }

        // Add vertices to graph
        vertices.values.forEach { graph.addVertex(it) }

        // Add edges to graph
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

    fun processNode(response: StreamMessage) {
        val node = mapper.convertValue<Node>(response.payload)
        Log.i(TAG, "Processing node ${node.id}")
        val convNode = convertNode(node)
        consumers.forEach {
            try {
                if (vertices.containsKey(convNode.id)) {
                    Log.d(TAG, "Update to node ${convNode.id}")
                    // TODO: currently not supported...
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

        val edge = object : Edge {
            override fun getId(): String {
                return topologyEdge.id + "-" + topologyEdge.protocol.name
            }

            override fun getSourceVertex(): Vertex {
                  return srcVertex
            }

            override fun getTargetVertex(): Vertex {
                return dstVertex
            }

            override fun getProtocol(): String {
                return topologyEdge.protocol.name
            }
        }

        return EdgeVertex(edge, VertexPair(srcVertex, dstVertex))
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
        return object : Vertex {
            override fun getId(): String {
                return node.id.toString()
            }

            override fun getLabel(): String {
                return node.label
            }

            override fun getType(): Vertex.Type {
                return Vertex.Type.Node
            }
        }
    }

    fun convertPort(port: TopologyPort) : Vertex {
        return object : Vertex {
            override fun getId(): String {
                return port.id.toString()
            }

            override fun getLabel(): String {
                // TODO: can this be generated?
                return ""
            }

            override fun getType(): Vertex.Type {
                return Vertex.Type.Port
            }
        }
    }

    fun convertSegment(segment: TopologySegment) : Vertex {
        return object : Vertex {
            override fun getId(): String {
                return segment.id.toString()
            }

            override fun getLabel(): String {
                // TODO: can this be generated?
                return ""
            }

            override fun getType(): Vertex.Type {
                return Vertex.Type.Segment
            }
        }
    }

    companion object {

        private val TAG = "WebSocketConsumerService"

        /**
         * The WebSocket server IP and port.
         * Remember to update this accordingly!
         */
        private val WEB_SOCKET_SERVER = "ws://172.20.50.148:8080"

        /**
         * The (optional) filter criteria used for subscribing to ARNet streaming service.
         * Location(s) can be specified in the filter criteria.
         * This should be used to render a portion of a large network.
         */
        private val FILTER_CRITERIA = FilterCriteria(mutableSetOf(""))
    }
}
