package org.opennms.arnet.model

enum class RequestAction {
    SUBSCRIBE
}

data class FilterCriteria(val locations: Set<String>? = null)

data class ArnetRequest(val action: RequestAction, val criteria: FilterCriteria? = null)

// Use these to generate a request
fun subscribeRequest(criteria: FilterCriteria? = null) = ArnetRequest(RequestAction.SUBSCRIBE, criteria)