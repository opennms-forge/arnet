package org.opennms.oia.streaming

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.opennms.integration.api.v1.alarms.AlarmLifecycleListener
import org.opennms.integration.api.v1.dao.AlarmDao
import org.opennms.integration.api.v1.model.Alarm
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm
import org.opennms.oia.streaming.model.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class OiaWebSocketServer(
    port: Int,
    private val alarmDao: AlarmDao
) : WebSocketServer(InetSocketAddress(port)), AlarmLifecycleListener {

    private val mapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(OiaWebSocketServer::class.java)
    private val subscribers = ConcurrentHashMap<WebSocket, FilterCriteria?>()

    fun init() {
        start()
    }

    fun destroy() {
        stop()
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        log.info("Received websocket open on connection '$conn'")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        log.info("Received websocket close on connection '$conn'")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        log.info("Received websocket message '$message' on connection '$conn'")
        val request = mapper.readValue<StreamRequest>(message)

        when (request.action) {
            RequestAction.SUBSCRIBE -> subscribeConnection(conn, request.criteria)
            RequestAction.UNSUBSCRIBE -> unsubscribeConnection(conn)
        }
    }

    private fun subscribeConnection(conn: WebSocket, filterCriteria: FilterCriteria?) {
        require(subscribers[conn] == null)
        log.info("Received subscribe request from connection '$conn' with criteria '$filterCriteria'")
        subscribers[conn] = filterCriteria
        conn.send(generateTopology(filterCriteria))
    }

    private fun unsubscribeConnection(conn: WebSocket) {
        requireNotNull(subscribers[conn])
        log.info("Received unsubscribe request from connection '$conn'")
        subscribers.remove(conn)
    }

    private fun generateTopology(filterCriteria: FilterCriteria?): ByteArray {
        val alarms = alarmDao.alarms
            ?.filter { if (filterCriteria?.locations == null) true else filterCriteria.locations!!.contains(it.node.location) }
            ?.let { if (it.isNotEmpty()) it.toSet() else null }
        return mapper.writeValueAsBytes(topologyMessage(Topology(alarms = alarms)))
    }

    override fun onStart() {
        log.info("Websocket server started on port '$port'")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        log.warn("Error starting websocket server", ex)
    }

    override fun handleDeletedAlarm(alarmId: Int, reductionKey: String) {
        log.trace("Received deleted alarm with reduction key '$reductionKey'")
        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for alarm")
            return
        }
        
//        alarmDao.
        val alarm = ImmutableAlarm.newBuilder()
            .setId(alarmId)
            .setReductionKey(reductionKey)
            .build()

        generateAlarmReceivers(alarm)?.let { receivers ->
            log.debug("Broadcasting alarm '$alarm' to receivers '$receivers'")
            broadcast(mapper.writeValueAsBytes(alarmDeleteMessage(reductionKey), receivers)
        }
    }

    override fun handleAlarmSnapshot(alarms: MutableList<Alarm>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun handleNewOrUpdatedAlarm(alarm: Alarm) {
        log.trace("Received new or updated alarm '$alarm'")
        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for alarm")
            return
        }

        generateAlarmReceivers(alarm)?.let { receivers ->
            log.debug("Broadcasting alarm '$alarm' to receivers '$receivers'")
            broadcast(mapper.writeValueAsBytes(alarmMessage(alarm)), receivers)
        }
    }

    private fun generateAlarmReceivers(alarm: Alarm): Set<WebSocket>? {
        val location = alarm.node.location
        val receivers = subscribers.filter { it.value == null || it.value!!.locations?.contains(location) ?: true }
            .map { it.key }
            .toSet()

        log.trace("Receivers for alarm '$alarm' are '$receivers'")

        return if(receivers.isNotEmpty()) receivers else null
    }
}