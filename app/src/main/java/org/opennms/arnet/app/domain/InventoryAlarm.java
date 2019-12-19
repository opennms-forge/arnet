package org.opennms.arnet.app.domain;

import org.opennms.arnet.api.model.Alarm;

import java.util.Objects;

public class InventoryAlarm implements XY {

    private final Alarm a;
    private float x, y;

    public InventoryAlarm(Alarm a) {
        this.a = Objects.requireNonNull(a);
    }

    public String getReductionKey() {
        return a.getReductionKey();
    }

    public Alarm getAlarm() {
        return a;
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
        return "InventoryAlarm{" +
                "a=" + a +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}
