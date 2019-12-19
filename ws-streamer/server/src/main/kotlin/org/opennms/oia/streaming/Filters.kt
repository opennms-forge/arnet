package org.opennms.oia.streaming

import org.opennms.integration.api.v1.model.Alarm
import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.TopologyEdge
import org.opennms.oia.streaming.model.FilterCriteria

internal fun filterAlarm(alarm: Alarm, filterCriteria: FilterCriteria?) =
    if (filterCriteria?.locations == null) true else filterCriteria.locations!!.contains(alarm.node.location)

internal fun filterNode(node: Node, filterCriteria: FilterCriteria?) =
    if (filterCriteria?.locations == null) true else filterCriteria.locations!!.contains(node.location)

internal fun filterEdge(edge: TopologyEdge, filterCriteria: FilterCriteria?): Boolean {
    if (filterCriteria?.locations == null) {
        return true
    }

    var matchingEndpoints: Int = 0

    val edgeVisitor = object : TopologyEdge.EndpointVisitor {
        override fun visitSource(node: Node) {
            if (filterCriteria.locations!!.contains(node.location)) {
                matchingEndpoints++
            }
        }

        override fun visitTarget(node: Node) {
            if (filterCriteria.locations!!.contains(node.location)) {
                matchingEndpoints++
            }
        }
    }

    edge.visitEndpoints(edgeVisitor)

    if (matchingEndpoints == 2) {
        return true
    }

    return false
}