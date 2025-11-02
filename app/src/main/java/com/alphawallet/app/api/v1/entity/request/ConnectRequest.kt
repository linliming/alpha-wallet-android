package com.alphawallet.app.api.v1.entity.request

class ConnectRequest(requestUrl: String?) : ApiV1Request(requestUrl) {
    var address: String? = null
}
