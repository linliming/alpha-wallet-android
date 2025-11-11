package com.alphawallet.app.widget

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.alphawallet.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class AWBottomSheetDialog(context: Context, callback: Callback) :
    BottomSheetDialog(context) {
    private val closeButton: ImageView?
    private val confirmButton: Button?
    private val contentTextView: TextView?
    private val titleTextView: TextView?

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        setContentView(R.layout.layout_dialog_common)

        titleTextView = findViewById(R.id.title)
        contentTextView = findViewById(R.id.content)
        closeButton = findViewById(R.id.close_action)
        confirmButton = findViewById(R.id.button_confirm)
        setCallback(callback)
    }

    private fun setCallback(listener: Callback) {
        closeButton!!.setOnClickListener { v: View? ->
            listener.onClosed()
            dismiss()
        }
        confirmButton!!.setOnClickListener { v: View? ->
            listener.onConfirmed()
            dismiss()
        }
        setOnCancelListener { v: DialogInterface? -> listener.onCancelled() }
    }

    fun setContent(text: String?) {
        contentTextView!!.text = text
    }

    fun setConfirmButton(text: String?) {
        confirmButton!!.text = text
    }

    override fun setTitle(title: CharSequence?) {
        titleTextView!!.text = title
    }

    interface Callback {
        fun onClosed()
        fun onConfirmed()
        fun onCancelled()
    }
}
