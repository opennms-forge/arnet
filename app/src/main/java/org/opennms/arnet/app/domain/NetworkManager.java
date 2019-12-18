package org.opennms.arnet.app.domain;

import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.opennms.arnet.api.Consumer;
import org.opennms.arnet.api.model.Alarm;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Event;
import org.opennms.arnet.api.model.Situation;
import org.opennms.arnet.api.model.Vertex;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

public class NetworkManager implements Consumer {

    private static final String TAG = "NetworkManager";

    private final Graph<InventoryVertex, InventoryEdge> inventoryGraph = new SparseMultigraph<>();
    private final Map<String, InventoryVertex> inventoryVerticesById = new LinkedHashMap<>();
    private final Map<String, InventoryEdge> inventoryEdgesById = new LinkedHashMap<>();
    private final NetworkListener listener;

    private LayoutStrategy layoutStrategy = new FRLayoutStrategy();

    public NetworkManager(NetworkListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    private void merge(Graph<Vertex, Edge> graph) {
        boolean didAddOrRemoveVertex = false;
        boolean didAddOrRemoveEdge = false;

        synchronized (inventoryGraph) {
            final Set<String> graphVertexIds = new HashSet<>();
            for (Vertex v : graph.getVertices()) {
                graphVertexIds.add(v.getId());
                if (inventoryVerticesById.containsKey(v.getId())) {
                    // We already have this vertex on the graph, nothing to do
                    continue;
                }

                // The vertex is not on the graph, let's add it
                addVertex(v);
                didAddOrRemoveVertex = true;
            }

            // We've added all of the vertices that need to be added, let's see if any have been removed
            for (String vertexIdToRemove : Sets.difference(inventoryVerticesById.keySet(), graphVertexIds)) {
                final InventoryVertex iv = inventoryVerticesById.remove(vertexIdToRemove);
                inventoryGraph.removeVertex(iv);
                didAddOrRemoveVertex = true;
            }

            final Set<String> graphEdgeIds = new HashSet<>();
            for (Edge e : graph.getEdges()) {
                graphEdgeIds.add(e.getId());
                if (inventoryEdgesById.containsKey(e.getId())) {
                    // We already have this edge on the graph, nothing to do
                    continue;
                }

                // The edge is not on the graph, let's add it
                addEdge(e);
                didAddOrRemoveEdge = true;
            }

            // We've added all of the edges that need to be added, let's see if any have been removed
            for (String edgeIdToRemove : Sets.difference(inventoryEdgesById.keySet(), graphEdgeIds)) {
                final InventoryEdge ie = inventoryEdgesById.remove(edgeIdToRemove);
                inventoryGraph.removeEdge(ie);
                didAddOrRemoveEdge = true;
            }

            // Recompute the layout if things have changed
            if (didAddOrRemoveVertex) {
                recalculateLayout();
            }
        }
    }

    private void addVertex(Vertex v) {
        final InventoryVertex iv = new InventoryVertex(v);
        inventoryGraph.addVertex(iv);
        inventoryVerticesById.put(iv.getId(), iv);
        listener.onVertexAddedOrUpdated(iv);
    }

    private void addEdge(Edge e) {
        final InventoryEdge ie = new InventoryEdge(e);
        final InventoryVertex sourceVertex = inventoryVerticesById.get(e.getSourceVertex().getId());
        if (sourceVertex == null) {
            Log.w(TAG, String.format("No source vertex found with id: %s for edge with id: %s. Skipping.",
                    e.getSourceVertex().getId(), e.getId()));
            return;
        }
        final InventoryVertex targetVertex = inventoryVerticesById.get(e.getTargetVertex().getId());
        if (targetVertex == null) {
            Log.w(TAG, String.format("No target vertex found with id: %s for edge with id: %s. Skipping.",
                    e.getTargetVertex().getId(), e.getId()));
            return;
        }
        inventoryGraph.addEdge(ie, sourceVertex, targetVertex);
        inventoryEdgesById.put(ie.getId(), ie);
        listener.onEdgeAddedOrUpdated(ie);
    }

    private void recalculateLayout() {
        layoutStrategy.apply(inventoryGraph);
        listener.onLayoutRecalculated();
    }

    @Override
    public void accept(Graph<Vertex, Edge> graph, Collection<Alarm> alarms, Collection<Situation> situations) {
        merge(graph);
    }

    @Override
    public void acceptVertex(Vertex v) {
        synchronized (inventoryGraph) {
            addVertex(v);
            recalculateLayout();
        }
    }

    @Override
    public void acceptDeletedVertex(String vertexId) {
        synchronized (inventoryGraph) {
            final InventoryVertex iv = inventoryVerticesById.remove(vertexId);
            inventoryGraph.removeVertex(iv);
            listener.onVertexRemoved(iv);
            recalculateLayout();
        }
    }

    @Override
    public void acceptEdge(Edge e) {
        synchronized (inventoryGraph) {
            addEdge(e);
        }
    }

    @Override
    public void acceptDeletedEdge(String edgeId) {
        synchronized (inventoryGraph) {
            final InventoryEdge ie = inventoryEdgesById.remove(edgeId);
            inventoryGraph.removeEdge(ie);
            listener.onEdgeRemoved(ie);
        }
    }

    @Override
    public void acceptAlarm(Alarm a) {

    }

    @Override
    public void acceptDeletedAlarm(String reductionKey) {

    }

    @Override
    public void acceptSituation(Situation s) {

    }

    @Override
    public void acceptDeleteSituation(String reductionKey) {

    }

    @Override
    public void acceptEvent(Event e) {

    }

    public List<InventoryVertex> getInventoryVertices() {
        return Lists.newArrayList(inventoryVerticesById.values());
    }

    public List<InventoryEdge> getInventoryEdges() {
        return Lists.newArrayList(inventoryEdgesById.values());
    }

    public void setLayoutStrategy(LayoutStrategy layoutStrategy) {
        this.layoutStrategy = Objects.requireNonNull(layoutStrategy);
    }

}
