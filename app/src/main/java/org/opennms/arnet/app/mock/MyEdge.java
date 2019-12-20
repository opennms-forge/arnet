package org.opennms.arnet.app.mock;

import org.opennms.oia.streaming.client.api.model.Edge;
import org.opennms.oia.streaming.client.api.model.Vertex;

import java.util.Objects;

public class MyEdge implements Edge {
    private final String id;
    private final Vertex source;
    private final Vertex target;

    public MyEdge(String id, Vertex source, Vertex target) {
        this.id = Objects.requireNonNull(id);
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
    }

    public static Edge forId(String id, Vertex source, Vertex target) {
        return new MyEdge(id, source, target);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Vertex getSourceVertex() {
        return source;
    }

    @Override
    public Vertex getTargetVertex() {
        return target;
    }

    @Override
    public String getProtocol() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyEdge myEdge = (MyEdge) o;
        return Objects.equals(id, myEdge.id) &&
                Objects.equals(source, myEdge.source) &&
                Objects.equals(target, myEdge.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, target);
    }
}
