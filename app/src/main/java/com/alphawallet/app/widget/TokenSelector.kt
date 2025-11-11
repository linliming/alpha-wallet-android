package com.alphawallet.app.widget

import android.content.Context
import android.content.res.TypedArray
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.entity.lifi.Token
import com.alphawallet.app.util.Utils
import com.google.android.material.button.MaterialButton

/**
 * Token selector component that displays token metadata and accepts amount input.
 */
class TokenSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private val icon: AddressIcon
    private val label: TextView
    private val address: TextView
    private val symbolText: TextView
    private val btnSelectToken: MaterialButton
    private val tokenLayout: LinearLayout
    private val editText: EditText
    private val balance: TextView
    private val maxBtn: TextView
    private val error: TextView

    private var runnable: Runnable? = null
    private var callback: TokenSelectorEventListener? = null
    private var tokenItem: Token? = null

    init {
        inflate(context, R.layout.token_selector, this)
        label = findViewById(R.id.label)
        address = findViewById(R.id.address)
        icon = findViewById(R.id.token_icon)
        symbolText = findViewById(R.id.text_token_symbol)
        btnSelectToken = findViewById(R.id.btn_select_token)
        tokenLayout = findViewById(R.id.layout_token)
        editText = findViewById(R.id.amount_entry)
        balance = findViewById(R.id.balance)
        error = findViewById(R.id.error)
        maxBtn = findViewById(R.id.btn_max)
        maxBtn.setOnClickListener { callback?.onMaxClicked() }

        symbolText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                callback?.onSelectionChanged(tokenItem)
            }
        })

        applyAttributes(context, attrs)
    }

    /**
     * Applies XML attributes to configure label and interactivity.
     */
    private fun applyAttributes(context: Context, attrs: AttributeSet?) {
        val typedArray: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.TokenSelector,
            0,
            0
        )

        try {
            val showLabel = typedArray.getBoolean(R.styleable.TokenSelector_tsShowLabel, true)
            val isEditable = typedArray.getBoolean(R.styleable.TokenSelector_tsEditable, true)
            val showMaxBtn = typedArray.getBoolean(R.styleable.TokenSelector_tsShowMaxButton, true)
            val labelRes = typedArray.getResourceId(R.styleable.TokenSelector_tsLabelRes, R.string.empty)

            label.visibility = if (showLabel) View.VISIBLE else View.GONE
            label.setText(labelRes)

            if (!isEditable) {
                editText.isEnabled = false
                editText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            maxBtn.visibility = if (showMaxBtn) View.VISIBLE else View.GONE
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Clears all token data and hides the selector.
     */
    fun clear() {
        tokenItem = null
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        icon.blankIcon()
        btnSelectToken.visibility = View.VISIBLE
        tokenLayout.visibility = View.GONE
        editText.text?.clear()
        address.setText(R.string.empty)
        balance.visibility = View.INVISIBLE
        error.visibility = View.GONE
        visibility = View.INVISIBLE
    }

    /**
     * Clears current state and shows the selector placeholder.
     */
    fun reset() {
        clear()
        visibility = View.VISIBLE
    }

    /**
     * Binds the selector to the supplied token instance.
     */
    fun init(token: Token) {
        tokenItem = token
        icon.bindData(token.logoURI.orEmpty(), token.chainId, token.address.orEmpty(), token.symbol.orEmpty())
        btnSelectToken.visibility = View.GONE
        tokenLayout.visibility = View.VISIBLE
        symbolText.text = token.symbol
        address.text = Utils.formatAddress(token.address)
        visibility = View.VISIBLE
    }

    /**
     * Connects a listener for selection, amount, and action events.
     */
    fun setEventListener(listener: TokenSelectorEventListener) {
        callback = listener
        btnSelectToken.setOnClickListener { listener.onSelectorClicked() }
        tokenLayout.setOnClickListener { listener.onSelectorClicked() }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                error.visibility = View.GONE
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runnable?.let { handler.removeCallbacks(it) }
            }

            override fun afterTextChanged(s: Editable?) {
                runnable = Runnable { listener.onAmountChanged(s?.toString().orEmpty()) }.also {
                    handler.postDelayed(it, DEBOUNCE_DELAY_MS)
                }
            }
        })
    }

    /**
     * Returns the currently selected token, if any.
     */
    fun getToken(): Token? = tokenItem

    /**
     * Provides the amount entered by the user.
     */
    fun getAmount(): String = editText.text?.toString().orEmpty()

    /**
     * Sets the amount text without triggering selection changes.
     */
    fun setAmount(amount: String) {
        editText.setText(amount)
    }

    /**
     * Clears the amount field.
     */
    fun clearAmount() {
        editText.text?.clear()
    }

    /**
     * Displays the balance string and toggles max button availability.
     */
    fun setBalance(amount: String?) {
        val token = tokenItem ?: return
        val inBalance = !amount.isNullOrEmpty() && amount != "0"
        val display = buildString {
            append(context.getString(R.string.label_balance))
            append(' ')
            append(if (inBalance) amount else "0")
            append(' ')
            append(token.symbol)
        }

        balance.text = display
        balance.visibility = View.VISIBLE
        setMaxButtonEnabled(inBalance)
    }

    /**
     * Shows an error message below the amount field.
     */
    fun setError(message: String?) {
        error.visibility = View.VISIBLE
        error.text = message
    }

    /**
     * Enables or disables the Max button.
     */
    fun setMaxButtonEnabled(enabled: Boolean) {
        maxBtn.isEnabled = enabled
    }

    /**
     * Callbacks that report selector interactions to the hosting component.
     */
    interface TokenSelectorEventListener {
        /** Triggered when the token card or select button is pressed. */
        fun onSelectorClicked()

        /** Triggered when the user modifies the amount input. */
        fun onAmountChanged(amount: String)

        /** Triggered when a new token is bound to the selector. */
        fun onSelectionChanged(token: Token?)

        /** Triggered when the Max button is pressed. */
        fun onMaxClicked()
    }

    companion object {
        private const val DEBOUNCE_DELAY_MS = 1000L
    }
}
