package com.google.ar.sceneform.samples.augmentedimage;

import org.opennms.arnet.api.model.Edge;
import com.google.ar.sceneform.samples.graph.GraphProvider;
import org.opennms.arnet.api.model.Vertex;

import java.util.function.Consumer;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

public class StaticGraphProviderImpl implements GraphProvider {

    private final Graph<Vertex, Edge> g = new SparseMultigraph<>();

    public StaticGraphProviderImpl() {
        g.addVertex(MyVertex.forId(1));
        g.addVertex(MyVertex.forId(2));
        g.addVertex(MyVertex.forId(3));

        g.addEdge(MyEdge.forId("Edge-A"), MyVertex.forId(1), MyVertex.forId(2));
        g.addEdge(MyEdge.forId("Edge-B"), MyVertex.forId(2), MyVertex.forId(3));
    }

    @Override
    public Graph getAndSubscribe(Consumer<Graph<Vertex, Edge>> consumer) {
        return g;
    }

    private static class MyVertex implements Vertex {

        private final int id;

        public MyVertex(int id) {
            this.id = id;
        }

        public static MyVertex forId(int id) {
            return new MyVertex(id);
        }

        @Override
        public String getId() {
            return Integer.toString(id);
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public Type getType() {
            return null;
        }
    }

    private static class MyEdge implements Edge {
        private final String id;

        public MyEdge(String id) {
            this.id = id;
        }

        public static MyEdge forId(String id) {
            return new MyEdge(id);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Vertex getSourceVertex() {
            return null;
        }

        @Override
        public Vertex getTargetVertex() {
            return null;
        }

        @Override
        public String getProtocol() {
            return null;
        }
    }
}
