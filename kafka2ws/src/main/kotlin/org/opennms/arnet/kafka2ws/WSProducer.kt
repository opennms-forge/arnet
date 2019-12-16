package org.opennms.arnet.kafka2ws

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import org.opennms.arnet.model.*
import java.util.*

@ServerWebSocket("/")
class WSProducer(private val mapper: ObjectMapper, private val broadcaster: WebSocketBroadcaster) {

    @OnMessage
    fun onMessage(message: WSRequest, session: WebSocketSession) {
        val alarmA = Alarm("a", Severity.CRITICAL, "a-desc", Date(), "a-id")
        val alarmB = Alarm("b", Severity.CRITICAL, "b-desc", Date(), "b-id")
        val situationAlarm = Alarm("situation-a-b", Severity.CRITICAL, "situation-descr", Date(), "situation-id")
        val testSituation = Situation(setOf(alarmA, alarmB), situationAlarm)
        session.sendSync(testSituation)
    }

    @OnClose
    fun onClose(session: WebSocketSession) {

    }

}