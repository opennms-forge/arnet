package org.opennms.arnet.app.mock;

import org.opennms.arnet.api.Consumer;
import org.opennms.arnet.api.ConsumerService;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Vertex;

import java.util.Collections;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

/**
 * A mock consumer that generates fixed data for testing.
 */
public class MockConsumerService implements ConsumerService {

    private final Graph<Vertex, Edge> g = new SparseMultigraph<>();

    public MockConsumerService() {
        g.addVertex(MyVertex.forId(1));
        g.addVertex(MyVertex.forId(2));
        g.addVertex(MyVertex.forId(3));

        g.addEdge(MyEdge.forId("Edge-A"), MyVertex.forId(1), MyVertex.forId(2));
        g.addEdge(MyEdge.forId("Edge-B"), MyVertex.forId(2), MyVertex.forId(3));
    }

    @Override
    public void accept(Consumer consumer) {
        consumer.accept(g, Collections.emptyList(), Collections.emptyList());
    }
}
