package com.alphawallet.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.alphawallet.app.R

class QRCodeActionsView @JvmOverloads constructor(
    context: Context,
    @LayoutRes layoutId: Int = R.layout.layout_dialog_qr_code_actions
) : FrameLayout(context), View.OnClickListener {
    private var onSendToAddressClickListener: OnClickListener? = null
    private var onAddCustonTokenClickListener: OnClickListener? = null
    private var onWatchWalletClickListener: OnClickListener? = null
    private var onOpenInEtherscanClickListener: OnClickListener? = null
    private var onCloseActionListener: OnClickListener? = null

    init {
        init(layoutId)
    }

    private fun init(@LayoutRes layoutId: Int) {
        LayoutInflater.from(getContext()).inflate(layoutId, this, true)
        findViewById<View?>(R.id.send_to_this_address_action).setOnClickListener(this)
        findViewById<View?>(R.id.add_custom_token_action).setOnClickListener(this)
        findViewById<View?>(R.id.watch_account_action).setOnClickListener(this)
        findViewById<View?>(R.id.open_in_etherscan_action).setOnClickListener(this)
        findViewById<View?>(R.id.close_action).setOnClickListener(this)
    }

    override fun onClick(view: View) {
        if (view.getId() == R.id.send_to_this_address_action) {
            if (onSendToAddressClickListener != null) {
                onSendToAddressClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.add_custom_token_action) {
            if (onAddCustonTokenClickListener != null) {
                onAddCustonTokenClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.watch_account_action) {
            if (onWatchWalletClickListener != null) {
                onWatchWalletClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.open_in_etherscan_action) {
            if (onOpenInEtherscanClickListener != null) {
                onOpenInEtherscanClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.close_action) {
            if (onCloseActionListener != null) {
                onCloseActionListener!!.onClick(view)
            }
        }
    }

    fun setOnSendToAddressClickListener(onSendToAddressClickListener: OnClickListener?) {
        this.onSendToAddressClickListener = onSendToAddressClickListener
    }

    fun setOnAddCustonTokenClickListener(onAddCustonTokenClickListener: OnClickListener?) {
        this.onAddCustonTokenClickListener = onAddCustonTokenClickListener
    }

    fun setOnWatchWalletClickListener(onWatchWalletClickListener: OnClickListener?) {
        this.onWatchWalletClickListener = onWatchWalletClickListener
    }

    fun setOnOpenInEtherscanClickListener(onOpenInEtherscanClickListener: OnClickListener?) {
        this.onOpenInEtherscanClickListener = onOpenInEtherscanClickListener
    }

    fun setOnCloseActionListener(onCloseActionListener: OnClickListener?) {
        this.onCloseActionListener = onCloseActionListener
    }
}
