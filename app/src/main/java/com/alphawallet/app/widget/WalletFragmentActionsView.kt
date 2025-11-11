package com.alphawallet.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.alphawallet.app.R

class WalletFragmentActionsView @JvmOverloads constructor(
    context: Context,
    @LayoutRes layoutId: Int = R.layout.layout_dialog_wallet_actions
) : FrameLayout(context), View.OnClickListener {
    private var onCopyWalletAddressClickListener: OnClickListener? = null
    private var onShowMyWalletAddressClickListener: OnClickListener? = null
    private var onAddHideTokensClickListener: OnClickListener? = null
    private var onRenameThisWalletListener: OnClickListener? = null

    init {
        init(layoutId)
    }

    private fun init(@LayoutRes layoutId: Int) {
        LayoutInflater.from(getContext()).inflate(layoutId, this, true)
        findViewById<View?>(R.id.copy_wallet_address_action).setOnClickListener(this)
        findViewById<View?>(R.id.show_my_wallet_address_action).setOnClickListener(this)
        findViewById<View?>(R.id.add_hide_tokens_action).setOnClickListener(this)
        findViewById<View?>(R.id.rename_this_wallet_action).setOnClickListener(this)
    }

    override fun onClick(view: View) {
        if (view.getId() == R.id.copy_wallet_address_action) {
            if (onCopyWalletAddressClickListener != null) {
                onCopyWalletAddressClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.show_my_wallet_address_action) {
            if (onShowMyWalletAddressClickListener != null) {
                onShowMyWalletAddressClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.add_hide_tokens_action) {
            if (onAddHideTokensClickListener != null) {
                onAddHideTokensClickListener!!.onClick(view)
            }
        } else if (view.getId() == R.id.rename_this_wallet_action) {
            if (onRenameThisWalletListener != null) {
                onRenameThisWalletListener!!.onClick(view)
            }
        }
    }

    fun setOnCopyWalletAddressClickListener(onClickListener: OnClickListener?) {
        this.onCopyWalletAddressClickListener = onClickListener
    }

    fun setOnShowMyWalletAddressClickListener(onClickListener: OnClickListener?) {
        this.onShowMyWalletAddressClickListener = onClickListener
    }

    fun setOnAddHideTokensClickListener(onClickListener: OnClickListener?) {
        this.onAddHideTokensClickListener = onClickListener
    }

    fun setOnRenameThisWalletClickListener(onClickListener: OnClickListener?) {
        this.onRenameThisWalletListener = onClickListener
    }
}
