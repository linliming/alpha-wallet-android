package com.alphawallet.app.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.alphawallet.app.R
import com.alphawallet.app.ui.widget.entity.OnQuantityChangedListener
import java.math.BigInteger

class QuantitySelector @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    RelativeLayout(context, attrs), TextWatcher, OnEditorActionListener {
    private val quantityText: EditText
    private val incrementBtn: ImageButton
    private val decrementBtn: ImageButton
    val min: Int = 1
    private var listener: OnQuantityChangedListener? = null
    var quantity: Int = 1
        private set
    var max: Int = 0
        private set

    init {
        inflate(context, R.layout.quantity_selector, this)
        quantityText = findViewById<EditText>(R.id.quantity)
        incrementBtn = findViewById<ImageButton>(R.id.number_up)
        decrementBtn = findViewById<ImageButton>(R.id.number_down)
    }

    fun init(maxAmount: Int, listener: OnQuantityChangedListener) {
        this.max = maxAmount
        this.listener = listener
        incrementBtn.setOnClickListener(OnClickListener { v: View? -> increment() })
        decrementBtn.setOnClickListener(OnClickListener { v: View? -> decrement() })
        quantityText.setOnEditorActionListener(this)
        quantityText.addTextChangedListener(this)
        set(quantity)
    }

    fun set(q: Int) {
        quantity = q
        quantityText.getText().clear()
        val qStr = q.toString()
        if (q > 0) {
            quantityText.setText(qStr)
        }
        quantityText.clearFocus()
    }

    fun increment() {
        if (quantity + 1 <= max) {
            quantityText.setText((++quantity).toString())
        }
        quantityText.clearFocus()
    }

    fun decrement() {
        if (quantity - 1 >= min) {
            quantityText.setText((--quantity).toString())
        }
        quantityText.clearFocus()
    }

    fun reset() {
        set(min)
    }

    fun setOnQuantityChangedListener(listener: OnQuantityChangedListener) {
        this.listener = listener
    }

    fun isValid(quantity: Int): Boolean {
        return quantity <= this.max && quantity >= this.min
    }

    override fun beforeTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
    }

    override fun onTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
    }

    override fun afterTextChanged(s: Editable) {
        quantity = if (s.toString().isEmpty()) 0 else BigInteger(s.toString()).toInt()
        listener!!.onQuantityChanged(quantity)
    }

    override fun onEditorAction(textView: TextView?, actionId: Int, keyEvent: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            quantityText.clearFocus()
        }
        return false
    }
}
