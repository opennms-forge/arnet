package org.opennms.arnet.app.mock;

import org.opennms.arnet.api.model.Alarm;

import java.util.Date;
import java.util.Objects;

public class MyAlarm implements Alarm {
    private final String reductionKey;
    private final String vertexId;

    public MyAlarm(String reductionKey, String vertexId) {
        this.reductionKey = Objects.requireNonNull(reductionKey);
        this.vertexId = Objects.requireNonNull(vertexId);
    }

    @Override
    public String getReductionKey() {
        return reductionKey;
    }

    @Override
    public Severity getSeverity() {
        return Severity.WARNING;
    }

    @Override
    public String getDescription() {
        return "descr";
    }

    @Override
    public Date getLastUpdated() {
        return new Date(0);
    }

    @Override
    public String getVertexId() {
        return vertexId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyAlarm myAlarm = (MyAlarm) o;
        return Objects.equals(reductionKey, myAlarm.reductionKey) &&
                Objects.equals(vertexId, myAlarm.vertexId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reductionKey, vertexId);
    }
}
