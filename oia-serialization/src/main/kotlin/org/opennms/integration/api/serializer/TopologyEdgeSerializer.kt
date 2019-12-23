package org.opennms.integration.api.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.TopologyEdge
import org.opennms.integration.api.v1.model.TopologyPort
import org.opennms.integration.api.v1.model.TopologySegment

class TopologyEdgeSerializer(t: Class<TopologyEdge>?) : StdSerializer<TopologyEdge>(t) {
    constructor() : this(null)

    override fun serialize(value: TopologyEdge, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject();
        gen.writeStringField("id", value.id);
        gen.writeStringField("tooltipText", value.tooltipText);
        gen.writeStringField("protocol", value.protocol.toString());

        lateinit var source: Any
        lateinit var target: Any
        lateinit var sourceDeserializationType: TopologyEdge.EndpointType
        lateinit var targetDeserializationType: TopologyEdge.EndpointType
        value.visitEndpoints(object : TopologyEdge.EndpointVisitor {
            override fun visitSource(node: Node) {
                source = node
                sourceDeserializationType = TopologyEdge.EndpointType.NODE
            }

            override fun visitSource(port: TopologyPort) {
                source = port
                sourceDeserializationType = TopologyEdge.EndpointType.PORT
            }

            override fun visitSource(segment: TopologySegment) {
                source = segment
                sourceDeserializationType = TopologyEdge.EndpointType.SEGMENT
            }

            override fun visitTarget(node: Node) {
                target = node
                targetDeserializationType = TopologyEdge.EndpointType.NODE
            }

            override fun visitTarget(port: TopologyPort) {
                target = port
                targetDeserializationType = TopologyEdge.EndpointType.PORT
            }

            override fun visitTarget(segment: TopologySegment) {
                target = segment
                targetDeserializationType = TopologyEdge.EndpointType.SEGMENT
            }
        })

        gen.writeObjectField("source", source)
        gen.writeObjectField("target", target)
        gen.writeObjectField("sourceDeserializationType", sourceDeserializationType)
        gen.writeObjectField("targetDeserializationType", targetDeserializationType)

        gen.writeEndObject();
    }
}