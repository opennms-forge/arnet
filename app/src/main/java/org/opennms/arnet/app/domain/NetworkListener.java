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

    default void onAlarmAddedOrUpdated(InventoryAlarm ia) {

    }

    default void onAlarmRemoved(InventoryAlarm ia) {

    }

    default void onSituationAddedOrUpdated(InventorySituation is) {

    }

    default void onSituationRemoved(InventorySituation is) {

    }

    default void onEvent(InventoryEvent ie) {

    }
}
