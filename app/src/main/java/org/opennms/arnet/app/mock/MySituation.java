package org.opennms.arnet.app.mock;

import org.opennms.arnet.api.model.Alarm;
import org.opennms.arnet.api.model.Situation;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MySituation implements Situation {
    private final String reductionKey;
    private final Set<Alarm> relatedAlarms;
    private final String vertexId;

    public MySituation(String reductionKey, Collection<Alarm> relatedAlarms, String vertexId) {
        this.reductionKey = Objects.requireNonNull(reductionKey);
        this.relatedAlarms = new LinkedHashSet<>(relatedAlarms);
        this.vertexId = Objects.requireNonNull(vertexId);
    }

    @Override
    public Set<Alarm> getRelatedAlarms() {
        return relatedAlarms;
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
        return "a situation";
    }

    @Override
    public Date getLastUpdated() {
        return new Date(0);
    }

    @Override
    public String getVertexId() {
        return vertexId;
    }
}
