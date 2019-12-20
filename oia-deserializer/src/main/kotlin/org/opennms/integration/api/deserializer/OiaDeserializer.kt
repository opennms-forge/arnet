package org.opennms.integration.api.deserializer

import com.fasterxml.jackson.databind.ObjectMapper
import org.opennms.integration.api.deserializer.mixins.AlarmMixin
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm

/**
 * Takes a jackson mapper, copies it, then adds functionality for de-serializing to OIA types using the OIA builders
 * from the OIA common module.
 *
 * The instance returned is intended to be used specifically for de-serializing to OIA types (i.e. for calls to
 * mapper.convertValue()) and may not work for general purpose mapping as it is overriding the mapper's annotation
 * processing.
 * 
 * The mapper passed in is unaffected as it is only copied.
 */
fun getOiaDeserializer(mapper: ObjectMapper): ObjectMapper {
    return mapper.copy().apply {
        this.addOiaMixins()
        setAnnotationIntrospector(OiaIntrospector)
    }
}

private fun ObjectMapper.addOiaMixins() {
    this.addMixIn(ImmutableAlarm.Builder::class.java, AlarmMixin::class.java)
}