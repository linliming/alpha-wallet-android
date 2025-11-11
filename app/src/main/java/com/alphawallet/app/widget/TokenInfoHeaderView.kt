package com.alphawallet.app.widget

import android.content.Context
import android.util.Pair
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import java.math.BigDecimal
import java.math.RoundingMode

class TokenInfoHeaderView(context: Context?) : LinearLayout(context) {
    private val icon: TokenIcon
    private val amount: TextView
    private val symbol: TextView
    private val marketValue: TextView
    private val priceChange: TextView

    init {
        inflate(context, R.layout.item_token_info_header, this)
        icon = findViewById<TokenIcon>(R.id.token_icon)
        amount = findViewById<TextView>(R.id.token_amount)
        symbol = findViewById<TextView>(R.id.token_symbol)
        marketValue = findViewById<TextView>(R.id.market_value)
        priceChange = findViewById<TextView>(R.id.price_change)
    }

    constructor(context: Context?, token: Token, svs: TokensService) : this(context) {
        icon.bindData(token)
        if (!token.isEthereum()) icon.setChainIcon(token.tokenInfo.chainId)
        setAmount(token.getFixedFormattedBalance())
        setSymbol(token.tokenInfo.symbol)
        //obtain from ticker
        val pricePair: Pair<Double?, Double?> =
            svs.getFiatValuePair(token.tokenInfo.chainId, token.getAddress())

        setMarketValue(pricePair.first!!)
        setPriceChange(pricePair.second!!)
    }

    fun setAmount(text: String?) {
        amount.setText(text)
    }

    fun setSymbol(text: String?) {
        symbol.setText(text)
    }

    fun setMarketValue(value: Double) {
        val formattedValue = TickerService.getCurrencyString(value)
        marketValue.setText(formattedValue)
    }

    /**
     *
     * Automatically formats the string based on the passed value
     *
     */
    private fun setPriceChange(percentChange24h: Double) {
        try {
            priceChange.setVisibility(VISIBLE)
            val color = ContextCompat.getColor(
                getContext(),
                if (percentChange24h < 0) R.color.negative else R.color.positive
            )
            val percentChangeBI =
                BigDecimal.valueOf(percentChange24h).setScale(3, RoundingMode.DOWN)
            val formattedPercents =
                (if (percentChange24h < 0) "(" else "(+") + percentChangeBI + "%)"
            priceChange.setText(formattedPercents)
            priceChange.setTextColor(color)
        } catch (ex: Exception) { /* Quietly */
        }
    }
}
