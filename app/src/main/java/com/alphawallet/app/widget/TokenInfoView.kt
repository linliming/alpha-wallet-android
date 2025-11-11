package com.alphawallet.app.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.util.Utils

class TokenInfoView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var label: TextView? = null
    private var value: TextView? = null
    private var valueLongText: TextView? = null
    private var isLink = false
    private var hasPrefix = false

    constructor(context: Context, labelText: String?) : this(context, null as AttributeSet?) {
        label!!.setText(labelText)
        isLink = false
    }

    init {
        inflate(context, R.layout.item_token_info, this)
        getAttrs(context, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.TokenInfoView,
            0, 0
        )

        try {
            val labelRes = a.getResourceId(R.styleable.TokenInfoView_tokenInfoLabel, R.string.empty)
            label = findViewById<TextView>(R.id.label)
            value = findViewById<TextView>(R.id.value)
            valueLongText = findViewById<TextView>(R.id.value_long)

            label!!.setText(labelRes)
        } finally {
            a.recycle()
        }
    }

    fun setLabel(text: String?) {
        label!!.setText(text)
    }

    fun setValue(text: String?) {
        if (!TextUtils.isEmpty(text)) {
            setVisibility(VISIBLE)
            if (text!!.startsWith("http")) {
                setLink()
            }
            val useView = getTextView(text.length)
            useView.setText(text)
        }
    }

    fun setCopyableValue(text: String) {
        if (!TextUtils.isEmpty(text)) {
            setVisibility(VISIBLE)
            var display = text
            // If text is an instance of an address, format it; otherwise do nothing
            if (Utils.isAddressValid(text)) {
                display = Utils.formatAddress(text)
            }
            val useView = getTextView(display.length)
            useView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_copy, 0)
            useView.setText(display)
            setCopyListener(useView, label!!.getText(), text)
        }
    }

    private fun setCopyListener(
        textView: TextView,
        clipLabel: CharSequence?,
        clipValue: CharSequence?
    ) {
        textView.setOnClickListener(OnClickListener { view: View? ->
            val clipboard =
                getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, clipValue))
            Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        })
    }

    fun setCurrencyValue(v: Double) {
        setVisibility(VISIBLE)
        value?.setVisibility(VISIBLE)
        valueLongText!!.setVisibility(GONE)
        val prefix = if (hasPrefix && v > 0) "+" else ""
        value?.text = prefix + TickerService.getFullCurrencyString(v)

        val color =
            ContextCompat.getColor(getContext(), if (v < 0) R.color.negative else R.color.positive)
        value?.setTextColor(color)
    }

    private fun getTextView(length: Int): TextView {
        if (length < 23 || isLink) {
            value!!.setVisibility(VISIBLE)
            valueLongText!!.setVisibility(GONE)
            return value!!
        } else {
            value!!.setVisibility(GONE)
            valueLongText!!.setVisibility(VISIBLE)
            return valueLongText!!
        }
    }

    fun setLink() {
        isLink = true
        value!!.setTextColor(ContextCompat.getColor(getContext(), R.color.brand))
        value!!.setOnClickListener(OnClickListener { v: View? ->
            val url = value!!.getText().toString()
            if (url.startsWith("http")) {
                val i = Intent(Intent.ACTION_VIEW)
                i.setData(Uri.parse(url))
                getContext().startActivity(i)
            }
        })
    }

    fun setHasPrefix(hasPrefix: Boolean) {
        this.hasPrefix = hasPrefix
    }
}
