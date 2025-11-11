package com.alphawallet.app.widget

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import com.alphawallet.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class TestNetDialog(context: Context, chainId: Long, callback: TestNetDialogCallback) :
    BottomSheetDialog(context) {
    private val confirmButton: Button?
    private val newChainId: Long

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        setContentView(R.layout.layout_dialog_testnet_confirmation)
        confirmButton = findViewById<Button?>(R.id.enable_testnet_action)
        newChainId = chainId
        setCallback(callback)
    }

    private fun setCallback(listener: TestNetDialogCallback) {
        confirmButton!!.setOnClickListener(View.OnClickListener { v: View? ->
            listener.onTestNetDialogConfirmed(newChainId)
            dismiss()
        })
        setOnCancelListener(DialogInterface.OnCancelListener { v: DialogInterface? -> listener.onTestNetDialogCancelled() })
    }

    interface TestNetDialogCallback {
        fun onTestNetDialogClosed()

        fun onTestNetDialogConfirmed(chainId: Long)

        fun onTestNetDialogCancelled() {
        }
    }
}
