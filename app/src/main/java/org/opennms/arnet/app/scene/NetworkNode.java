package org.opennms.arnet.app.scene;

import android.util.Log;

import com.google.ar.core.AugmentedImage;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.utilities.AndroidPreconditions;

import org.jetbrains.annotations.Nullable;
import org.opennms.arnet.api.ConsumerService;
import org.opennms.arnet.app.domain.InventoryEdge;
import org.opennms.arnet.app.domain.InventoryVertex;
import org.opennms.arnet.app.domain.NetworkManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkNode extends AnchorNode implements Scene.OnUpdateListener, NetworkListenerDelegate.Visitor {
    private static final String TAG = "NetworkNode";

    private boolean destroyed = false;

    private final Scene scene;
    private final RenderableRegistry renderables;

    private final ConsumerService consumerService;
    private NetworkManager networkManager;
    private NetworkListenerDelegate delegate;

    private final Map<String, InventoryNode> inventoryNodesById = new LinkedHashMap<>();
    private final Map<String, ConnectorNode> connectorNodesById = new LinkedHashMap<>();

    public NetworkNode(Scene scene, RenderableRegistry renderables, ConsumerService consumerService) {
        this.scene = Objects.requireNonNull(scene);
        this.renderables = Objects.requireNonNull(renderables);
        this.consumerService = Objects.requireNonNull(consumerService);

        renderables.getFuture().whenComplete((aVoid, ex) -> {
            if (ex != null) {
                Log.e(TAG, "Exception renderables", ex);
                return;
            }
            doInit();
        });
    }

    public void setImage(AugmentedImage image) {
        // Set the anchor based on the center of the image.
        setAnchor(image.createAnchor(image.getCenterPose()));
    }

    public synchronized void destroy() {
        destroyed = true;
        scene.removeOnUpdateListener(this);
        if (networkManager != null) {
            consumerService.dismiss(networkManager);
        }
    }

    private synchronized void doInit() {
        if (destroyed) {
            return;
        }

        scene.addOnUpdateListener(this);

        // Create and register the Network manager
        delegate = new NetworkListenerDelegate();
        networkManager = new NetworkManager(delegate);
        consumerService.accept(networkManager);
    }

    @Override
    public void onVertexAddedOrUpdated(InventoryVertex v) {
        inventoryNodesById.computeIfAbsent(v.getId(), id -> {
            InventoryNode node = new InventoryNode(v);
            node.setParent(this);
            node.setRenderable(renderables.getRedBall());
            node.setWorldScale(new Vector3(0.1f, 0.1f, 0.1f));
            return node;
        });
    }

    @Override
    public void onVertexRemoved(InventoryVertex v) {
        InventoryNode node = inventoryNodesById.remove(v.getId());
        if (node == null) {
            return;
        }
        removeChild(node);
    }

    @Override
    public void onEdgeAddedOrUpdated(InventoryEdge e) {
        connectorNodesById.computeIfAbsent(e.getId(), id -> {
            InventoryNode sourceNode = inventoryNodesById.get(e.getSourceVertexId());
            InventoryNode targetNode = inventoryNodesById.get(e.getTargetVertexId());

            ConnectorNode node = new ConnectorNode(e, sourceNode, targetNode);
            node.setParent(this);
            node.setRenderable(renderables.getCube());
            return node;
        });
    }

    @Override
    public void onEdgeRemoved(InventoryEdge e) {
        ConnectorNode node = connectorNodesById.remove(e.getId());
        if (node == null) {
            return;
        }
        removeChild(node);
    }

    @Override
    public void onLayoutRecalculated() {
        // Inherit the vertex positions from the layout
        inventoryNodesById.values().forEach(InventoryNode::inheritPositionFromLayout);
        // Connectors should track the vertices
        connectorNodesById.values().forEach(ConnectorNode::trackSourceAndTargetVertices);
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        // Process the pending tasks
        for (NetworkListenerDelegate.Task task : delegate.getTasks()) {
            task.visit(this);
        }
    }

}
