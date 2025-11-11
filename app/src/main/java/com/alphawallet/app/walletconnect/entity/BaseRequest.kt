package com.alphawallet.app.walletconnect.entity

import com.alphawallet.app.walletconnect.entity.WCEthereumSignMessage.WCSignType
import com.alphawallet.token.entity.Signable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.util.Arrays

abstract class BaseRequest
    (rawParams: String, type: WCSignType) {
    protected var rawParams: String
    private val type: WCSignType
    @JvmField
    protected var params: List<String>? = null

    init {
        Timber.tag(TAG).i(rawParams)

        this.rawParams = rawParams
        this.type = type
        try {
            params = Gson().fromJson(rawParams, object : TypeToken<List<String?>?>() {
            }.type)
        } catch (e: Exception) {
            val unwrapped = unwrap(rawParams)
            val index = unwrapped.indexOf(",")
            params =
                Arrays.asList(unwrap(unwrapped.substring(0, index)), unwrapped.substring(index + 1))
        }
    }

    protected fun unwrap(src: String): String {
        val stringBuilder = StringBuilder(src)
        return stringBuilder.substring(1, stringBuilder.length - 1)
    }

    val message: String
        get() = WCEthereumSignMessage(params!!, type).data

    abstract val signable: Signable?

    open fun getSignable(callbackId: Long, origin: String?): Signable? {
        return null
    }

    abstract val walletAddress: String?

    companion object {
        private val TAG: String = BaseRequest::class.java.name
    }
}
