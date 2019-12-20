package org.opennms.arnet.app.domain;

import org.opennms.oia.streaming.client.api.model.Situation;

import java.util.Objects;

public class InventorySituation implements XY {

    private final Situation s;
    private float x, y;

    public InventorySituation(Situation s) {
        this.s = Objects.requireNonNull(s);
    }

    public String getReductionKey() {
        return s.getReductionKey();
    }

    public Situation getSituation() {
        return s;
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
        return "InventorySituation{" +
                "s=" + s +
                ", x=" + x +
                ", y=" + y +
                '}';
    }


}
