package org.opennms.integration.api.deserializer

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import org.opennms.integration.api.deserializer.proxies.TopologyEdgeBuilderProxy
import org.opennms.integration.api.v1.model.*
import org.opennms.integration.api.v1.model.immutables.*

const val BUILD_METHOD = "build"
const val SETTER_PREFIX = "set"

object OiaIntrospector : JacksonAnnotationIntrospector() {

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

            else -> super.findPOJOBuilder(ac)
        }
    }

    override fun findPOJOBuilderConfig(ac: AnnotatedClass?): JsonPOJOBuilder.Value? {
        requireNotNull(ac)

        return JsonPOJOBuilder.Value(BUILD_METHOD, SETTER_PREFIX)
    }

}