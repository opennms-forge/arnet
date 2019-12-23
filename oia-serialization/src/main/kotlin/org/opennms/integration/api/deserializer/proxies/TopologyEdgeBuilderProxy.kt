package org.opennms.integration.api.deserializer.proxies

import com.fasterxml.jackson.module.kotlin.convertValue
import org.opennms.integration.api.deserializer.oiaDeserializer
import org.opennms.integration.api.v1.model.*
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyEdge

internal class TopologyEdgeBuilderProxy {

    private val builder = ImmutableTopologyEdge.newBuilder()
    private lateinit var sourceDeserializationType: TopologyEdge.EndpointType
    private lateinit var targetDeserializationType: TopologyEdge.EndpointType
    private lateinit var source: Any
    private lateinit var target: Any

    fun setProtocol(protocol: TopologyProtocol): TopologyEdgeBuilderProxy {
        builder.setProtocol(protocol)
        return this
    }

    fun setId(id: String): TopologyEdgeBuilderProxy {
        builder.setId(id)
        return this
    }

    fun setTooltipText(tooltipText: String?): TopologyEdgeBuilderProxy {
        builder.setTooltipText(tooltipText)
        return this
    }

    fun setSourceDeserializationType(sourceType: TopologyEdge.EndpointType) {
        sourceDeserializationType = sourceType
    }

    fun setTargetDeserializationType(targetType: TopologyEdge.EndpointType) {
        targetDeserializationType = targetType
    }


    fun setSource(source: Any): TopologyEdgeBuilderProxy {
        this.source = source
        return this
    }

    fun setTarget(target: Any): TopologyEdgeBuilderProxy {
        this.target = target
        return this
    }

    fun build(): ImmutableTopologyEdge {
        when (sourceDeserializationType) {
            TopologyEdge.EndpointType.NODE -> builder.setSource(oiaDeserializer.convertValue<Node>(source))
            TopologyEdge.EndpointType.PORT -> builder.setSource(oiaDeserializer.convertValue<TopologyPort>(source))
            TopologyEdge.EndpointType.SEGMENT -> builder.setSource(oiaDeserializer.convertValue<TopologySegment>(source))
            else -> error("Invalid source")
        }

        when (targetDeserializationType) {
            TopologyEdge.EndpointType.NODE -> builder.setTarget(oiaDeserializer.convertValue<Node>(target))
            TopologyEdge.EndpointType.PORT -> builder.setTarget(oiaDeserializer.convertValue<TopologyPort>(target))
            TopologyEdge.EndpointType.SEGMENT -> builder.setTarget(oiaDeserializer.convertValue<TopologySegment>(target))
            else -> error("Invalid target")
        }

        return builder.build()
    }

}