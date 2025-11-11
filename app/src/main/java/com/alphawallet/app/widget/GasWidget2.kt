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
import com.alphawallet.app.entity.TXSpeed
import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.Realm1559Gas
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
import io.realm.RealmQuery
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

/**
 * EIP-1559 Gas Widget
 *
 * This widget is responsible for displaying and managing gas settings for EIP-1559 transactions.
 */
class GasWidget2 @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null, override val gasPrice: BigInteger?
) : LinearLayout(ctx, attrs), Runnable, GasWidgetInterface {

    private var gasSpread: GasPriceSpread? = null
    private var realmGasSpread: Realm1559Gas? = null
    private lateinit var tokensService: TokensService
    private lateinit var customGasLimit: BigInteger // from slider
    private lateinit var presetGasLimit: BigInteger // gas limit for presets
    private lateinit var transactionValue: BigInteger // 'value' from dapp
    private lateinit var initialGasPrice: BigInteger // gasprice from dapp
    private lateinit var token: Token
    private lateinit var actionSheetInterface: ActionSheetInterface

    private val handler = Handler(Looper.getMainLooper())

    private val speedText: TextView
    private val timeEstimate: TextView
    private val gasWarning: LinearLayout
    private val speedWarning: LinearLayout

    private var currentGasSpeedIndex = TXSpeed.STANDARD
    private var customNonce: Long = -1
    private var resendGasPrice = BigInteger.ZERO
    private var gasEstimateTime: Long = 0

    init {
        inflate(context, R.layout.item_gas_settings, this)
        orientation = VERTICAL
        speedText = findViewById(R.id.text_speed)
        timeEstimate = findViewById(R.id.text_time_estimate)
        gasWarning = findViewById(R.id.layout_gas_warning)
        speedWarning = findViewById(R.id.layout_speed_warning)
    }

    /**
     * Sets up the widget for an EIP-1559 transaction.
     */
    fun setupWidget(svs: TokensService, t: Token, tx: Web3Transaction, actionSheetIf: ActionSheetInterface) {
        this.tokensService = svs
        this.token = t
        this.transactionValue = tx.value ?: BigInteger.ZERO
        this.initialGasPrice = tx.gasPrice ?: BigInteger.ZERO
        this.customNonce = tx.nonce
        this.actionSheetInterface = actionSheetIf

        val gasLimitFromTx = tx.gasLimit
        presetGasLimit = if (gasLimitFromTx == null || gasLimitFromTx == BigInteger.ZERO) {
            GasService.getDefaultGasLimit(token, tx)
        } else {
            gasLimitFromTx
        }

        customGasLimit = presetGasLimit

        setupGasSpeeds(tx)
        startGasListener()

        if (!tokensService.hasLockedGas(token.tokenInfo.chainId)) {
            findViewById<View>(R.id.edit_text).isVisible = true
            setOnClickListener { openGasSettings() }
        }
    }

    private fun openGasSettings() {
        val baseEth = tokensService.getToken(token.tokenInfo.chainId, token.getWallet())
        val intent = Intent(context, GasSettingsActivity::class.java).apply {
            putExtra(C.EXTRA_SINGLE_ITEM, currentGasSpeedIndex.ordinal)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_CUSTOM_GAS_LIMIT, customGasLimit.toString())
            putExtra(C.EXTRA_GAS_LIMIT_PRESET, presetGasLimit.toString())
            baseEth?.let { putExtra(C.EXTRA_TOKEN_BALANCE, it.balance.toString()) }
            putExtra(C.EXTRA_AMOUNT, transactionValue.toString())
            putExtra(C.EXTRA_GAS_PRICE, gasSpread) //Parcelised
            putExtra(C.EXTRA_NONCE, customNonce)
            putExtra(C.EXTRA_1559_TX, true)
            putExtra(C.EXTRA_MIN_GAS_PRICE, resendGasPrice.toLong())
        }
        actionSheetInterface.gasSelectLauncher()?.launch(intent)
    }

    private fun setupGasSpeeds(w3tx: Web3Transaction) {
        tokensService.getTickerRealmInstance()?.use { realm ->
            val gasReturn = realm.where(Realm1559Gas::class.java)
                .equalTo("chainId", token.tokenInfo.chainId).findFirst()

            if (gasReturn != null) {
                initGasSpeeds(gasReturn)
            } else {
                // Couldn't get current gas. Add a blank custom gas speed node
                gasSpread = GasPriceSpread(context, w3tx.maxFeePerGas ?: BigInteger.ZERO, w3tx.maxPriorityFeePerGas ?: BigInteger.ZERO)
            }
        }

        w3tx.maxFeePerGas?.let { maxFee ->
            w3tx.maxPriorityFeePerGas?.let { priorityFee ->
                if (maxFee > BigInteger.ZERO && priorityFee > BigInteger.ZERO) {
                    gasSpread?.setCustom(maxFee, priorityFee, GasPriceSpread.FAST_SECONDS)
                }
            }
        }
    }

    override fun onDestroy() {
        realmGasSpread?.removeAllChangeListeners()
    }

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

        if (maxFeePerGas != null && maxFeePerGas > BigInteger.ZERO && maxPriorityFee != null && maxPriorityFee > BigInteger.ZERO) {
            gasSpread?.setCustom(maxFeePerGas, maxPriorityFee, expectedTxTime)
        }

        tokensService.track(currentGasSpeedIndex.name)
        handler.post(this)
    }

    override fun checkSufficientGas(): Boolean {
        val useGasLimit = this.gasLimit ?: return false
        val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex) ?: return false

        //Calculate total network fee here:
        val networkFee = BigDecimal(gs.gasPrice.maxFeePerGas).multiply(BigDecimal(useGasLimit))
        val base = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet()) ?: return false

        val sufficientGas = if (token.isEthereum()) {
            token.balance >= BigDecimal(transactionValue).add(networkFee)
        } else {
            base.balance >= networkFee
        }

        gasWarning.isVisible = !sufficientGas
        return sufficientGas
    }

    private fun getLegacyGasPrice(): BigInteger {
        var legacyPrice = initialGasPrice
        try {
            tokensService.getTickerRealmInstance()?.use { realm ->
                val rgs = realm.where(RealmGasSpread::class.java)
                    .equalTo("chainId", token.tokenInfo.chainId).findFirst()

                if (rgs != null) {
                    legacyPrice = rgs.getGasFee(TXSpeed.STANDARD)
                }
            }
        } catch (e: Exception) {
            // e.printStackTrace()
        }

        return legacyPrice
    }

    private fun getGasQuery2(): RealmQuery<Realm1559Gas>? {
        return tokensService.getTickerRealmInstance()?.where(Realm1559Gas::class.java)
            ?.equalTo("chainId", token.tokenInfo.chainId)
    }

    private fun startGasListener() {
        realmGasSpread?.removeAllChangeListeners()
        realmGasSpread = getGasQuery2()?.findFirstAsync()
        realmGasSpread?.addChangeListener { realmSpread: Realm1559Gas? ->
            if (realmSpread != null && realmSpread.isValid) {
                initGasSpeeds(realmSpread)
            }
        }
    }

    private fun initGasSpeeds(gs: Realm1559Gas) {
        try {
            val custom = customGasSpeed
            gasSpread = GasPriceSpread(context, gs.result)
            if (custom != null) {
                gasSpread?.setCustom(custom)
            }
            gasEstimateTime = gs.timeStamp

            handler.post(this)
        } catch (e: Exception) {
            currentGasSpeedIndex = TXSpeed.STANDARD
            Timber.e(e)
        }
    }

    override fun run() {
        val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex) ?: return
        val currentGasLimit = this.gasLimit ?: return

        val baseCurrency = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet()) ?: return
        val networkFee = (gs.gasPrice.baseFee.add(gs.gasPrice.priorityFee)).multiply(currentGasLimit)
        var gasAmountInBase = BalanceUtils.getSlidingBaseValue(BigDecimal(networkFee), baseCurrency.tokenInfo.decimals, GasSettingsActivity.GAS_PRECISION)
        if (gasAmountInBase == "0") gasAmountInBase = "0.0001"
        var displayStr = context.getString(R.string.gas_amount, gasAmountInBase, baseCurrency.getSymbol())

        try {
            tokensService.getTickerRealmInstance()?.use { realm ->
                val rtt = realm.where(RealmTokenTicker::class.java)
                    .equalTo("contract", TokensRealmSource.databaseKey(token.tokenInfo.chainId, "eth"))
                    .findFirst()

                rtt?.price?.toDoubleOrNull()?.let { cryptoRate ->
                    val cryptoAmount = BalanceUtils.weiToEth(BigDecimal(networkFee)).toDouble()
                    displayStr += context.getString(
                        R.string.gas_fiat_suffix,
                        TickerService.getCurrencyString(cryptoAmount * cryptoRate),
                        rtt.currencySymbol
                    )

                    if (token.tokenInfo.chainId == MAINNET_ID && gs.seconds > 0) {
                        displayStr += context.getString(R.string.gas_time_suffix, Utils.shortConvertTimePeriodInSeconds(gs.seconds, context))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }

        timeEstimate.text = displayStr
        speedText.text = gs.speed

        if (currentGasSpeedIndex == TXSpeed.CUSTOM) {
            gasSpread?.getSelectedGasFee(TXSpeed.CUSTOM)?.gasPrice?.maxFeePerGas?.let { checkCustomGasPrice(it) }
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

    private fun setGasReadyStatus(ready: Boolean) {
        findViewById<View>(R.id.view_spacer).isVisible = ready
        findViewById<View>(R.id.gas_fetch_wait).isVisible = !ready
    }

    override fun getGasPrice(defaultPrice: BigInteger?): BigInteger? {
        return if (gasSpread != null && gasSpread?.getSelectedGasFee(currentGasSpeedIndex) != null) {
            val gs = gasSpread!!.getSelectedGasFee(currentGasSpeedIndex)
            gs?.gasPrice?.maxFeePerGas
        } else {
            defaultPrice
        }
    }

    override val gasMax: BigInteger?
        get() {
            val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex)
            return gs?.gasPrice?.maxFeePerGas
        }

    override val priorityFee: BigInteger?
        get() {
            val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex)
            return gs?.gasPrice?.priorityFee
        }

    override fun isSendingAll(tx: Web3Transaction?): Boolean {
        return false
    }

    override fun gasPriceReady(): Boolean {
        return gasPriceReady(gasEstimateTime)
    }

    override val value: BigInteger?
        get() = transactionValue

    private fun checkCustomGasPrice(customGasPrice: BigInteger) {
        val dGasPrice = customGasPrice.toDouble()
        val ug = gasSpread?.getQuickestGasSpeed()
        val lg = gasSpread?.getSlowestGasSpeed()

        if (lg == null || ug == null) return

        if (resendGasPrice > BigInteger.ZERO) {
            if (dGasPrice > 3.0 * resendGasPrice.toDouble()) {
                showCustomSpeedWarning(true)
            } else {
                speedWarning.isVisible = false
            }
        } else if (dGasPrice < lg.gasPrice.maxFeePerGas.toDouble()) {
            showCustomSpeedWarning(false)
        } else if (dGasPrice > 2.0 * ug.gasPrice.maxFeePerGas.toDouble()) {
            showCustomSpeedWarning(true)
        } else {
            speedWarning.isVisible = false
        }
    }

    override fun setupResendSettings(mode: ActionSheetMode?, gasPrice: BigInteger?) {
        resendGasPrice = gasPrice ?: BigInteger.ZERO
        val speedupNote = findViewById<TextView>(R.id.text_speedup_note)
        //If user wishes to cancel transaction, otherwise default is speed it up.
        speedupNote.text = if (mode == ActionSheetMode.CANCEL_TRANSACTION) {
            context.getString(R.string.text_cancel_note)
        } else {
            context.getString(R.string.text_speedup_note)
        }
        speedupNote.isVisible = true
    }

    private fun showCustomSpeedWarning(high: Boolean) {
        val warningText = findViewById<TextView>(R.id.text_speed_warning)

        warningText.text = if (high) {
            resources.getString(R.string.speed_high_gas)
        } else {
            resources.getString(R.string.speed_too_low)
        }
        speedWarning.isVisible = true
    }

    private fun manageWarnings() {
        if (gasWarning.isVisible || speedWarning.isVisible) {
            speedText.isVisible = false
            if (gasWarning.isVisible && speedWarning.isVisible) {
                speedWarning.isVisible = false
            }
        }
    }

    override val gasLimit: BigInteger?
        get() = if (currentGasSpeedIndex == TXSpeed.CUSTOM) customGasLimit else presetGasLimit

    override val nonce: Long
        get() = if (currentGasSpeedIndex == TXSpeed.CUSTOM) customNonce else -1

    override val expectedTransactionTime: Long
        get() {
            val gs = gasSpread?.getSelectedGasFee(currentGasSpeedIndex)
            return gs?.seconds ?: 0
        }

    override fun setGasEstimate(estimate: BigInteger?) {
        estimate?.let {
            var newEstimate = it
            if (customGasLimit != it && it > C.GAS_LIMIT_MIN.toBigInteger()) { //some kind of contract interaction
                newEstimate = it.multiply(BigInteger.valueOf(6)).divide(BigInteger.valueOf(5)) // increase estimate by 20% to be safe
            }
            setGasEstimateExact(newEstimate)
        }
    }

    override fun setGasEstimateExact(estimate: BigInteger?) {
        estimate?.let {
            if (customGasLimit == presetGasLimit) {
                customGasLimit = it
            }

            //presets always use estimate if available
            presetGasLimit = it

            //now update speeds
            handler.post(this)
        }
    }

    private val customGasSpeed: GasSpeed?
        get() = if (gasSpread?.hasCustom() == true) gasSpread?.getCustom() else null
}
