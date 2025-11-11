package com.alphawallet.app.widget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActionSheetInterface
import com.alphawallet.app.entity.GasPriceSpread
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.TXSpeed
import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmGasSpread
import com.alphawallet.app.repository.entity.RealmTokenTicker
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.GasSettingsActivity
import com.alphawallet.app.ui.widget.entity.GasSpeed
import com.alphawallet.app.ui.widget.entity.GasWidgetInterface
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import io.realm.Realm
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

/**
 * GasWidget in Kotlin
 *
 * This widget is responsible for displaying and managing gas settings for Ethereum transactions.
 * It has been migrated from Java to Kotlin, with added comments and fixes for static analysis issues,
 * and further optimized for idiomatic Kotlin.
 */
class GasWidget @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : LinearLayout(ctx, attrs), Runnable, GasWidgetInterface {

    private var gasSpread: GasPriceSpread? = null
    private var realmGasSpread: RealmGasSpread? = null
    private lateinit var tokensService: TokensService
    private lateinit var customGasLimit: BigInteger // from slider
    private lateinit var initialTxGasLimit: BigInteger // gas limit from dapp
    private lateinit var baseLineGasLimit: BigInteger // candidate gas limit
    private lateinit var presetGasLimit: BigInteger // gas limit for presets
    private lateinit var transactionValue: BigInteger // 'value' from dapp
    private lateinit var adjustedValue: BigInteger // adjusted 'value' for 'send all'
    private lateinit var token: Token
    private lateinit var functionInterface: StandardFunctionInterface
    private lateinit var actionSheetInterface: ActionSheetInterface

    private val handler = Handler(Looper.getMainLooper())

    private val speedText: TextView
    private val timeEstimate: TextView
    private val gasWarning: LinearLayout
    private val speedWarning: LinearLayout

    private var currentGasSpeedIndex = TXSpeed.STANDARD
    private var customNonce: Long = -1
    private var isSendingAll = false
    private var resendGasPrice = BigInteger.ZERO
    var gasEstimateTime: Long = 0

    init {
        inflate(ctx, R.layout.item_gas_settings, this)
        orientation = VERTICAL
        speedText = findViewById(R.id.text_speed)
        timeEstimate = findViewById(R.id.text_time_estimate)
        gasWarning = findViewById(R.id.layout_gas_warning)
        speedWarning = findViewById(R.id.layout_speed_warning)
    }

    /**
     * Sets up the widget for a legacy transaction.
     * This is for chains that don't support EIP1559 or when sending all funds.
     * Gas price selection is not allowed in these cases.
     *
     * @param svs The TokensService instance.
     * @param t The token being sent.
     * @param tx The Web3Transaction object.
     * @param sfi The StandardFunctionInterface instance.
     * @param actionSheetIf The ActionSheetInterface instance.
     */
    fun setupWidget(
        svs: TokensService,
        t: Token,
        tx: Web3Transaction,
        sfi: StandardFunctionInterface,
        actionSheetIf: ActionSheetInterface
    ) {
        this.tokensService = svs
        this.token = t
        this.initialTxGasLimit = tx.gasLimit ?: BigInteger.ZERO
        this.functionInterface = sfi
        this.transactionValue = tx.value ?: BigInteger.ZERO
        this.adjustedValue = tx.value ?: BigInteger.ZERO
        this.isSendingAll = isSendingAll(tx)
        this.customNonce = tx.nonce
        this.actionSheetInterface = actionSheetIf

        baseLineGasLimit = if (this.initialTxGasLimit == BigInteger.ZERO) {
            GasService.getDefaultGasLimit(token, tx)
        } else {
            this.initialTxGasLimit
        }

        presetGasLimit = baseLineGasLimit
        customGasLimit = baseLineGasLimit

        setupGasSpeeds(tx)
        startGasListener()

        if (!tokensService.hasLockedGas(token.tokenInfo.chainId)) {
            findViewById<View>(R.id.edit_text).isVisible = true
            setOnClickListener { openGasSettings() }
        }
    }

    /**
     * Opens the GasSettingsActivity to allow for advanced gas configuration.
     */
    private fun openGasSettings() {
        val baseEth = tokensService.getToken(token.tokenInfo.chainId, token.getWallet())
            ?: tokensService.getToken(token.getWallet(), token.tokenInfo.chainId, token.getAddress())

        baseEth?.let {
            val intent = Intent(context, GasSettingsActivity::class.java).apply {
                putExtra(C.EXTRA_SINGLE_ITEM, currentGasSpeedIndex.ordinal)
                putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
                putExtra(C.EXTRA_CUSTOM_GAS_LIMIT, customGasLimit.toString())
                putExtra(C.EXTRA_GAS_LIMIT_PRESET, presetGasLimit.toString())
                putExtra(C.EXTRA_TOKEN_BALANCE, it.balance.toString())
                putExtra(C.EXTRA_AMOUNT, transactionValue.toString())
                putExtra(C.EXTRA_GAS_PRICE, gasSpread)  // Parcelised
                putExtra(C.EXTRA_NONCE, customNonce)
                putExtra(C.EXTRA_1559_TX, false)
                putExtra(C.EXTRA_MIN_GAS_PRICE, resendGasPrice.toLong())
            }
            actionSheetInterface.gasSelectLauncher()?.launch(intent)
        }
    }

