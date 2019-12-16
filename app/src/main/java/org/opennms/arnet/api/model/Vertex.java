package org.opennms.arnet.api.model;

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
