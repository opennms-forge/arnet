package org.opennms.arnet.app.mock;

import org.opennms.oia.streaming.client.api.model.Vertex;

import java.util.Objects;

public class MyVertex implements Vertex {

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyVertex myVertex = (MyVertex) o;
        return id == myVertex.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
