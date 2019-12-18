package org.opennms.arnet.app.domain;

import edu.uci.ics.jung.graph.Graph;

public interface LayoutStrategy {
    void apply(Graph<InventoryVertex, ?> g);
}
