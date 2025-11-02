package com.alphawallet.app.api.v1.entity.request

import com.alphawallet.app.api.v1.entity.ApiV1
import com.alphawallet.app.api.v1.entity.Metadata
import com.alphawallet.app.api.v1.entity.Method
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

open class ApiV1Request
    (requestUrl: String?) {
    @JvmField
    protected var requestUrl: HttpUrl? = null
    var method: Method? = null
        protected set
    var redirectUrl: String? = null
        protected set
    var metadata: Metadata? = null
        protected set
    var isValid: Boolean = false
        protected set

    init {
        parse(requestUrl)
    }

    fun parse(urlString: String?) {
        val url = urlString?.toHttpUrlOrNull()
        if (url != null) {
            try {
                val encodedPath = url.encodedPath
                for (method in ApiV1.VALID_METHODS) {
                    if (method.path == encodedPath) {
                        this.isValid = true
                        this.requestUrl = url
                        this.method = method
                        this.redirectUrl = url.queryParameter(ApiV1.RequestParams.REDIRECT_URL)
                        val metadataJson = url.queryParameter(ApiV1.RequestParams.METADATA)
                        this.metadata = Gson().fromJson(
                            metadataJson,
                            Metadata::class.java
                        )
                    }
                }
            } catch (e: Exception) {
                //continue
            }
        } else {
            isValid = false
        }
    }

    fun getRequestUrl(): String {
        return requestUrl.toString()
    }
}
