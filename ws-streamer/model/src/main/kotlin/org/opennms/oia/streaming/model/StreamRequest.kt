package org.opennms.oia.streaming.model

enum class RequestAction {
    SUBSCRIBE,
    UNSUBSCRIBE
}

data class FilterCriteria(val locations: Set<String>? = null)

data class StreamRequest(val action: RequestAction, val criteria: FilterCriteria? = null)

// TODO: its a hack to put these methods inside this object but I was getting unresolved references
//  otherwise
class RequestUtils {
    companion object {
        // Use these to generate a request
        fun subscribeRequest(criteria: FilterCriteria? = null) = StreamRequest(RequestAction.SUBSCRIBE, criteria)
        fun unsubscribeRequest(criteria: FilterCriteria? = null) = StreamRequest(RequestAction.UNSUBSCRIBE, criteria)
    }
}
