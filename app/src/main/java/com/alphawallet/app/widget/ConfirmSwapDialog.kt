package com.alphawallet.app.widget

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import com.alphawallet.app.R
import com.alphawallet.app.entity.lifi.Quote
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.Hex.hexToBigInteger
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.math.BigDecimal
import java.math.BigInteger

class ConfirmSwapDialog(activity: Activity) : BottomSheetDialog(activity) {
    private val infoLayout: LinearLayout
    private val btnConfirm: MaterialButton
    private var listener: ConfirmSwapDialogEventListener? = null

    init {
        val view = View.inflate(context, R.layout.dialog_confirm_swap, null)
        setContentView(view)
        infoLayout = view.findViewById(R.id.layout_info)
        btnConfirm = view.findViewById(R.id.btn_confirm)
    }

    constructor(activity: Activity, quote: Quote?, listener: ConfirmSwapDialogEventListener) : this(
        activity,
        quote
    ) {
        this.listener = listener
        btnConfirm.setOnClickListener { v: View? -> listener.onConfirm() }
    }

    constructor(activity: Activity, quote: Quote?) : this(activity) {
        init(quote)
    }

    fun init(quote: Quote?) {
        if (quote != null) {
            infoLayout.removeAllViews()
            infoLayout.addView(buildNetworkDisplayWidget(quote.action!!.fromChainId.toInt()))
            //            infoLayout.addView(buildFeeWidget(quote)); // TODO: Determine fees
            infoLayout.addView(buildGasWidget(quote))
            infoLayout.addView(buildFromWidget(quote))
            infoLayout.addView(buildToWidget(quote))
        }
    }

    private fun buildNetworkDisplayWidget(chainId: Int): NetworkDisplayWidget {
        return NetworkDisplayWidget(context, chainId)
    }

    private fun buildFeeWidget(quote: Quote): SimpleSheetWidget {
        val widget = SimpleSheetWidget(context, "Fee")
        widget.setValue("TODO") // TODO: How to calculate this?
        widget.setCaption("TODO") // TODO
        return widget
    }

    private fun buildGasWidget(quote: Quote): SimpleSheetWidget {
        val request = quote.transactionRequest
        val gas = hexToBigInteger(request!!.gasPrice!!, BigInteger.ZERO).toString()
        val widget = SimpleSheetWidget(context, R.string.label_gas_price)
        widget.setValue(gas)
        return widget
    }

    private fun buildFromWidget(quote: Quote): SimpleSheetWidget {
        val srcAmt: String = BalanceUtils.getScaledValueFixed(
            BigDecimal(quote.action!!.fromAmount),
            quote.action!!.fromToken!!.decimals,
            4
        )
        val srcTkn = quote.action!!.fromToken!!.symbol
        val widget = SimpleSheetWidget(context, R.string.label_from)
        widget.setValue(context.getString(R.string.valueSymbol, srcAmt, srcTkn))
        return widget
    }

    private fun buildToWidget(quote: Quote): SimpleSheetWidget {
        val destAmt: String = BalanceUtils.getScaledValueFixed(
            BigDecimal(quote.estimate!!.toAmountMin),
            quote.action!!.toToken!!.decimals,
            4
        )
        val destTkn = quote.action!!.toToken!!.symbol
        val widget = SimpleSheetWidget(context, R.string.label_to)
        widget.setValue(context.getString(R.string.valueSymbol, destAmt, destTkn))
        return widget
    }

    fun setEventListener(listener: ConfirmSwapDialogEventListener) {
        this.listener = listener
        btnConfirm.setOnClickListener { v: View? -> listener.onConfirm() }
    }

    interface ConfirmSwapDialogEventListener {
        fun onConfirm()
    }
}
