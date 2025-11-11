package com.alphawallet.app.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.use
import com.alphawallet.app.R
import com.alphawallet.app.repository.CurrencyRepository
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.ui.widget.entity.InputFiatCallback
import com.alphawallet.app.ui.widget.entity.NumericInput

class InputFiatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val amountInput: NumericInput
    private val moreLayout: LinearLayout
    private val icon: ImageView
    private val expandMore: ImageView
    private val symbolText: TextView
    private val subTextValue: TextView
    private val header: StandardHeader
    private var callback: InputFiatCallback? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.item_input_fiat, this, true)

        header = findViewById(R.id.header)
        moreLayout = findViewById(R.id.layout_more_click)
        expandMore = findViewById(R.id.expand_more)
        icon = findViewById(R.id.icon)
        symbolText = findViewById(R.id.symbol)
        amountInput = findViewById(R.id.amount_entry)
        subTextValue = findViewById(R.id.subtext_value)

        attrs?.let { setupAttrs(context, it) }
        setupViewListeners()
        initValues()
    }

    /**
     * Sets the initial currency symbol and flag once the view is inflated.
     */
    private fun initValues() {
        val currencySymbol = TickerService.getCurrencySymbolTxt()
        symbolText.text = currencySymbol
        icon.setImageResource(CurrencyRepository.getFlagByISO(currencySymbol))
    }

    /**
     * Applies XML attributes related to the header and currency interactions.
     */
    private fun setupAttrs(context: Context, attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.InputView, 0, 0).use { typedArray ->
            val showHeader = typedArray.getBoolean(R.styleable.InputView_show_header, true)
            val headerTextId =
                typedArray.getResourceId(R.styleable.InputView_label, R.string.enter_target_price)
            header.setText(headerTextId)
            header.visibility = if (showHeader) View.VISIBLE else View.GONE

            val canChangeCurrency = typedArray.getBoolean(R.styleable.InputView_can_change_currency, true)
            expandMore.visibility = if (canChangeCurrency) VISIBLE else GONE
        }
    }

    /**
     * Attaches event listeners for currency selection and amount input changes.
     */
    private fun setupViewListeners() {
        moreLayout.setOnClickListener { callback?.onMoreClicked() }

        amountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                callback?.onInputChanged(s?.toString().orEmpty())
            }
        })
    }

    /**
     * Registers the handler for user interactions on the view.
     */
    fun setCallback(callback: InputFiatCallback) {
        this.callback = callback
    }

    /**
     * Updates the displayed currency symbol and corresponding flag icon.
     */
    fun setCurrency(symbol: String) {
        icon.setImageResource(CurrencyRepository.getFlagByISO(symbol))
        symbolText.text = symbol
    }

    /**
     * Requests focus for the amount input box to prompt the soft keyboard.
     */
    fun showKeyboard() {
        amountInput.requestFocus()
    }

    /**
     * Updates the additional informational label beneath the input field.
     */
    fun setSubTextValue(text: String) {
        subTextValue.text = text
    }
}
