package org.opennms.arnet.model

data class Situation(val relatedAlarms: Set<Alarm>, val alarm: Alarm)