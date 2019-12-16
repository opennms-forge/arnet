package org.opennms.arnet.api.model;

import java.util.Date;

public interface Event {

    String getUEI();

    String getDescription();

    Date getTime();

    String getVertexId();

}
