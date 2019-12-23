package org.opennms.integration.api.deserializer.mixins

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties("situation")
class AlarmMixin