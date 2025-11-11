package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.util.BalanceUtils
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Created by Jenny Jingjing Li on 4/3/2021
 */
class BalanceDisplayWidget(context: Context?, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    val balance: TextView
    val newBalance: TextView
    private val tokenIcon: TokenIcon
    private var transaction: Transaction? = null

    init {
        inflate(context, R.layout.item_balance_display, this)
        balance = findViewById(R.id.text_balance)
        newBalance = findViewById(R.id.text_new_balance)
        tokenIcon = findViewById(R.id.token_icon)
    }

    fun setupBalance(token: Token, tx: Transaction?) {
        if (token.isNonFungible()) {
            tokenIcon.visibility = VISIBLE
            tokenIcon.bindData(token)
        } else {
            tokenIcon.visibility = GONE
            balance.text =
                context.getString(
                    R.string.total_cost,
                    token.getStringBalanceForUI(5),
                    token.getSymbol()
                )
        }
        transaction = tx
    }

    fun setNewBalanceText(
        token: Token,
        transactionAmount: BigDecimal?,
        networkFee: BigInteger,
        balanceAfterTransaction: BigInteger
    ) {
        var balanceAfterTransaction = balanceAfterTransaction
        if (token.isEthereum()) {
            balanceAfterTransaction =
                balanceAfterTransaction.subtract(networkFee).max(BigInteger.ZERO)
        } else if (transaction == null || transaction!!.transactionInput == null || transaction!!.transactionInput!!.isSendOrReceive(
                transaction!!
            )
        ) {
            balanceAfterTransaction =
                token.getBalanceRaw().subtract(transactionAmount).toBigInteger()
        } else {
            newBalance.visibility = GONE
            return
        }

        //convert to ETH amount
        val newBalanceVal: String = BalanceUtils.getScaledValueScientific(
            BigDecimal(balanceAfterTransaction),
            token.tokenInfo.decimals.toLong()
        )
        newBalance.text = context.getString(R.string.new_balance, newBalanceVal, token.getSymbol())
    }
}
