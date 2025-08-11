package com.alphawallet.app.service

import com.alphawallet.app.entity.QueryResponse
import java.io.IOException

/**
 * Created by JB on 4/11/2022.
 */
interface IPFSServiceType {
    fun getContent(url: String?): String?

    @Throws(IOException::class)
    fun performIO(
        url: String?,
        headers: Array<String?>?,
    ): QueryResponse?
}
