package org.opennms.arnet.app.domain;

import org.opennms.arnet.api.model.Vertex;

import java.util.Objects;

public class InventoryVertex implements XY {

    private final Vertex v;
    private float x, y;

    public InventoryVertex(Vertex v) {
        this.v = Objects.requireNonNull(v);
    }

    public String getId() {
        return v.getId();
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public void setY(float y) {
        this.y = y;

    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public String toString() {
        return "InventoryVertex{" +
                "v=" + v +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}
