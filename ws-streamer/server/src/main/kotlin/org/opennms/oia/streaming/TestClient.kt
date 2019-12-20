package org.opennms.oia.streaming

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.opennms.oia.streaming.model.*
import java.lang.Exception
import java.net.URI
import java.nio.ByteBuffer

fun main() {
    val client = WsClient(URI("ws://172.20.50.109:8080"))
    client.connect()
    Thread.sleep(10000)
}

class WsClient(serverURI: URI) : WebSocketClient(serverURI) {
    override fun onOpen(handshakedata: ServerHandshake?) {
        send(jacksonObjectMapper().writeValueAsString(subscribeRequest()))
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onMessage(message: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onError(ex: Exception?) {
        ex?.printStackTrace()
    }

    override fun onMessage(bytes: ByteBuffer?) {
        requireNotNull(bytes)
        println("test")
        try {
            val msg = jacksonObjectMapper().readValue<StreamMessage>(bytes.array())
            
            when(msg.type) {
                MessageType.Topology -> println(msg.deserializePayload<Topology>())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
    }
}