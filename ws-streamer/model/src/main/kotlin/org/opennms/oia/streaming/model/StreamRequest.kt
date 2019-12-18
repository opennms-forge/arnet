package org.opennms.oia.streaming.model

import com.fasterxml.jackson.annotation.JsonInclude

enum class RequestAction {
    SUBSCRIBE,
    UNSUBSCRIBE
}

data class FilterCriteria(val locations: Set<String>? = null)

data class StreamRequest(val action: RequestAction, val criteria: FilterCriteria? = null)

// Use these to generate a request
fun subscribeRequest(criteria: FilterCriteria? = null) = StreamRequest(RequestAction.SUBSCRIBE, criteria)
fun unsubscribeRequest(criteria: FilterCriteria? = null) = StreamRequest(RequestAction.UNSUBSCRIBE, criteria)