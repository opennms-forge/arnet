package org.opennms.oia.streaming.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.immutables.ImmutableNode

fun getMapper(): ObjectMapper {
    val mapper = jacksonObjectMapper()

    mapper.setAnnotationIntrospector(object : JacksonAnnotationIntrospector() {
        override fun findPOJOBuilder(ac: AnnotatedClass?): Class<*> {
            requireNotNull(ac)

            return when (ac.rawType) {
                Node::class.java -> ImmutableNode.Builder::class.java
                else -> super.findPOJOBuilder(ac)
            }
        }

        override fun findPOJOBuilderConfig(ac: AnnotatedClass?): JsonPOJOBuilder.Value {
            requireNotNull(ac)

            return when (ac.rawType) {
                Node::class.java -> JsonPOJOBuilder.Value("build", "set")
                else -> super.findPOJOBuilderConfig(ac)
            }
        }
    })

    return mapper
}