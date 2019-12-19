package org.opennms.arnet.app.domain;

import java.util.Collection;

import edu.uci.ics.jung.graph.Graph;

public interface LayoutStrategy {
    void apply(Graph<InventoryVertex, InventoryEdge> inventoryGraph, Collection<InventoryAlarm> values, Collection<InventorySituation> values1);
}
