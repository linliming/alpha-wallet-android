package com.alphawallet.app.entity

class ServiceException(message: String?) : Exception(message) {
    val error: ErrorEnvelope = ErrorEnvelope(message)
}
