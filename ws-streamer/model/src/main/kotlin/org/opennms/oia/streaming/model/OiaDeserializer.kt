package org.opennms.oia.streaming.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.opennms.integration.api.v1.model.*
import org.opennms.integration.api.v1.model.immutables.*

val oiaDeserializer: ObjectMapper by lazy {
    val mapper = jacksonObjectMapper()

    mapper.addMixIn(ImmutableAlarm.Builder::class.java, AlarmMixin::class.java)

    mapper.setAnnotationIntrospector(object : JacksonAnnotationIntrospector() {
        override fun findPOJOBuilder(ac: AnnotatedClass?): Class<*>? {
            requireNotNull(ac)

            return when (ac.rawType) {
                Alarm::class.java -> ImmutableAlarm.Builder::class.java
                AlarmFeedback::class.java -> ImmutableAlarmFeedback.Builder::class.java
                DatabaseEvent::class.java -> ImmutableDatabaseEvent.Builder::class.java
                EventParameter::class.java -> ImmutableEventParameter.Builder::class.java
                Geolocation::class.java -> ImmutableGeolocation.Builder::class.java
                InMemoryEvent::class.java -> ImmutableInMemoryEvent.Builder::class.java
                IpInterface::class.java -> ImmutableIpInterface.Builder::class.java
                MetaData::class.java -> ImmutableMetaData.Builder::class.java
                Node::class.java -> ImmutableNode.Builder::class.java
                NodeAssetRecord::class.java -> ImmutableNodeAssetRecord.Builder::class.java
                NodeCriteria::class.java -> ImmutableNodeCriteria.Builder::class.java
                SnmpInterface::class.java -> ImmutableSnmpInterface.Builder::class.java
                TopologyEdge::class.java -> TopologyEdgeBuilderProxy::class.java
                TopologyPort::class.java -> ImmutableTopologyPort.Builder::class.java
                TopologySegment::class.java -> ImmutableTopologySegment.Builder::class.java

                else -> null
            }
        }

        override fun findPOJOBuilderConfig(ac: AnnotatedClass?): JsonPOJOBuilder.Value? {
            requireNotNull(ac)

            return JsonPOJOBuilder.Value("build", "set")
        }
    })

    mapper
}

@JsonIgnoreProperties("situation")
private class AlarmMixin()

private class TopologyEdgeBuilderProxy() {
    private val builder = ImmutableTopologyEdge.newBuilder()

    fun setProtocol(protocol: TopologyProtocol): TopologyEdgeBuilderProxy {
        builder.setProtocol(protocol)
        return this
    }

    fun setId(id: String): TopologyEdgeBuilderProxy {
        builder.setId(id)
        return this
    }

    fun setTooltipText(tooltipText: String): TopologyEdgeBuilderProxy {
        builder.setTooltipText(tooltipText)
        return this
    }

    fun setSource(source: Any): TopologyEdgeBuilderProxy {
        when (source) {
            is Node -> builder.setSource(source)
            is TopologyPort -> builder.setSource(source)
            is TopologySegment -> builder.setSource(source)
            else -> error("Invalid source")
        }
        return this
    }

    fun setTarget(target: Any): TopologyEdgeBuilderProxy {
        when (target) {
            is Node -> builder.setTarget(target)
            is TopologyPort -> builder.setTarget(target)
            is TopologySegment -> builder.setTarget(target)
            else -> error("Invalid target")
        }
        return this
    }

    fun build(): ImmutableTopologyEdge {
        return builder.build()
    }
}