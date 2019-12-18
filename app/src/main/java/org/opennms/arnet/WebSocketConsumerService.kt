package org.opennms.arnet

import android.util.Log
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph

import org.java_websocket.handshake.ServerHandshake
import org.opennms.arnet.api.Consumer
import org.opennms.arnet.api.ConsumerService
import org.opennms.arnet.model.*
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
            send(mapper.writeValueAsString(ArnetRequest(RequestAction.SUBSCRIBE, FILTER_CRITERIA)))
        }

        override fun onMessage(message: String) {
            Log.d(TAG, "message: $message")

            val response : ArnetResponse = mapper.readValue(message,
                ArnetResponse::class.java)

            when(response.type) {
                ResponseType.Alarm -> processAlarm(response)
                ResponseType.Edge -> processEdge(response)
                ResponseType.Event -> processEvent(response)
                ResponseType.Topology -> processTopology(response)
                ResponseType.Situation -> processSituation(response)
                ResponseType.Vertex -> processVertex(response)
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

        fun processAlarm(response: ArnetResponse) {
            val alarm = mapper.convertValue<Alarm>(response.payload)
            Log.i(TAG, "Processing alarm ${alarm.reductionKey}")
            consumers.forEach { c ->
                try {
                    c.acceptAlarm(convertAlarm(alarm))
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process alarm ${alarm.reductionKey} : $e")
                }
            }
        }

        fun processEdge(response: ArnetResponse) {
            val edge = mapper.convertValue<Edge>(response.payload)
            Log.i(TAG, "Processing edge ${edge.id}")
            consumers.forEach { c ->
                try {
                    c.acceptEdge(convertEdge(edge))
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process edge ${edge.id} : $e")
                }
            }
        }

        fun processEvent(response: ArnetResponse) {
            val event = mapper.convertValue<Event>(response.payload)
            Log.i(TAG, "Processing event ${event.UEI}")
            consumers.forEach { c ->
                try {
                    c.acceptEvent(convertEvent(event))
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process event ${event.UEI} : $e")
                }
            }
        }

        fun processTopology(response: ArnetResponse) {
            Log.i(TAG, "Processing topology")
            val topology = mapper.convertValue<Topology>(response.payload)

            val graph: Graph<org.opennms.arnet.api.model.Vertex,
                    org.opennms.arnet.api.model.Edge> = SparseMultigraph()

            Log.i(TAG, "Processing ${topology.vertices?.size} vertices, " +
                    "${topology.edges?.size} edges, ${topology.alarms?.size} alarms, " +
                    "and ${topology.situations?.size} situations")

            topology.vertices?.forEach { v -> graph.addVertex(convertVertex(v)) }
            topology.edges?.forEach { e ->
                val em = convertEdge(e)
                graph.addEdge(em, em.sourceVertex, em.targetVertex)
            }

            consumers.forEach { c ->
                try {
                    c.accept(graph,
                        topology.alarms?.map { a -> convertAlarm(a) },
                        topology.situations?.map { s -> convertSituation(s) })
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process topology : $e")
                }
            }
        }

        fun processSituation(response: ArnetResponse) {
            val situation = mapper.convertValue<Situation>(response.payload)
            Log.i(TAG, "Processing situation ${situation.alarm.reductionKey}")
            consumers.forEach { c ->
                try {
                    c.acceptSituation(convertSituation(situation))
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process situation " +
                                "${situation.alarm.reductionKey} : $e")
                }
            }
        }

        fun processVertex(response: ArnetResponse) {
            val vertex = mapper.convertValue<Vertex>(response.payload)
            Log.i(TAG, "Processing vertex ${vertex.id}")
            consumers.forEach { c ->
                try {
                    c.acceptVertex(convertVertex(vertex))
                } catch (e: Error) {
                    Log.e(TAG, "Consumer unable to process vertex ${vertex.id} : $e")
                }
            }
        }

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
                    return alarm.lastUpdated
                }

                override fun getVertexId(): String {
                    return alarm.vertexId
                }
            }
        }

        fun convertEdge(edge: Edge) : org.opennms.arnet.api.model.Edge {
            return object : org.opennms.arnet.api.model.Edge {
                override fun getId(): String {
                    return edge.id
                }

                override fun getSourceVertex(): org.opennms.arnet.api.model.Vertex {
                    return convertVertex(edge.sourceVertex)
                }

                override fun getTargetVertex(): org.opennms.arnet.api.model.Vertex {
                    return convertVertex(edge.targetVertex)
                }

                override fun getProtocol(): String {
                    return edge.protocol
                }
            }
        }

        fun convertEvent(event: Event) : org.opennms.arnet.api.model.Event {
            return object : org.opennms.arnet.api.model.Event {
                override fun getUEI(): String {
                    return event.UEI
                }

                override fun getDescription(): String {
                    return event.description
                }

                override fun getTime(): Date {
                    return event.time
                }

                override fun getVertexId(): String {
                    return event.vertexId
                }
            }
        }

        fun convertSituation(situation: Situation) : org.opennms.arnet.api.model.Situation {
            return object : org.opennms.arnet.api.model.Situation {
                override fun getReductionKey(): String {
                    return situation.alarm.reductionKey
                }

                override fun getSeverity(): org.opennms.arnet.api.model.Alarm.Severity {
                    return org.opennms.arnet.api.model.Alarm.Severity.valueOf(
                        situation.alarm.severity.name)
                }

                override fun getDescription(): String {
                    return situation.alarm.description
                }

                override fun getLastUpdated(): Date {
                    return situation.alarm.lastUpdated
                }

                override fun getVertexId(): String {
                    return situation.alarm.vertexId
                }

                override fun getRelatedAlarms(): MutableSet<org.opennms.arnet.api.model.Alarm> {
                    return situation.relatedAlarms.map { a -> convertAlarm(a) }.toMutableSet()
                }
            }
        }

        fun convertVertex(vertex: Vertex) : org.opennms.arnet.api.model.Vertex {
            return object : org.opennms.arnet.api.model.Vertex {
                override fun getId(): String {
                    return vertex.id
                }

                override fun getLabel(): String {
                    return vertex.label
                }

                override fun getType(): org.opennms.arnet.api.model.Vertex.Type {
                    return org.opennms.arnet.api.model.Vertex.Type.valueOf(vertex.type.name)
                }
            }
        }
    }

    companion object {

        private val TAG = "WebSocketConsumerService"

        /**
         * TODO: update this accordingly!
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
