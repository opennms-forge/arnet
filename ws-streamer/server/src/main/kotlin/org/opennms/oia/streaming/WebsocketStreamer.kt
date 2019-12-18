package org.opennms.oia.streaming

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.opennms.oia.streaming.model.RequestAction
import org.opennms.oia.streaming.model.StreamRequest
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class WebsocketStreamer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val mapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(WebsocketStreamer::class.java)

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
            RequestAction.SUBSCRIBE -> log.info("Received subscribe request from connection '$conn'")
            RequestAction.UNSUBSCRIBE -> log.info("Received unsubscribe request from connection '$conn'")
        }
    }

    override fun onStart() {
        log.info("Websocket server started on port '$port'")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        log.warn("Error starting websocket server", ex)
    }

}