package org.opennms.arnet.app.domain;

import org.opennms.oia.streaming.client.api.model.Edge;

import java.util.Objects;

public class InventoryEdge {

    private final Edge e;

    public InventoryEdge(Edge e) {
        this.e = Objects.requireNonNull(e);
    }

    public String getSourceVertexId() {
        return e.getSourceVertex().getId();
    }

    public String getTargetVertexId() {
        return e.getTargetVertex().getId();
    }

    public String getId() {
        return e.getId();
    }

    @Override
    public String toString() {
        return "InventoryEdge{" +
                "e=" + e +
                '}';
    }
}
