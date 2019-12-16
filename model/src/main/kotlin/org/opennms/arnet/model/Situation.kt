package org.opennms.arnet.model

data class Situation(val relatedAlarms: Set<Alarm>, private val alarm: Alarm) : AlarmFields by alarm