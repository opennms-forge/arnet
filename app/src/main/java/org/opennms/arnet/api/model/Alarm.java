package org.opennms.arnet.api.model;

import java.util.Date;

public interface Alarm {

    enum Severity {
        INDETERMINATE,
        CLEARED,
        NORMAL,
        WARNING,
        MINOR,
        MAJOR,
        CRITICAL
    }

    String getReductionKey();

    Severity getSeverity();

    String getDescription();

    Date getLastUpdated();

    String getVertexId();

}
