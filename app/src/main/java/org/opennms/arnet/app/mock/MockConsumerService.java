package org.opennms.arnet.app.mock;

import org.opennms.oia.streaming.client.api.Consumer;
import org.opennms.oia.streaming.client.api.ConsumerService;
import org.opennms.oia.streaming.client.api.model.Alarm;
import org.opennms.oia.streaming.client.api.model.Edge;
import org.opennms.oia.streaming.client.api.model.Situation;
import org.opennms.oia.streaming.client.api.model.Vertex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final List<Alarm> alarms;
    private final List<Situation> situations;

    private final AtomicReference<Vertex> lastVertex = new AtomicReference<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    private final Set<Consumer> consumers = new LinkedHashSet<>();

    public MockConsumerService() {
        int nextId = 1;
        // TODO: Express this as nicer builder-style pattern
        // Create some fixed graph to start
        Vertex v1 = MyVertex.forId(nextId++);
        g.addVertex(v1);
        Vertex v2 = MyVertex.forId(nextId++);
        g.addVertex(v2);
        Vertex v3 = MyVertex.forId(nextId++);
        g.addVertex(v3);
        Vertex v4 = MyVertex.forId(nextId++);
        g.addVertex(v4);
        Vertex v5 = MyVertex.forId(nextId++);
        g.addVertex(v5);
        g.addEdge(MyEdge.forId("Edge-1-2", v1, v2), v1, v2);
        g.addEdge(MyEdge.forId("Edge-1-4", v1, v4), v1, v4);
        g.addEdge(MyEdge.forId("Edge-1-5", v1, v5), v1, v5);
        g.addEdge(MyEdge.forId("Edge-2-3", v2, v3), v2, v3);

        // Setup for generation
        lastVertex.set(v5);
        this.nextId.set(nextId);

        // Create some alarms
        alarms = Arrays.asList(new MyAlarm("oops1", v1.getId()), new MyAlarm("oops2", v1.getId()));

        // And a situation
        situations = Arrays.asList(new MySituation("situ1", alarms, v1.getId()));
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
               // lastVertex.set(v);
            }
        }, 1000, 1000);
    }

    @Override
    public void accept(Consumer consumer) {
        consumer.accept(g, alarms, situations);
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
