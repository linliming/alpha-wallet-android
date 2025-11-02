package com.alphawallet.app.entity

import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.AssetDefinitionService
import java.math.BigInteger
import java.util.Collections

class TicketRangeElement
    (assetService: AssetDefinitionService, token: Token?, v: BigInteger) {
    @JvmField
    val id: BigInteger
    @JvmField
    var time: Long = 0

    init {
        val timeAttr = assetService.getAttribute(token, v, "time")
        id = v

        time = if (timeAttr != null) {
            timeAttr.value!!.toLong()
        } else {
            0
        }
    }

    companion object {
        fun sortElements(elementList: List<TicketRangeElement>) {
            Collections.sort(
                elementList
            ) { e1: TicketRangeElement, e2: TicketRangeElement ->
                var w1 = e1.time
                var w2 = e2.time
                if (e1.time == 0L && e2.time == 0L) {
                    w1 = e1.id.toLong()
                    w2 = e2.id.toLong()
                }
                java.lang.Long.compare(w1, w2)
            }
        }
    }
}
