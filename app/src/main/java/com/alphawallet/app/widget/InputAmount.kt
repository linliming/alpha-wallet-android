package com.alphawallet.app.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.alphawallet.app.C.GAS_LIMIT_MIN
import com.alphawallet.app.R
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmGasSpread
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.repository.entity.RealmTokenTicker
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.entity.AmountReadyCallback
import com.alphawallet.app.ui.widget.entity.NumericInput
import com.alphawallet.app.util.BalanceUtils
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * A custom view for inputting crypto or fiat amounts, with support for showing available balance,
 * "All Funds" functionality, and dynamic switching between crypto and fiat display.
 * This class has been migrated to idiomatic Kotlin, ensuring null safety and improved readability.
 */
class InputAmount @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val editText: NumericInput
    private val symbolText: TextView
    private val icon: TokenIcon
    private val header: StandardHeader
    private val availableSymbol: TextView
    private val availableAmount: TextView
    private val allFunds: TextView
    private val gasFetch: ProgressBar

    private lateinit var token: Token
    private var realm: Realm? = null
    private var tickerRealm: Realm? = null
    private lateinit var tokensService: TokensService
    private var gasPriceEstimate = BigInteger.ZERO
    private var exactAmount = BigDecimal.ZERO
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var amountReadyCallback: AmountReadyCallback
    private var amountReady = false

    private var realmTickerUpdate: RealmTokenTicker? = null
    private var realmTokenUpdate: RealmToken? = null

    private var showingCrypto = !CustomViewSettings.inputAmountFiatDefault()

    init {
        inflate(context, R.layout.item_input_amount, this)

        editText = findViewById(R.id.amount_entry)
        symbolText = findViewById(R.id.text_token_symbol)
        icon = findViewById(R.id.token_icon)
        header = findViewById(R.id.header)
        availableSymbol = findViewById(R.id.text_symbol)
        availableAmount = findViewById(R.id.text_available)
        allFunds = findViewById(R.id.text_all_funds)
        gasFetch = findViewById(R.id.gas_fetch_progress)

        setupAttrs(context, attrs)
        setupViewListeners()
    }

    /**
     * Initializes the component with a specific token and services.
     * It will still work if assetDefinitionService is null, but some tokens may not display correctly.
     *
     * @param token The token for which the amount is being input.
     * @param svs The service for managing tokens.
     * @param amountCallback The callback to be invoked when the amount is ready or changes.
     */
    fun setupToken(token: Token, svs: TokensService, amountCallback: AmountReadyCallback) {
        this.token = token
        this.tokensService = svs
        this.amountReadyCallback = amountCallback
        icon.bindData(token)
        header.chainName.setChainID(token.tokenInfo.chainId)
        updateAvailableBalance()

        this.realm = tokensService.getWalletRealmInstance()
        this.tickerRealm = tokensService.getTickerRealmInstance()
        bindDataSource()
        setupAllFunds()
    }

    /**
     * Triggers the amount ready callback with the currently entered amount.
     * If the gas price is being fetched, it will wait until the fetch is complete.
     */
    fun getInputAmount() {
        if (gasFetch.isVisible) {
            amountReady = true
        } else {
            // Immediate return
            if (exactAmount > BigDecimal.ZERO) {
                amountReadyCallback.amountReady(exactAmount, BigDecimal(gasPriceEstimate)) // 'All Funds' includes gas price
            } else {
                amountReadyCallback.amountReady(weiInputAmount, BigDecimal.ZERO)
            }
        }
    }

    /**
     * Cleans up resources, including removing Realm change listeners and closing Realm instances.
     */
    fun onDestroy() {
        realmTokenUpdate?.removeAllChangeListeners()
        realmTickerUpdate?.removeAllChangeListeners()

        realm?.let {
            it.removeAllChangeListeners()
            if (!it.isClosed) it.close()
        }

        tickerRealm?.let {
            it.removeAllChangeListeners()
            if (!it.isClosed) it.close()
        }

        realmTickerUpdate = null
        realmTokenUpdate = null
    }

    /**
     * Sets the amount in the input field.
     *
     * @param ethAmount The amount to set, as a string representation.
     */
    fun setAmount(ethAmount: String) {
        exactAmount = BigDecimal.ZERO
        editText.setText(ethAmount)
        handler.post(setCursor)
    }

    /**
     * Shows or hides an error message below the input field.
     *
     * @param showError True to show the error, false to hide it.
     * @param customError A custom error message resource ID, or 0 for the default insufficient funds error.
     */
    fun showError(showError: Boolean, customError: Int) {
        val errorText = findViewById<TextView>(R.id.text_error)
        errorText.text = if (customError != 0) {
            context.getString(customError)
        } else {
            context.getString(R.string.error_insufficient_funds, token.getShortSymbol())
        }

        if (showError) {
            errorText.isVisible = true
            editText.setTextColor(context.getColor(R.color.error))
        } else {
            errorText.isVisible = false
            editText.setTextColor(context.getColor(R.color.text_secondary))
        }
    }

    private fun updateAvailableBalance() {
        if (exactAmount > BigDecimal.ZERO) return

        if (showingCrypto) {
            showCrypto()
        } else {
            showFiat()
        }
    }

    private fun bindDataSource() {
        val tokenAddress = token.getAddress()?.lowercase() ?: return

        realmTokenUpdate?.removeAllChangeListeners()

        realmTokenUpdate = realm?.where(RealmToken::class.java)
            ?.equalTo("address", TokensRealmSource.databaseKey(token.tokenInfo.chainId, tokenAddress), Case.INSENSITIVE)
            ?.findFirstAsync()

        tokensService.storeToken(token)

        realmTokenUpdate?.addChangeListener { realmToken: RealmToken? ->
            if (realmToken != null && realmToken.isValid && exactAmount == BigDecimal.ZERO) {
                tokensService.getToken(realmToken.chainId, realmToken.tokenAddress)?.let {
                    token = it
                    updateAvailableBalance()
                }
            }
        }
    }

    private fun setupViewListeners() {
        findViewById<LinearLayout>(R.id.layout_more_click).setOnClickListener {
            if (getTickerQuery() == null) return@setOnClickListener

            val rtt = getTickerQuery()?.findFirst()
            if (showingCrypto && rtt != null) {
                showingCrypto = false
                startTickerListener()
            } else {
                showingCrypto = true
                tickerRealm?.removeAllChangeListeners() // Stop ticker listener
            }
            updateAvailableBalance()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (editText.hasFocus()) {
                    exactAmount = BigDecimal.ZERO // Invalidate the 'all funds' amount
                    showError(false, 0)
                }
            }

            override fun afterTextChanged(s: Editable) {
                if (editText.hasFocus()) {
                    amountReadyCallback.updateCryptoAmount(weiInputAmount)
                }
            }
        })

        editText.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showError(false, 0)
            }
        }

        editText.setOnClickListener {
            showError(false, 0)
        }
    }

    private fun getTickerQuery(): RealmQuery<RealmTokenTicker>? {
        val contract = if (token.isEthereum()) "eth" else token.getAddress().lowercase()
        return tickerRealm?.where(RealmTokenTicker::class.java)
            ?.equalTo("contract", TokensRealmSource.databaseKey(token.tokenInfo.chainId, contract))
    }

    private fun startTickerListener() {
        val query = getTickerQuery() ?: return
        realmTickerUpdate?.removeAllChangeListeners()
        realmTickerUpdate = query.findFirstAsync()
        realmTickerUpdate?.addChangeListener { _: RealmTokenTicker? ->
            updateAvailableBalance()
        }
    }

    private fun showCrypto() {
        icon.bindData(token)
        symbolText.text = token.getSymbol()
        availableSymbol.text = token.getSymbol()
        availableAmount.text = token.getStringBalanceForUI(5)
        updateAllFundsAmount()
    }

    private fun showFiat() {
        icon.showLocalCurrency()

        try {
            getTickerQuery()?.findFirst()?.let { rtt ->
                val currencyLabel = rtt.currencySymbol + TickerService.getCurrencySymbol()
                symbolText.text = currencyLabel

                val cryptoRate = rtt.price?.toDoubleOrNull() ?: 0.0
                val availableCryptoBalance = token.getCorrectedBalance(18).toDouble()
                availableAmount.text = TickerService.getCurrencyString(availableCryptoBalance * cryptoRate)
                availableSymbol.text = rtt.currencySymbol
                updateAllFundsAmount() // Update amount if showing 'All Funds'

                amountReadyCallback.updateCryptoAmount(weiInputAmount) // Now update
            }
            updateAllFundsAmount()
        } catch (e: Exception) {
            Timber.e(e)
            // continue with old value
        }
    }

    private val weiInputAmount: BigDecimal
        get() {
            val inputVal = editText.bigDecimalValue
            if (inputVal == BigDecimal.ZERO) {
                return inputVal
            }

            return if (showingCrypto) {
                inputVal.multiply(BigDecimal.TEN.pow(token.tokenInfo.decimals))
            } else {
                convertFiatAmountToWei(inputVal.toDouble())
            }
        }

    private fun setupAllFunds() {
        allFunds.setOnClickListener {
            if (token.isEthereum() && token.hasPositiveBalance()) {
                val gasSpread = tokensService.getTickerRealmInstance()?.where(RealmGasSpread::class.java)
                    ?.equalTo("chainId", token.tokenInfo.chainId)
                    ?.findFirst()

                if (gasSpread != null && gasSpread.getGasPrice() > BigInteger.ZERO) {
                    onLatestGasPrice(gasSpread.getGasPrice())
                } else {
                    gasFetch.isVisible = true
                    val web3j = TokenRepository.getWeb3jService(token.tokenInfo.chainId)
                    web3j.ethGasPrice().sendAsync()
                        .thenAccept { onLatestGasPrice(it.gasPrice) }
                        .exceptionally { onGasFetchError(it) }
                }
            } else {
                exactAmount = token.balance
                updateAllFundsAmount()
            }
            handler.post(setCursor)
        }
    }

    private fun onLatestGasPrice(price: BigInteger) {
        gasPriceEstimate = price
        val networkFee = BigDecimal(gasPriceEstimate.multiply(BigInteger.valueOf(GAS_LIMIT_MIN.toLong())))
        exactAmount = token.balance.subtract(networkFee)
        if (exactAmount < BigDecimal.ZERO) exactAmount = BigDecimal.ZERO
        handler.post(updateValue)
    }

    private val updateValue = Runnable {
        gasFetch.isVisible = false
        updateAllFundsAmount()

        if (amountReady) {
            amountReadyCallback.amountReady(exactAmount, BigDecimal(gasPriceEstimate))
            amountReady = false
        }
    }

    private val setCursor = Runnable {
        editText.setSelection(editText.text?.length ?: 0)
    }

    private fun setupAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(
            attrs, R.styleable.InputView, 0, 0
        )
        try {
            header.isVisible = a.getBoolean(R.styleable.InputView_show_header, true)
            allFunds.isVisible = a.getBoolean(R.styleable.InputView_show_allFunds, true)
            header.chainName.isVisible = a.getBoolean(R.styleable.InputView_showChainName, true)
            val currencyMode = a.getBoolean(R.styleable.InputView_currencyMode, false)
            val headerTextId = a.getResourceId(R.styleable.InputView_label, R.string.amount)
            header.setText(headerTextId)
            if (currencyMode) {
                symbolText.text = TickerService.getCurrencySymbolTxt()
                icon.showLocalCurrency()
            }
        } finally {
            a.recycle()
        }
    }

    private fun onGasFetchError(throwable: Throwable): Void? {
        Timber.e(throwable)
        gasFetch.isVisible = false
        return null
    }

    private fun convertWeiAmountToFiat(value: BigDecimal): String {
        var fiatValue = "0"
        try {
            getTickerQuery()?.findFirst()?.let { rtt ->
                val cryptoRate = rtt.price?.toDoubleOrNull() ?: 0.0
                val cryptoAmount = value.divide(BigDecimal.TEN.pow(token.tokenInfo.decimals), 18, RoundingMode.DOWN).toDouble()
                val df = DecimalFormat("#,##0.00").apply {
                    roundingMode = RoundingMode.DOWN
                }
                fiatValue = df.format(cryptoAmount * cryptoRate)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return fiatValue
    }

    private fun convertFiatAmountToWei(fiatValue: Double): BigDecimal {
        try {
            getTickerQuery()?.findFirst()?.let { rtt ->
                val cryptoRate = rtt.price?.toDoubleOrNull() ?: 0.0
                if (cryptoRate > 0) {
                    val ethValue = BigDecimal.valueOf(fiatValue / cryptoRate)
                    return ethValue.multiply(BigDecimal.TEN.pow(token.tokenInfo.decimals))
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return BigDecimal.ZERO
    }

    private fun updateAllFundsAmount() {
        if (exactAmount <= BigDecimal.ZERO) return

        if (showingCrypto) {
            val amountStr = BalanceUtils.getScaledValueFixed(exactAmount, token.tokenInfo.decimals.toLong(), 4)
            editText.setText(amountStr)
        } else {
            val fiatStr = convertWeiAmountToFiat(exactAmount)
            editText.setText(fiatStr)
        }
    }
}
