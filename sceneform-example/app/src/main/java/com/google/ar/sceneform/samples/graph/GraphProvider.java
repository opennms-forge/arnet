package com.google.ar.sceneform.samples.graph;

import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Vertex;

import java.util.function.Consumer;

import edu.uci.ics.jung.graph.Graph;

public interface GraphProvider {

    Graph<Vertex, Edge> getAndSubscribe(Consumer<Graph<Vertex, Edge>> consumer);

}
