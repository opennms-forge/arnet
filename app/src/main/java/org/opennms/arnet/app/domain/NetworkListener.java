package org.opennms.arnet.app.domain;

public interface NetworkListener {

    default void onVertexAddedOrUpdated(InventoryVertex v) {

    }

    default void  onVertexRemoved(InventoryVertex v) {

    }

    default void  onEdgeAddedOrUpdated(InventoryEdge e) {

    }

    default void  onEdgeRemoved(InventoryEdge e) {

    }

    default void  onLayoutRecalculated() {

    }

}
