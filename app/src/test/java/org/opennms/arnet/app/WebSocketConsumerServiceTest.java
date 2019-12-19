package org.opennms.arnet.app;

import org.junit.Ignore;
import org.junit.Test;
import org.opennms.arnet.WebSocketConsumerService;
import org.opennms.arnet.api.Consumer;
import org.opennms.arnet.api.model.Alarm;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Event;
import org.opennms.arnet.api.model.Situation;
import org.opennms.arnet.api.model.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import edu.uci.ics.jung.graph.Graph;

import static org.awaitility.Awaitility.await;

@Ignore
public class WebSocketConsumerServiceTest implements Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketConsumerServiceTest.class);

    boolean gotVertex = false;

    @Test
    public void canConsume() {
        LOG.debug("Starting consumer.");
        WebSocketConsumerService wsSvc = new WebSocketConsumerService();
        wsSvc.start();
        wsSvc.accept(this);
        await().until(() -> gotVertex);
        wsSvc.stop();
    }

    @Override
    public void accept(Graph<Vertex, Edge> graph, Collection<Alarm> alarms, Collection<Situation> situations) {

    }

    @Override
    public void acceptVertex(Vertex v) {
        gotVertex = true;

    }

    @Override
    public void acceptDeletedVertex(String vertexId) {

    }

    @Override
    public void acceptEdge(Edge e) {

    }

    @Override
    public void acceptDeletedEdge(String edgeId) {

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
}
