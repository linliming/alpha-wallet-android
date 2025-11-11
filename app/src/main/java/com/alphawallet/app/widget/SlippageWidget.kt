package com.alphawallet.app.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import com.alphawallet.app.R
import com.alphawallet.app.util.KeyboardUtils.hideKeyboard
import com.alphawallet.app.util.KeyboardUtils.showKeyboard
import com.google.android.material.radiobutton.MaterialRadioButton
import java.math.BigDecimal

class SlippageWidget(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val radioGroup: RadioGroup?
    private val radio1: MaterialRadioButton
    private val radio2: MaterialRadioButton
    private val radio3: MaterialRadioButton
    private val radio4: MaterialRadioButton
    private var editText: EditText

    init {
        inflate(context, R.layout.item_slippage_widget, this)

        radioGroup = findViewById<RadioGroup?>(R.id.radio_group)
        radio1 = findViewById<MaterialRadioButton>(R.id.radio1)
        radio1.setText(SLIPPAGE_VALUE_1)
        radio2 = findViewById<MaterialRadioButton>(R.id.radio2)
        radio2.setText(SLIPPAGE_VALUE_2)
        radio3 = findViewById<MaterialRadioButton>(R.id.radio3)
        radio3.setText(SLIPPAGE_VALUE_3)
        radio4 = findViewById<MaterialRadioButton>(R.id.radio4)
        editText = findViewById<EditText>(R.id.edit_text)

        radio4.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { compoundButton: CompoundButton?, b: Boolean ->
            editText.setEnabled(b)
            if (b) {
                editText.requestFocus()
                showKeyboard(editText)
            } else {
                editText.clearFocus()
                hideKeyboard(editText)
            }
        })
    }

    val slippage: String
        get() {
            if (radio1.isChecked()) {
                return "0.001"
            } else if (radio2.isChecked()) {
                return "0.005"
            } else if (radio3.isChecked()) {
                return "0.01"
            } else {
                val customVal = editText.getText().toString()
                val d = BigDecimal(customVal)
                if (TextUtils.isEmpty(customVal)) {
                    return "0"
                } else {
                    return d.movePointLeft(2).toString()
                }
            }
        }

    companion object {
        private const val SLIPPAGE_VALUE_1 = "0.1%"
        private const val SLIPPAGE_VALUE_2 = "0.5%"
        private const val SLIPPAGE_VALUE_3 = "1%"
    }
}
