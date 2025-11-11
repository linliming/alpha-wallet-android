package com.alphawallet.app.widget

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.ui.widget.entity.OnQuantityChangedListener
import com.alphawallet.app.ui.widget.entity.QuantitySelectorDialogInterface
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class QuantitySelectorDialog(context: Context, callback: QuantitySelectorDialogInterface) :
    BottomSheetDialog(context), OnQuantityChangedListener {
    private val quantitySelector: QuantitySelector
    private val maxText: TextView
    private val btnMax: TextView
    private val confirmButton: MaterialButton
    private val closeImage: ImageView
    private val context: Context
    private val callback: QuantitySelectorDialogInterface

    init {
        val view = View.inflate(getContext(), R.layout.dialog_quantity_selector, null)
        setContentView(view)
        this.context = context
        this.callback = callback
        maxText = view.findViewById<TextView>(R.id.max_text)
        btnMax = view.findViewById<TextView>(R.id.btn_max)
        confirmButton = view.findViewById<MaterialButton>(R.id.btn_confirm)
        quantitySelector = view.findViewById<QuantitySelector>(R.id.quantity_selector)
        closeImage = view.findViewById<ImageView>(R.id.image_close)
    }

    val quantity: Int
        get() = quantitySelector.getQuantity()

    fun init(balance: Int, position: Int) {
        quantitySelector.init(balance, this)
        maxText.setText(context.getString(R.string.input_amount_max, balance.toString()))
        closeImage.setOnClickListener(View.OnClickListener { v: View? -> cancel() })
        confirmButton.setOnClickListener(View.OnClickListener { v: View? -> confirm(position) })
        btnMax.setOnClickListener(View.OnClickListener { v: View? -> quantitySelector.set(balance) })
        setOnCancelListener(DialogInterface.OnCancelListener { dialogInterface: DialogInterface? ->
            cancel(
                position
            )
        })

        if (quantitySelector.getQuantity() > balance) {
            quantitySelector.reset()
        }
    }

    private fun confirm(position: Int) {
        callback.onConfirm(position, this.quantity)
        dismiss()
    }

    private fun cancel(position: Int) {
        callback.onCancel(position)
    }

    private fun isValid(quantity: Int): Boolean {
        return quantitySelector.isValid(quantity)
    }

    override fun onQuantityChanged(quantity: Int) {
        confirmButton.setEnabled(isValid(quantity))
    }
}
