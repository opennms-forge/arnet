package org.opennms.arnet.app.scene;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;

import org.opennms.arnet.app.domain.InventoryAlarm;

import java.util.Objects;

public class AlarmNode extends Node {
    private InventoryAlarm a;

    public AlarmNode(InventoryAlarm a) {
        this.a = Objects.requireNonNull(a);
        inheritPositionFromLayout();
    }

    public void inheritPositionFromLayout() {
        setLocalPosition(new Vector3(a.getX(), 0.5f, a.getY()));
    }
}
