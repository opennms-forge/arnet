package org.opennms.arnet.app.scene;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import org.opennms.arnet.api.model.Vertex;
import org.opennms.arnet.app.domain.InventoryEdge;

import java.util.Objects;

public class ConnectorNode extends Node {

    private InventoryEdge e;
    private InventoryNode sourceNode;
    private InventoryNode targetNode;

    public ConnectorNode(InventoryEdge e, InventoryNode sourceNode, InventoryNode targetNode) {
        this.e = Objects.requireNonNull(e);
        this.sourceNode = Objects.requireNonNull(sourceNode);
        this.targetNode = Objects.requireNonNull(targetNode);
    }

    public void trackSourceAndTargetVertices() {
        Vector3 sourcePosition = sourceNode.getLocalPosition();
        Vector3 targetPosition = targetNode.getLocalPosition();

        final Vector3 difference = Vector3.subtract(sourcePosition, targetPosition);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

        setLocalScale(new Vector3( 0.5f, 0.5f, difference.length() * 100));
        setLocalPosition(Vector3.add(sourcePosition, targetPosition).scaled(.5f));
        setLocalRotation(rotationFromAToB);
    }
}
