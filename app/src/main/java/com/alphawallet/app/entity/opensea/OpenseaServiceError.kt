package com.alphawallet.app.entity.opensea

import com.alphawallet.app.entity.ErrorEnvelope

/**
 * Created by James on 20/12/2018.
 * Stormbird in Singapore
 */
class OpenseaServiceError(message: String?) : Exception(message) {
    val error: ErrorEnvelope = ErrorEnvelope(message)
}
