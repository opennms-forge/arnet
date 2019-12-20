package org.opennms.oia.streaming.client.api.model;

import java.util.Date;

public interface Event {

    String getUEI();

    String getDescription();

    Date getTime();

    String getVertexId();

}
