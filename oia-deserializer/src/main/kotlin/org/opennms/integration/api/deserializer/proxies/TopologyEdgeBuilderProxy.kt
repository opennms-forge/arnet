package org.opennms.integration.api.deserializer.proxies

import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.TopologyPort
import org.opennms.integration.api.v1.model.TopologyProtocol
import org.opennms.integration.api.v1.model.TopologySegment
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyEdge

internal class TopologyEdgeBuilderProxy {

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