    /**
     * Fetches initial gas speed settings from Realm or sets a default.
     * @param w3tx The Web3Transaction object.
     */
    private fun setupGasSpeeds(w3tx: Web3Transaction) {
        tokensService.getTickerRealmInstance()?.use { realm ->
            val gasReturn = realm.where(RealmGasSpread::class.java)
                .equalTo("chainId", token.tokenInfo.chainId).findFirst()

            if (gasReturn != null) {
                initGasSpeeds(gasReturn)
            } else {
                // Couldn't get current gas. Add a blank custom gas speed node.
                gasSpread = GasPriceSpread(context, w3tx.gasPrice ?: BigInteger.ZERO)
            }
        }

        w3tx.gasPrice?.let {
            if (it > BigInteger.ZERO) {
                gasSpread?.setCustom(it, GasPriceSpread.FAST_SECONDS)
            }
        }
    }

    /**
     * Configures the view for a resend or cancel transaction action.
     * @param mode The action mode (e.g., CANCEL_TRANSACTION).
     * @param gasPrice The minimum gas price required for the resend.
     */
    override fun setupResendSettings(mode: ActionSheetMode?, gasPrice: BigInteger?) {
        resendGasPrice = gasPrice ?: BigInteger.ZERO
        findViewById<TextView>(R.id.text_speedup_note).apply {
            text = when (mode) {
                ActionSheetMode.CANCEL_TRANSACTION -> resources.getString(R.string.text_cancel_note)
                else -> resources.getString(R.string.text_speedup_note)
            }
            isVisible = true
        }
    }

    /**
     * Cleans up resources, removing Realm change listeners.
     */
    override fun onDestroy() {
        realmGasSpread?.removeAllChangeListeners()
    }

    /**
     * Callback from GasSettingsActivity after the user selects a gas speed.
     *
     * @param gasSelectionIndex Index of the selected gas speed (TXSpeed ordinal).
     * @param maxFeePerGas The chosen maxFeePerGas price.
     * @param maxPriorityFee The chosen maxPriorityFee price.
     * @param customGasLimit The custom gas limit, if set.
     * @param expectedTxTime The estimated transaction time in seconds.
     * @param customNonce The custom nonce, if set.
     */
    override fun setCurrentGasIndex(
        gasSelectionIndex: Int,
        maxFeePerGas: BigInteger?,
        maxPriorityFee: BigInteger?,
        customGasLimit: BigDecimal?,
        expectedTxTime: Long,
        customNonce: Long
    ) {
        if (gasSelectionIndex < TXSpeed.values().size) {
            currentGasSpeedIndex = TXSpeed.values()[gasSelectionIndex]
        }

        this.customNonce = customNonce
        this.customGasLimit = customGasLimit?.toBigInteger() ?: this.customGasLimit

        maxFeePerGas?.let {
            if (it > BigInteger.ZERO) {
                gasSpread?.setCustom(it, expectedTxTime)
            }
        }

        tokensService.track(currentGasSpeedIndex.name)
        handler.post(this)
    }

