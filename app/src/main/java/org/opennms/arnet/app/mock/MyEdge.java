package org.opennms.arnet.app.mock;

import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Vertex;

import java.util.Objects;

public class MyEdge implements Edge {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyEdge myEdge = (MyEdge) o;
        return Objects.equals(id, myEdge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
