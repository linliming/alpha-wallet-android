package com.alphawallet.app.entity

/** A generic functional interface to supply a result of an async operation
 * @param <T> Result required by caller
</T> */
interface GenericCallback<T> {
    fun call(t: T)
}
