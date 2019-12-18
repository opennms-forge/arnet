package org.opennms.arnet.app.scene;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;

import org.opennms.arnet.app.domain.InventoryVertex;

import java.util.Objects;

public class InventoryNode extends Node {

    private InventoryVertex v;

    public InventoryNode(InventoryVertex v) {
        this.v = Objects.requireNonNull(v);
        inheritPositionFromLayout();
    }

    public void inheritPositionFromLayout() {
        setLocalPosition(new Vector3((v.getX() / 2) - 0.25f, 0.1f, (v.getY() / 2) - 0.25f));
    }
}
