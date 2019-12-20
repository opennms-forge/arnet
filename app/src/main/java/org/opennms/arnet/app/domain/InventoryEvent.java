package org.opennms.arnet.app.domain;

import org.opennms.oia.streaming.client.api.model.Event;

import java.util.Objects;

public class InventoryEvent {
    private final Event e;

    public InventoryEvent(Event e) {
        this.e = Objects.requireNonNull(e);
    }
}
