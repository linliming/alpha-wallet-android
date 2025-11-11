package com.alphawallet.app.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.DialogDismissInterface

class BuyEthOptionsView @JvmOverloads constructor(
    context: Context,
    @LayoutRes layoutId: Int = R.layout.dialog_buy_eth_options
) :
    FrameLayout(context), View.OnClickListener {
    private var onBuyWithCoinbasePayListener: OnClickListener? = null
    private var onBuyWithRampListener: OnClickListener? = null
    private var dismissInterface: DialogDismissInterface? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun init(@LayoutRes layoutId: Int) {
        LayoutInflater.from(context).inflate(layoutId, this, true)
        findViewById<View>(R.id.buy_with_coinbase_pay).setOnClickListener(this)
        findViewById<View>(R.id.buy_with_ramp).setOnClickListener(this)

        //close after 30 seconds of inactivity
        handler.postDelayed(closePopup, C.STANDARD_POPUP_INACTIVITY_DISMISS.toLong())
    }

    private val closePopup = Runnable { dismissInterface!!.dismissDialog() }

    init {
        init(layoutId)
    }

    override fun onClick(view: View) {
        handler.removeCallbacks(closePopup)
        if (view.id == R.id.buy_with_coinbase_pay) {
            if (onBuyWithCoinbasePayListener != null) {
                onBuyWithCoinbasePayListener!!.onClick(view)
            }
        } else if (view.id == R.id.buy_with_ramp) {
            if (onBuyWithRampListener != null) {
                onBuyWithRampListener!!.onClick(view)
            }
        }
    }

    fun setOnBuyWithCoinbasePayListener(onClickListener: OnClickListener?) {
        this.onBuyWithCoinbasePayListener = onClickListener
    }

    fun setOnBuyWithRampListener(onClickListener: OnClickListener?) {
        this.onBuyWithRampListener = onClickListener
    }

    fun setDismissInterface(dismissInterface: DialogDismissInterface?) {
        this.dismissInterface = dismissInterface
    }
}
