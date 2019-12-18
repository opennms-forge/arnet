package org.opennms.oia.streaming

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.opennms.arnet.model.ArnetRequest
import java.net.InetSocketAddress

class WebsocketStreamer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    val mapper = jacksonObjectMapper()
    
    init {
        println("hi $port")
    }

    fun init() {
        start()
    }

    override fun onOpen(p0: WebSocket?, p1: ClientHandshake?) {
    }

    override fun onClose(p0: WebSocket?, p1: Int, p2: String?, p3: Boolean) {
    }

    override fun onMessage(p0: WebSocket?, p1: String?) {
        val request = mapper.readValue<ArnetRequest>(p1!!)
        p0!!.send("Received ${request.action}")
        println(request)
    }

    override fun onStart() {
    }

    override fun onError(p0: WebSocket?, p1: Exception?) {
    }
}