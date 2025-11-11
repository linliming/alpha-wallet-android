package com.alphawallet.app.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import android.widget.Toast
import com.alphawallet.app.R
import com.alphawallet.app.util.Utils
import com.google.android.material.button.MaterialButton

class CopyTextView(private val context: Context, attrs: AttributeSet) :
    LinearLayout(context, attrs) {
    private var button: MaterialButton = TODO()
    private var textResId = 0
    private var gravity = 0
    private var lines = 0
    private var showToast = false
    private var boldFont = false
    private var removePadding = false
    private var marginRight = 0f
    private var originalText: String? = null

    init {
        inflate(context, R.layout.item_copy_textview, this)

        getAttrs(context, attrs)

        bindViews()
    }

    private fun getAttrs(context: Context, attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.CopyTextView,
            0, 0
        )

        try {
            textResId = a.getResourceId(R.styleable.CopyTextView_text, R.string.action_add_wallet)
            gravity = a.getInt(R.styleable.CopyTextView_android_gravity, Gravity.NO_GRAVITY)
            showToast = a.getBoolean(R.styleable.CopyTextView_showToast, true)
            boldFont = a.getBoolean(R.styleable.CopyTextView_bold, false)
            removePadding = a.getBoolean(R.styleable.CopyTextView_removePadding, false)
            marginRight = a.getDimension(R.styleable.CopyTextView_marginRight, 0.0f)
            lines = a.getInt(R.styleable.CopyTextView_lines, 1)
        } finally {
            a.recycle()
        }
    }

    private fun bindViews() {
        if (lines > 1) {
            button = findViewById(R.id.button_address)
            findViewById<View>(R.id.button).visibility = GONE
            button.setVisibility(VISIBLE)
            button.setLines(lines)
        } else {
            button = findViewById(R.id.button)
        }

        text = getContext().getString(textResId)
        button.setOnClickListener(OnClickListener { v: View? -> copyToClipboard() })
    }

    var text: String?
        get() = originalText
        set(text) {
            originalText = text.toString()

            visibility = if (TextUtils.isEmpty(originalText)) GONE else VISIBLE

            if (Utils.isAddressValid(originalText)) {
                button!!.text = Utils.formatAddress(originalText, 10)
            } else if (Utils.isTxHashValid(originalText)) {
                button!!.text = Utils.formatTxHash(originalText, 10)
            } else {
                button!!.text = originalText
            }
        }

    fun setFixedText(text: CharSequence) {
        originalText = text.toString()

        visibility =
            if (TextUtils.isEmpty(originalText)) GONE else VISIBLE

        if (Utils.isAddressValid(originalText)) {
            button!!.text = Utils.splitAddress(originalText, lines)
        } else if (Utils.isDivisibleString(originalText)) {
            button!!.text = Utils.splitHex(originalText, lines)
        } else if (Utils.isTxHashValid(originalText)) {
            button!!.text = Utils.formatTxHash(originalText, 10)
        } else {
            button!!.text = originalText
        }
    }

    private fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(KEY_ADDRESS, originalText)
        clipboard?.setPrimaryClip(clip)

        if (showToast) {
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val KEY_ADDRESS: String = "key_address"
    }
}
