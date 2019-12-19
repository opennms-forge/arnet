package org.opennms.oia.streaming

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.opennms.integration.api.v1.alarms.AlarmLifecycleListener
import org.opennms.integration.api.v1.dao.AlarmDao
import org.opennms.integration.api.v1.dao.EdgeDao
import org.opennms.integration.api.v1.dao.NodeDao
import org.opennms.integration.api.v1.events.EventListener
import org.opennms.integration.api.v1.events.EventSubscriptionService
import org.opennms.integration.api.v1.model.*
import org.opennms.integration.api.v1.topology.TopologyEdgeConsumer
import org.opennms.oia.streaming.model.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class OiaWebSocketServer(
    port: Int,
    private val alarmDao: AlarmDao,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val eventSubscriptionService: EventSubscriptionService
) : WebSocketServer(InetSocketAddress(port)), AlarmLifecycleListener, TopologyEdgeConsumer, EventListener {

    private val log = LoggerFactory.getLogger(OiaWebSocketServer::class.java)

    private val mapper = jacksonObjectMapper()
    private val subscribers = ConcurrentHashMap<WebSocket, FilterCriteria>()
    private val nodeIdsBySession = ConcurrentHashMap<WebSocket, MutableSet<Int>?>()

    private val nodeCache = ConcurrentHashMap<Int, Node>()
    private val alarmCache = ConcurrentHashMap<Int, Alarm>()

    private val alarmLock = ReentrantLock()
    private val nodeLock = ReentrantLock()

    fun init() {
        eventSubscriptionService.addEventListener(this)
        start()
    }

    fun destroy() {
        eventSubscriptionService.removeEventListener(this)
        stop()
    }

    private fun subscribeConnection(conn: WebSocket, filterCriteria: FilterCriteria) {
        require(subscribers[conn] == null)
        log.info("Received subscribe request from connection '$conn' with criteria '$filterCriteria'")
        subscribers[conn] = filterCriteria

        // On subscribe send over the initial topology
        conn.send(generateTopology(filterCriteria))
    }

    private fun unsubscribeConnection(conn: WebSocket) {
        requireNotNull(subscribers[conn])
        log.info("Received unsubscribe request from connection '$conn'")
        subscribers.remove(conn)
    }

    private fun generateTopology(filterCriteria: FilterCriteria): ByteArray {
        val alarms = alarmDao.alarms
            .filter { filterAlarm(it, filterCriteria) }
            .let { if (it.isNotEmpty()) it.toSet() else null }
        val nodes = nodeDao.nodes
            .filter { filterNode(it, filterCriteria) }
            .let { if (it.isNotEmpty()) it.toSet() else null }
        val edges = edgeDao.edges
            .filter { filterEdge(it, filterCriteria) }
            .let { if (it.isNotEmpty()) it.toSet() else null }

        return mapper.writeValueAsBytes(topologyMessage(Topology(alarms = alarms, nodes = nodes, edges = edges)))
    }

    private fun generateAlarmReceivers(alarm: Alarm): Set<WebSocket>? {
        val receivers = generateReceivers(alarm.node.location)
        log.trace("Receivers for alarm '$alarm' are '$receivers'")

        return receivers
    }

    private fun generateNodeReceivers(node: Node): Set<WebSocket>? {
        val receivers = generateReceivers(node.location)?.filter {
            return@filter nodeIdsBySession[it]?.let { nodes ->
                !nodes.contains(node.id)
            } ?: true
        }
            ?.toSet()

        log.trace("Receivers for node '$node' are '$receivers'")

        return receivers
    }

    private fun generateEdgeReceivers(edge: TopologyEdge, nodeCallback: ((Node?, Node?) -> Unit)?): Set<WebSocket>? {
        var sourceNode: Node? = null
        var targetNode: Node? = null
        val edgeVisitor = object : TopologyEdge.EndpointVisitor {
            override fun visitSource(node: Node) {
                sourceNode = node
            }

            override fun visitTarget(node: Node) {
                targetNode = node
            }
        }

        edge.visitEndpoints(edgeVisitor)
        nodeCallback?.invoke(sourceNode, targetNode)

        val receivers = if (sourceNode == null || targetNode == null) null else {
            subscribers
                .filter { filterEdge(edge, it.value) }
                .map { it.key }
                .let { if (it.isNotEmpty()) it else null }
                ?.toSet()
        }

        log.trace("Receivers for edge '$edge' are '$receivers'")

        return receivers
    }

    private fun generateReceivers(location: String): Set<WebSocket>? {
        val receivers = subscribers.filter { it.value.locations?.contains(location) ?: true }
            .map { it.key }
            .toSet()

        return if (receivers.isNotEmpty()) receivers else null
    }

    private fun handleNode(node: Node) {
        nodeLock.withLock {
            generateNodeReceivers(node)?.let { receivers ->
                nodeCache[node.id] = node
                broadcast(mapper.writeValueAsBytes(nodeMessage(node)), receivers)

                // Record that these receivers have seen this node
                receivers.forEach {
                    nodeIdsBySession.compute(it) { _, nodeIds -> (nodeIds ?: mutableSetOf()).apply { add(node.id) } }
                }
            }
        }
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        requireNotNull(conn)
        requireNotNull(handshake)

        log.info("Received websocket open on connection '$conn'")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        requireNotNull(conn)
        requireNotNull(reason)

        log.info("Received websocket close on connection '$conn'")
        unsubscribeConnection(conn)
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        requireNotNull(conn)
        requireNotNull(message)

        log.info("Received websocket message '$message' on connection '$conn'")
        val request = mapper.readValue<StreamRequest>(message)

        when (request.action) {
            RequestAction.SUBSCRIBE -> subscribeConnection(conn, request.criteria ?: FilterCriteria())
            RequestAction.UNSUBSCRIBE -> unsubscribeConnection(conn)
        }
    }

    override fun onStart() {
        log.info("Websocket server started on port '$port'")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        log.warn("Error starting websocket server", ex)
    }

    override fun handleDeletedAlarm(alarmId: Int, reductionKey: String?) {
        requireNotNull(reductionKey)

        log.trace("Received deleted alarm with reduction key '$reductionKey'")

        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for alarm")
            return
        }

        alarmLock.withLock {
            alarmCache[alarmId]?.let { alarm ->
                generateAlarmReceivers(alarm)?.let { receivers ->
                    log.debug("Broadcasting alarm delete for reduction key '$reductionKey' to receivers '$receivers'")
                    broadcast(mapper.writeValueAsBytes(alarmDeleteMessage(reductionKey)), receivers)
                }
            }

            alarmCache.remove(alarmId)
        }
    }

    override fun handleAlarmSnapshot(alarms: MutableList<Alarm>?) {
        requireNotNull(alarms)

        alarmLock.withLock {
            val existingAlarms = alarmCache.values.toSet()
            val snapshotAlarms = alarms.toSet()

            (snapshotAlarms - existingAlarms).forEach { handleNewOrUpdatedAlarm(it) }
            (existingAlarms - snapshotAlarms).forEach { handleDeletedAlarm(it.id, it.reductionKey) }

            alarmCache.clear()
            alarmCache.putAll(alarms.map { it.id to it })
        }
    }

    override fun handleNewOrUpdatedAlarm(alarm: Alarm?) {
        requireNotNull(alarm)

        log.trace("Received new or updated alarm '$alarm'")

        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for alarm")
            return
        }

        handleNode(alarm.node)

        alarmLock.withLock {
            alarmCache[alarm.id] = alarm
            generateAlarmReceivers(alarm)?.let { receivers ->
                log.debug("Broadcasting alarm '$alarm' to receivers '$receivers'")
                broadcast(mapper.writeValueAsBytes(alarmMessage(alarm)), receivers)
            }
        }
    }

    override fun onEdgeDeleted(topologyEdge: TopologyEdge?) {
        requireNotNull(topologyEdge)

        log.trace("Received deleted edge '$topologyEdge'")

        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for edge delete")
            return
        }

        generateEdgeReceivers(topologyEdge, null)?.let { receivers ->
            log.debug("Broadcasting edge delete '$topologyEdge' to receivers '$receivers'")
            broadcast(mapper.writeValueAsBytes(edgeDeleteMessage(topologyEdge)), receivers)
        }
    }

    override fun getProtocols(): MutableSet<TopologyProtocol> = Collections.singleton(TopologyProtocol.ALL)

    override fun onEdgeAddedOrUpdated(topologyEdge: TopologyEdge?) {
        requireNotNull(topologyEdge)

        log.trace("Received new or updated edge '$topologyEdge'")

        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for edge")
            return
        }

        generateEdgeReceivers(topologyEdge) { sourceNode, targetNode ->
            sourceNode?.let { handleNode(it) }
            targetNode?.let { handleNode(it) }
        }
            ?.let { receivers ->
                log.debug("Broadcasting edge '$topologyEdge' to receivers '$receivers'")
                broadcast(mapper.writeValueAsBytes(edgeMessage(topologyEdge)), receivers)
            }
    }

    override fun getName() = "OiaWebSocketServer"

    override fun getNumThreads() = 1

    override fun onEvent(event: InMemoryEvent?) {
        log.trace("Received new event '$event'")

        if (event?.nodeId == null) {
            log.trace("Ignoring event that doesn't match a node")
            return
        }

        nodeCache[event.nodeId]?.let { eventNode ->
            generateNodeReceivers(eventNode)?.let { receivers ->
                log.debug("Broadcasting event '$event' to receivers '$receivers'")
                broadcast(mapper.writeValueAsBytes(eventMessage(event)), receivers)
            }
        }
    }
}