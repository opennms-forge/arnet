package org.opennms.arnet.model

enum class RequestAction {
    INIT
}

data class WSRequest(val action: RequestAction)