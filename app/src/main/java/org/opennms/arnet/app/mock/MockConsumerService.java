package org.opennms.arnet.app.mock;

import org.opennms.arnet.api.Consumer;
import org.opennms.arnet.api.ConsumerService;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Vertex;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

/**
 * A mock consumer that generates fixed data for testing.
 */
public class MockConsumerService implements ConsumerService {

    private final Graph<Vertex, Edge> g = new SparseMultigraph<>();

    private final AtomicReference<Vertex> lastVertex = new AtomicReference<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    private final Set<Consumer> consumers = new LinkedHashSet<>();

    public MockConsumerService() {
        // Create some fixed graph to start
        Vertex v1 = MyVertex.forId(1);
        g.addVertex(v1);
        Vertex v2 = MyVertex.forId(2);
        g.addVertex(v2);
        Vertex v3 = MyVertex.forId(3);
        g.addVertex(v3);
        g.addEdge(MyEdge.forId("Edge-1-2", v1, v2), v1, v2);
        g.addEdge(MyEdge.forId("Edge-2-3", v2, v3), v2, v3);

        // Setup for generation
        lastVertex.set(v3);
        nextId.set(4);
    }

    public void updateGraphOnBackgroundThread() {
        // Every 1 sec, add another vertex and edge
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                // Generate the vertex and edge
                MyVertex v = MyVertex.forId(nextId.getAndIncrement());
                g.addVertex(v);
                Edge e = MyEdge.forId(String.format("Edge-%s-%s", lastVertex.get().getId(), v.getId()), lastVertex.get(), v);
                g.addEdge(e, lastVertex.get(), v);

                // Notify the consumers
                synchronized (consumers) {
                    for (Consumer consumer : consumers) {
                        consumer.acceptVertex(v);
                        consumer.acceptEdge(e);
                    }
                }

                // Ready for next iteration
                lastVertex.set(v);
            }
        }, 1000, 1000);
    }

    @Override
    public void accept(Consumer consumer) {
        consumer.accept(g, Collections.emptyList(), Collections.emptyList());
        synchronized (consumers) {
            consumers.add(consumer);
        }
    }

    @Override
    public void dismiss(Consumer consumer) {
        synchronized (consumers) {
            consumers.remove(consumer);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
