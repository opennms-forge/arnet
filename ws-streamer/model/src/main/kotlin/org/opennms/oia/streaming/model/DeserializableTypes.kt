package org.opennms.oia.streaming.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import org.opennms.integration.api.v1.model.immutables.ImmutableNode

@JsonDeserialize(builder = ImmutableNode.Builder::class)
@JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
interface DeserializableNode