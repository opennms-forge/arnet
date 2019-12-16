package org.opennms.arnet.api.model;

import java.util.Set;

public interface Situation extends Alarm {

    Set<Alarm> getRelatedAlarms();

}