    /**
     * Validates if the user's balance is sufficient to cover the transaction value and network fee.
     * @return true if funds are sufficient, false otherwise.
     */
    override fun checkSufficientGas(): Boolean {
        val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex) ?: return false
        val networkFee = BigDecimal(gs.gasPrice.maxFeePerGas).multiply(BigDecimal(useGasLimit))
        val base = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet()) ?: return false

        val sufficientGas = when {
            isSendingAll -> token.balance >= BigDecimal(adjustedValue).add(networkFee)
            token.isEthereum() -> token.balance >= BigDecimal(transactionValue).add(networkFee)
            else -> base.balance >= networkFee
        }

        gasWarning.isVisible = !sufficientGas
        return sufficientGas
    }

    /**
     * Determines which gas limit to use based on the current selection.
     * @return The gas limit to be used for the transaction.
     */
    private val useGasLimit: BigInteger
        get() = if (currentGasSpeedIndex == TXSpeed.CUSTOM) customGasLimit else presetGasLimit

    /**
     * If 'isSendingAll' is true, this calculates the maximum value that can be sent after deducting the network fee.
     * @return The adjusted value for the transaction.
     */
    private fun calculateSendAllValue(): BigInteger {
        if (!isSendingAll) return transactionValue

        val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex) ?: return transactionValue
        val networkFee = BigDecimal(gs.gasPrice.maxFeePerGas).multiply(BigDecimal(useGasLimit))

        val sendAllValue = token.balance.subtract(networkFee).toBigInteger()
        return if (sendAllValue < BigInteger.ZERO) BigInteger.ZERO else sendAllValue
    }

    /**
     * Subscribes to Realm for live updates on gas prices for the current chain.
     */
    private fun startGasListener() {
        realmGasSpread?.removeAllChangeListeners()
        realmGasSpread = tokensService.getTickerRealmInstance()?.where(RealmGasSpread::class.java)
            ?.equalTo("chainId", token.tokenInfo.chainId)
            ?.findFirstAsync()

        realmGasSpread?.addChangeListener { rgs: RealmGasSpread? ->
            if (rgs != null && rgs.isValid) {
                initGasSpeeds(rgs)
            }
        }
    }

    /**
     * Initializes gas speeds from a RealmGasSpread object.
     * @param rgs The RealmGasSpread object from the database.
     */
    private fun initGasSpeeds(rgs: RealmGasSpread) {
        try {
            gasSpread = GasPriceSpread(context, gasSpread, rgs.timeStamp, rgs.getGasFees(), rgs.isLocked())
            gasEstimateTime = rgs.timeStamp

            if (gasSpread?.hasLockedGas() == true && findViewById<View>(R.id.edit_text).isVisible) {
                findViewById<View>(R.id.edit_text).isVisible = false
                setOnClickListener(null)
            }
            handler.post(this)
        } catch (e: Exception) {
            currentGasSpeedIndex = TXSpeed.STANDARD
            Timber.e(e, "Error initializing gas speeds")
        }
    }

    /**
     * Main UI update runnable. Recalculates and displays the gas fee in both native currency and fiat.
     */
    override fun run() {
        val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex) ?: return
        if (gs.gasPrice?.maxFeePerGas == null) return

        updateGasFeeDisplay(gs)

        adjustedValue = calculateSendAllValue()
        if (isSendingAll) {
            functionInterface.updateAmount()
        }

        if (currentGasSpeedIndex == TXSpeed.CUSTOM) {
            checkCustomGasPrice(gs.gasPrice.maxFeePerGas)
        } else {
            speedWarning.isVisible = false
        }

        checkSufficientGas()
        manageWarnings()

        if (gasPriceReady(gasEstimateTime)) {
            actionSheetInterface.gasEstimateReady()
            setGasReadyStatus(true)
        } else {
            setGasReadyStatus(false)
        }
    }

    /**
     * Updates the text views that display the calculated gas fee.
     * @param gs The currently selected GasSpeed.
     */
    private fun updateGasFeeDisplay(gs: GasSpeed) {
        val baseCurrency = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet()) ?: return
        val networkFee = BigDecimal(gs.gasPrice.maxFeePerGas).multiply(BigDecimal(useGasLimit))

        var gasAmountInBase = BalanceUtils.getSlidingBaseValue(networkFee, baseCurrency.tokenInfo.decimals, GasSettingsActivity.GAS_PRECISION)
        if (gasAmountInBase == "0") gasAmountInBase = "0.0001"
        var displayStr = context.getString(R.string.gas_amount, gasAmountInBase, baseCurrency.getSymbol())

        try {
            tokensService.getTickerRealmInstance()?.use { realm ->
                val rtt = realm.where(RealmTokenTicker::class.java)
                    .equalTo("contract", TokensRealmSource.databaseKey(token.tokenInfo.chainId, "eth"))
                    .findFirst()

                if (rtt != null) {
                    rtt.price?.toDoubleOrNull()?.let { cryptoRate ->
                        val cryptoAmount = BalanceUtils.weiToEth(networkFee).toDouble()
                        displayStr += context.getString(
                            R.string.gas_fiat_suffix,
                            TickerService.getCurrencyString(cryptoAmount * cryptoRate),
                            rtt.currencySymbol
                        )

                        if (token.tokenInfo.chainId == MAINNET_ID && gs.seconds > 0) {
                            displayStr += context.getString(
                                R.string.gas_time_suffix,
                                Utils.shortConvertTimePeriodInSeconds(gs.seconds, context)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not calculate fiat value for gas")
        }

        timeEstimate.text = displayStr
        speedText.text = gs.speed
    }

    override fun gasPriceReady(): Boolean = gasPriceReady(gasEstimateTime)

    /**
     * Checks if the custom gas price is too high or too low compared to network estimates.
     * @param customGasPrice The user-defined gas price.
     */
    private fun checkCustomGasPrice(customGasPrice: BigInteger) {
        gasSpread?.let { gasSpread ->
            val dGasPrice = customGasPrice.toDouble()
            val ug = gasSpread.getQuickestGasSpeed()
            val lg = gasSpread.getSlowestGasSpeed()

            if (lg == null || ug == null) {
                return@let
            }

            val lowerBound = lg.gasPrice.maxFeePerGas.toDouble()
            val upperBound = ug.gasPrice.maxFeePerGas.toDouble()

            when {
                resendGasPrice > BigInteger.ZERO -> {
                    if (dGasPrice > 3.0 * resendGasPrice.toDouble()) {
                        showCustomSpeedWarning(isHigh = true)
                    } else {
                        speedWarning.isVisible = false
                    }
                }
                dGasPrice < lowerBound -> showCustomSpeedWarning(isHigh = false)
                dGasPrice > 2.0 * upperBound -> showCustomSpeedWarning(isHigh = true)
                else -> speedWarning.isVisible = false
            }
        }
    }

    /**
     * Toggles the visibility of the gas fetching progress indicator.
     * @param ready true to hide progress, false to show it.
     */
    private fun setGasReadyStatus(ready: Boolean) {
        findViewById<View>(R.id.view_spacer).isVisible = ready
        findViewById<View>(R.id.gas_fetch_wait).isVisible = !ready
    }

    /**
     * Shows a warning if the custom gas price is unusually high or low.
     * @param isHigh true for a high price warning, false for a low price warning.
     */
    private fun showCustomSpeedWarning(isHigh: Boolean) {
        if (currentGasSpeedIndex != TXSpeed.CUSTOM) {
            speedWarning.isVisible = false
            return
        }

        speedWarning.isVisible = true
        findViewById<TextView>(R.id.text_speed_warning).text = if (isHigh) {
            resources.getString(R.string.speed_high_gas)
        } else {
            resources.getString(R.string.speed_too_low)
        }
    }

    /**
     * Manages the visibility of gas-related warnings to prevent them from overlapping.
     */
    private fun manageWarnings() {
        val anyWarningVisible = gasWarning.isVisible || speedWarning.isVisible
        speedText.isVisible = !anyWarningVisible
        if (gasWarning.isVisible && speedWarning.isVisible) {
            // gasWarning has priority
            speedWarning.isVisible = false
        }
    }

    override fun isSendingAll(tx: Web3Transaction?): Boolean {
        return tx?.let { token.isEthereum() && it.leafPosition == -2L } ?: false
    }

    override val value: BigInteger
        get() = if (isSendingAll) adjustedValue else transactionValue

    override val gasLimit: BigInteger
        get() = useGasLimit

    override val nonce: Long
        get() = if (currentGasSpeedIndex == TXSpeed.CUSTOM) customNonce else -1

    override val gasMax: BigInteger
        get() = BigInteger.ZERO

    override val priorityFee: BigInteger
        get() = BigInteger.ZERO

    override val gasPrice: BigInteger?
        get() = gasSpread?.takeIf { it.isResultValid() }
            ?.getSelectedGasFee(currentGasSpeedIndex)?.gasPrice?.maxFeePerGas

    override fun getGasPrice(defaultPrice: BigInteger?): BigInteger? {
        return gasPrice ?: defaultPrice
    }

    override val expectedTransactionTime: Long
        get() = gasSpread?.getSelectedGasFee(currentGasSpeedIndex)?.seconds ?: 0

    override fun setGasEstimate(estimate: BigInteger?) {
        estimate?.let {
            var newEstimate = it
            // some kind of contract interaction
            if (!isSendingAll && it > C.GAS_LIMIT_MIN.toBigInteger()) {
                // increase estimate by 20% to be safe
                newEstimate = it.multiply(BigInteger.valueOf(6)).divide(BigInteger.valueOf(5))
            }
            setGasEstimateExact(newEstimate)
        }
    }

    /**
     * Applies the received gas estimate to the current settings.
     * @param estimate The exact gas limit to apply.
     */
    override fun setGasEstimateExact(estimate: BigInteger?) {
        estimate?.let { est ->
            // Override custom gas limit if it was a default
            if (customGasLimit == baseLineGasLimit) {
                customGasLimit = est
            }
            // If the original transaction had no gas limit, use the estimate
            if (initialTxGasLimit == BigInteger.ZERO) {
                baseLineGasLimit = est
            }

            // Presets always use the estimate if available
            presetGasLimit = est

            // Update UI
            handler.post(this)
        }
    }
}
