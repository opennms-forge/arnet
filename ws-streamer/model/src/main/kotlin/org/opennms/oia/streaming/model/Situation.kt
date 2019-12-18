package org.opennms.oia.streaming.model

import org.opennms.oia.streaming.model.Alarm

data class Situation(val relatedAlarms: Set<Alarm>, val alarm: Alarm)