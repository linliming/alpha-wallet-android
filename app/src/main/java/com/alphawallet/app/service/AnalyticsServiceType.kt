package com.alphawallet.app.service

import com.alphawallet.app.entity.ServiceErrorException

interface AnalyticsServiceType<T> {
    fun increment(property: String?)

    fun track(eventName: String?)

    fun track(
        eventName: String?,
        event: T,
    )

    fun flush()

    fun identify(uuid: String?)

    fun recordException(e: ServiceErrorException?)
}
