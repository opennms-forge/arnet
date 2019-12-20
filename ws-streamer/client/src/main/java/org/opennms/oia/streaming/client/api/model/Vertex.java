package org.opennms.oia.streaming.client.api.model;

public interface Vertex {

    enum Type {
        Node,
        Port,
        Segment
    }

    String getId();

    String getLabel();

    Type getType();

}
