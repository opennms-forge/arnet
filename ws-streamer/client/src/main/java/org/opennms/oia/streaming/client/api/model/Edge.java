package org.opennms.oia.streaming.client.api.model;

public interface Edge {

    String getId();

    Vertex getSourceVertex();

    Vertex getTargetVertex();

    /**
     * i.e. CDP, LLDP, Bridge
     */
    String getProtocol();

}
