package com.alphawallet.app.entity

class ServiceErrorException @JvmOverloads constructor(
    val code: ServiceErrorCode,
    message: String? = null,
    throwable: Throwable? = null
) :
    Exception(message, throwable) {
    enum class ServiceErrorCode {
        UNKNOWN_ERROR, INVALID_DATA, KEY_STORE_ERROR, FAIL_TO_SAVE_IV_FILE, KEY_STORE_SECRET, USER_NOT_AUTHENTICATED, KEY_IS_GONE,
        IV_OR_ALIAS_NO_ON_DISK, INVALID_KEY
    }
}
