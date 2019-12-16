package org.opennms.arnet.api;

import org.opennms.arnet.api.model.Alarm;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Event;
import org.opennms.arnet.api.model.Situation;
import org.opennms.arnet.api.model.Vertex;

import java.util.Collection;

import edu.uci.ics.jung.graph.Graph;

public interface Consumer {

    /**
     * (Called on #init or #reset)
     *
     */
    void accept(Graph<Vertex, Edge> graph, Collection<Alarm> alarms, Collection<Situation> situations);

    /**
     * Invoked when the vertex is added or updated.
     *
     * @param v
     */
    void acceptVertex(Vertex v);

    /**
     * Invoked when the vertex is deleted.
     *
     * @param vertexId
     */
    void acceptDeletedVertex(String vertexId);

    void acceptEdge(Edge e);

    void acceptDeletedEdge(String edgeId);

    void acceptAlarm(Alarm a);

    void acceptDeletedAlarm(String reductionKey);

    void acceptSituation(Situation s);

    void acceptDeleteSituation(String reductionKey);

    /**
     * Only called when new events occur. No sync or deletions.
     *
     */
    void acceptEvent(Event e);

}
