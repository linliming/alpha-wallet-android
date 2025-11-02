package com.alphawallet.app.entity

import org.web3j.protocol.core.Response

/**
 * Created by JB on 23/04/2022.
 */
class LogOverflowException(val error: Response.Error) : Exception(
    error.message
)
