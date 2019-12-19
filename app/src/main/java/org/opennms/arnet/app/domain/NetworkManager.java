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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

public class NetworkManager implements Consumer {

    private static final String TAG = "NetworkManager";

    private final Map<String, InventoryAlarm> alarmsByReductionKey = new HashMap<>();
    private final Map<String, InventorySituation> situationsByReductionKey = new HashMap<>();

    private final Graph<InventoryVertex, InventoryEdge> inventoryGraph = new SparseMultigraph<>();
    private final Map<String, InventoryVertex> inventoryVerticesById = new LinkedHashMap<>();
    private final Map<String, InventoryEdge> inventoryEdgesById = new LinkedHashMap<>();
    private final NetworkListener listener;

    private LayoutStrategy layoutStrategy = new ForceBasedLayoutStrategy();

    public NetworkManager(NetworkListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    @Override
    public void accept(Graph<Vertex, Edge> graph, Collection<Alarm> alarms, Collection<Situation> situations) {
        updateGraph(graph);
        updateAlarms(alarms);
        updateSituations(situations);
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
        synchronized (alarmsByReductionKey) {
            addAlarm(a);
        }
    }

    @Override
    public void acceptDeletedAlarm(String reductionKey) {
        synchronized (alarmsByReductionKey) {
            final InventoryAlarm ia = alarmsByReductionKey.remove(reductionKey);
            listener.onAlarmRemoved(ia);
        }
    }

    @Override
    public void acceptSituation(Situation s) {
        synchronized (situationsByReductionKey) {
            addSituation(s);
        }
    }

    @Override
    public void acceptDeleteSituation(String reductionKey) {
        synchronized (situationsByReductionKey) {
            final InventorySituation is = situationsByReductionKey.remove(reductionKey);
            listener.onSituationRemoved(is);
        }
    }

    @Override
    public void acceptEvent(Event e) {
        listener.onEvent(new InventoryEvent(e));
    }

    private void updateAlarms(Collection<Alarm> alarms) {
        boolean didAddOrRemoveAlarm = false;

        synchronized (alarmsByReductionKey) {
            final Set<String> alarmReductionKeys = new HashSet<>();
            for (Alarm alarm : alarms) {
                alarmReductionKeys.add(alarm.getReductionKey());
                if (alarmsByReductionKey.containsKey(alarm.getReductionKey())) {
                    // We already know about this alarm, nothing to do
                    continue;
                }

                // Add it
                addAlarm(alarm);
                didAddOrRemoveAlarm = true;
            }

            // We've added all of the alarms that need to be added, let's see if any need to be removed
            for (String reductionKeyToRemove : Sets.difference(alarmsByReductionKey.keySet(), alarmReductionKeys)) {
                final InventoryAlarm ia = alarmsByReductionKey.remove(reductionKeyToRemove);
                listener.onAlarmRemoved(ia);
                didAddOrRemoveAlarm = true;
            }
        }

        if (didAddOrRemoveAlarm) {
            recalculateLayout();
        }
    }

    private void updateSituations(Collection<Situation> situations) {
        boolean didAddOrRemoveSituation = false;

        synchronized (situationsByReductionKey) {
            final Set<String> situationReductionKeys = new HashSet<>();
            for (Situation situation : situations) {
                situationReductionKeys.add(situation.getReductionKey());
                if (situationsByReductionKey.containsKey(situation.getReductionKey())) {
                    // We already know about this situation, nothing to do
                    continue;
                }

                // Add it
                addSituation(situation);
                didAddOrRemoveSituation = true;
            }

            // We've added all of the situations that need to be added, let's see if any need to be removed
            for (String reductionKeyToRemove : Sets.difference(situationsByReductionKey.keySet(), situationReductionKeys)) {
                final InventorySituation is = situationsByReductionKey.remove(reductionKeyToRemove);
                listener.onSituationRemoved(is);
                didAddOrRemoveSituation = true;
            }
        }

        if (didAddOrRemoveSituation) {
            recalculateLayout();
        }
    }

    private void addAlarm(Alarm alarm) {
        final InventoryAlarm ia = new InventoryAlarm(alarm);
        alarmsByReductionKey.put(ia.getReductionKey(), ia);
        listener.onAlarmAddedOrUpdated(ia);
    }

    private void addSituation(Situation situation) {
        final InventorySituation is = new InventorySituation(situation);
        situationsByReductionKey.put(is.getReductionKey(), is);
        listener.onSituationAddedOrUpdated(is);
    }

    private void updateGraph(Graph<Vertex, Edge> graph) {
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
                listener.onVertexRemoved(iv);
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
                listener.onEdgeRemoved(ie);
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
        layoutStrategy.apply(inventoryGraph, alarmsByReductionKey.values(), situationsByReductionKey.values());
        listener.onLayoutRecalculated();
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

    public List<InventoryAlarm> getAlarms() {
        return new ArrayList<>(alarmsByReductionKey.values());
    }

    public List<InventorySituation> getSituations() {
        return new ArrayList<>(situationsByReductionKey.values());
    }
}
