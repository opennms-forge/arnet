package org.opennms.oia.streaming.client.api.model;

import java.util.Set;

public interface Situation extends Alarm {

    Set<Alarm> getRelatedAlarms();

}
