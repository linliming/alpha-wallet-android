package com.alphawallet.app.widget

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.preference.PreferenceManager
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.SharedPreferenceRepository
import com.alphawallet.app.repository.entity.Realm1559Gas
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.HomeActivity
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.ui.widget.entity.GasWidgetInterface
import com.alphawallet.app.util.stillAvailable
import com.alphawallet.app.walletconnect.entity.WCPeerMeta
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Created by JB on 17/11/2020.
 * Migrated to Kotlin.
 */
class ActionSheetDialog : ActionSheet, StandardFunctionInterface, ActionSheetInterface {

    // --- View Properties ---
    private val toolbar: BottomSheetToolbarView?
    private val gasWidget: GasWidget2?
    private val gasWidgetLegacy: GasWidget?
    private val balanceDisplay: BalanceDisplayWidget?
    private val networkDisplay: NetworkDisplayWidget?
    private val confirmationWidget: ConfirmationWidget?
    private val senderAddressDetail: AddressDetailView?
    private val receiptAddressDetail: AddressDetailView?
    private val amountDisplay: AmountDisplayWidget?
    private val assetDetailView: AssetDetailView?
    private val functionBar: FunctionButtonBar?
    private val detailWidget: TransactionDetailWidget?
    private val walletConnectRequestWidget: WalletConnectRequestWidget?

    // --- Controller/Data Properties ---
    private val gasWidgetInterface: GasWidgetInterface?
    private val token: Token?
    private val tokensService: TokensService?
    private val candidateTransaction: Web3Transaction?
    private val actionSheetCallback: ActionSheetCallback?
    private val callbackId: Long
    private val walletType: WalletType
    private val transactionDetail: Transaction?

    override val transaction: Web3Transaction
        get() = candidateTransaction ?: throw IllegalStateException("Transaction not available in this context")

    // --- State Properties ---
    private var signCallback: SignAuthenticationCallback? = null
    private var mode: ActionSheetMode
    private var txHash: String? = null
    private var actionCompleted = false
    private var use1559Transactions = false
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backupGasFetchJob: Job? = null
    private var gasToUse: EIP1559FeeOracleResult? = null
    private var establishedTransaction: Web3Transaction? = null // Final tx to be submitted

    /**
     * Constructor 1: Send Transaction / DApp Transaction
     */
    constructor(
        activity: Activity, tx: Web3Transaction, t: Token,
        destName: String, destAddress: String, ts: TokensService,
        aCallBack: ActionSheetCallback
    ) : super(activity) {
        val view = View.inflate(context, R.layout.dialog_action_sheet, null)
        setContentView(view)

        BottomSheetBehavior.from(view.parent as View).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        toolbar = findViewById(R.id.bottom_sheet_toolbar)
        gasWidget = findViewById(R.id.gas_widgetx)
        gasWidgetLegacy = findViewById(R.id.gas_widget_legacy)
        balanceDisplay = findViewById(R.id.balance)
        networkDisplay = findViewById(R.id.network_display_widget)
        confirmationWidget = findViewById(R.id.confirmation_view)
        detailWidget = findViewById(R.id.detail_widget)
        senderAddressDetail = findViewById(R.id.sender)
        receiptAddressDetail = findViewById(R.id.recipient)
        amountDisplay = findViewById(R.id.amount_display)
        assetDetailView = findViewById(R.id.asset_detail)
        functionBar = findViewById(R.id.layoutButtons)
        walletConnectRequestWidget = null // Explicitly null for this path

        mode = if (context is HomeActivity) {
            ActionSheetMode.SEND_TRANSACTION_DAPP
        } else {
            ActionSheetMode.SEND_TRANSACTION
        }

        signCallback = null
        actionSheetCallback = aCallBack
        actionCompleted = false

        token = t
        tokensService = ts
        candidateTransaction = tx
        callbackId = tx.leafPosition

        transactionDetail = ts.getCurrentAddress()?.let {
            Transaction(tx, token.tokenInfo.chainId, it).apply {
                transactionInput = Transaction.decoder.decodeInput(candidateTransaction, token.tokenInfo.chainId, token.getWallet())
            }
        }

        balanceDisplay?.setupBalance(token, transactionDetail)
        networkDisplay?.setNetwork(token.tokenInfo.chainId)

        gasWidgetInterface = setupGasWidget()

        actionSheetCallback.gasService.startGasPriceCycle(token.tokenInfo.chainId)

        if (tx.gasLimit != BigInteger.ZERO) {
            gasWidgetInterface?.setGasEstimateExact(tx.gasLimit)
            functionBar?.setPrimaryButtonEnabled(true)
        }

        updateAmount()

        senderAddressDetail?.setupAddress(token.getWallet(), "", null)
        receiptAddressDetail?.setupAddress(destAddress, destName, tokensService.getToken(token.tokenInfo.chainId, destAddress))

        // Determine ERC721 token ID early
        val erc721TokenId = if (token.isERC721()) {
            if (actionSheetCallback.getTokenId().compareTo(BigInteger.ZERO) > 0) {
                actionSheetCallback.getTokenId().toString()
            } else {
                transactionDetail.let { it?.transactionInput?.let { it1 -> token.getTransferValueRaw(it1).toString() } } ?: ""
            }
        } else {
            ""
        }

        if (token.isNonFungible()) {
            balanceDisplay?.visibility = View.GONE

            if (token.getInterfaceSpec() == ContractType.ERC1155) {
                val assetList = transactionDetail?.let {
                    token.getAssetListFromTransaction(it)
                }?: emptyList()

                amountDisplay?.visibility = View.VISIBLE
                amountDisplay?.setAmountFromAssetList(assetList.toMutableList())

            } else {
                amountDisplay?.visibility = View.GONE
                assetDetailView?.setupAssetDetail(token, erc721TokenId, this)
                assetDetailView?.visibility = View.VISIBLE
            }
        }

        setupTransactionDetails()
        setupCancelListeners()

        walletType = actionSheetCallback.walletType

        if (walletType == WalletType.HARDWARE) {
            functionBar?.setupFunctions(this, arrayListOf(R.string.use_hardware_card))
            functionBar?.isClickable = false
            handleTransactionOperation()
        } else {
            functionBar?.setupFunctions(this, arrayListOf(R.string.action_confirm))
        }

        functionBar?.revealButtons()
    }

    /**
     * Constructor 2: Wallet Connect Request
     */
    constructor(
        activity: Activity, wcPeerMeta: WCPeerMeta, chainIdOverride: Long, iconUrl: String,
        aCallBack: ActionSheetCallback
    ) : super(activity) {
        setContentView(R.layout.dialog_wallet_connect_sheet)
        mode = ActionSheetMode.WALLET_CONNECT_REQUEST

        functionBar = findViewById(R.id.layoutButtons)
        toolbar = findViewById(R.id.bottom_sheet_toolbar)
        actionSheetCallback = aCallBack
        walletConnectRequestWidget = findViewById(R.id.wallet_connect_widget)

        // --- Nullify properties not used in this path ---
        gasWidget = null
        gasWidgetLegacy = null
        balanceDisplay = null
        networkDisplay = null
        confirmationWidget = null
        senderAddressDetail = null
        receiptAddressDetail = null
        amountDisplay = null
        assetDetailView = null
        detailWidget = null
        token = null
        tokensService = null
        candidateTransaction = null
        callbackId = 0
        gasWidgetInterface = null
        transactionDetail = null
        walletType = actionSheetCallback.walletType
        // --- End Nullify ---

        toolbar?.setLogo(activity, iconUrl)
        toolbar?.setTitle(wcPeerMeta.name)
        toolbar?.setCloseListener { actionSheetCallback.denyWalletConnect() }

        walletConnectRequestWidget?.setupWidget(wcPeerMeta, chainIdOverride, actionSheetCallback::openChainSelection)

        val functionList = arrayListOf(R.string.approve, R.string.dialog_reject)
        functionBar?.setupFunctions(this, functionList)
        functionBar?.revealButtons()

        actionSheetStatus = if (EthereumNetworkBase.isChainSupported(chainIdOverride)) {
            ActionSheetStatus.OK
        } else {
            ActionSheetStatus.ERROR_INVALID_CHAIN
        }
    }

    /**
     * Constructor 3: Switch Chain
     */
    constructor(
        activity: Activity, aCallback: ActionSheetCallback, titleId: Int, buttonTextId: Int,
        cId: Long, baseToken: Token?, oldNetwork: NetworkInfo, newNetwork: NetworkInfo
    ) : super(activity) {
        setContentView(R.layout.dialog_action_sheet_switch_chain)

        toolbar = findViewById(R.id.bottom_sheet_toolbar)
        findViewById<SwitchChainWidget>(R.id.switch_chain_widget)?.setupSwitchChainData(oldNetwork, newNetwork)

        functionBar = findViewById(R.id.layoutButtons)
        actionSheetCallback = aCallback
        mode = ActionSheetMode.MESSAGE
        toolbar?.setTitle(titleId)

        // --- Nullify properties not used in this path ---
        gasWidget = null
        gasWidgetLegacy = null
        balanceDisplay = null
        networkDisplay = null
        confirmationWidget = null
        senderAddressDetail = null
        receiptAddressDetail = null
        amountDisplay = null
        assetDetailView = null
        detailWidget = null
        callbackId = cId
        token = baseToken
        tokensService = null
        candidateTransaction = null
        walletConnectRequestWidget = null
        gasWidgetInterface = null
        transactionDetail = null
        walletType = actionSheetCallback.walletType
        // --- End Nullify ---

        functionBar?.setupFunctions(this, arrayListOf(buttonTextId))
        functionBar?.revealButtons()
        setupCancelListeners()
    }

    /**
     * Constructor 4: Node Status Info
     */
    constructor(activity: Activity, mode: ActionSheetMode) : super(activity) {
        this.mode = mode
        if (mode == ActionSheetMode.NODE_STATUS_INFO) {
            setContentView(R.layout.dialog_action_sheet_node_status)
        }

        // --- Nullify all properties ---
        toolbar = null
        gasWidget = null
        gasWidgetLegacy = null
        balanceDisplay = null
        networkDisplay = null
        confirmationWidget = null
        senderAddressDetail = null
        receiptAddressDetail = null
        amountDisplay = null
        assetDetailView = null
        functionBar = null
        detailWidget = null
        walletConnectRequestWidget = null
        gasWidgetInterface = null
        token = null
        tokensService = null
        candidateTransaction = null
        actionSheetCallback = null
        walletType = WalletType.NOT_DEFINED
        transactionDetail = null
        callbackId = 0
        // --- End Nullify ---
    }

    private fun setupGasWidget(): GasWidgetInterface? {
        // Ensure properties are available for this path
        if (token == null || tokensService == null || candidateTransaction == null) return null

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val canUse1559Transactions = prefs.getBoolean(SharedPreferenceRepository.EXPERIMENTAL_1559_TX, true)

        use1559Transactions = canUse1559Transactions && has1559Gas() // 1559 Transactions toggled on and chain supports it
                && !(token.isEthereum() && candidateTransaction.leafPosition == -2L) // User not sweeping wallet
                && !tokensService.hasLockedGas(token.tokenInfo.chainId) // Service has not locked gas (eg Optimism)

        return if (use1559Transactions) {
            gasWidget?.setupWidget(tokensService, token, candidateTransaction, this)
            gasWidget
        } else {
            gasWidget?.visibility = View.GONE
            gasWidgetLegacy?.visibility = View.VISIBLE
            gasWidgetLegacy?.setupWidget(tokensService, token, candidateTransaction, this, this)
            gasWidgetLegacy
        }
    }

    override fun setSignOnly() {
        //sign only, and return signature to process
        mode = ActionSheetMode.SIGN_TRANSACTION
        toolbar?.setTitle(R.string.dialog_title_sign_transaction)
        use1559Transactions = candidateTransaction?.isLegacyTransaction == false // Respect ERC-1559 or legacy
    }

    fun setDappSigningMode() {
        this.mode = ActionSheetMode.SEND_TRANSACTION_DAPP
    }

    fun onDestroy() {
        gasWidgetInterface?.onDestroy()
        assetDetailView?.onDestroy()
        detailWidget?.onDestroy()
        backupGasFetchJob?.cancel()
        dialogScope.cancel()
    }

    fun setURL(url: String) {
        findViewById<AddressDetailView>(R.id.requester)?.setupRequester(url)
        setupTransactionDetails()

        if (candidateTransaction?.isConstructor == true) {
            receiptAddressDetail?.visibility = View.GONE
        }

        if (candidateTransaction != null && (candidateTransaction.value?.let { it > BigInteger.ZERO  } == true || candidateTransaction.isBaseTransfer)) {
            amountDisplay?.visibility = View.VISIBLE
            amountDisplay?.setAmountUsingToken(candidateTransaction.value, tokensService?.getServiceToken(token!!.tokenInfo.chainId), tokensService)
        } else {
            amountDisplay?.visibility = View.GONE
        }
    }

    private fun setupTransactionDetails() {
        if (token == null || candidateTransaction == null || tokensService == null) return

        if (candidateTransaction.isBaseTransfer) {
            detailWidget?.visibility = View.GONE
        } else {
            detailWidget?.visibility = View.VISIBLE
            detailWidget?.setupTransaction(
                candidateTransaction, token.tokenInfo.chainId,
                tokensService.getNetworkSymbol(token.tokenInfo.chainId), this
            )
        }
    }

    override fun setCurrentGasIndex(result: ActivityResult?) {
        val data = result?.data ?: return
        data.run {
            val gasSelectionIndex = getIntExtra(C.EXTRA_SINGLE_ITEM, TXSpeed.STANDARD.ordinal)
            val customNonce = getLongExtra(C.EXTRA_NONCE, -1)
            val maxFeePerGas = if (hasExtra(C.EXTRA_GAS_PRICE)) {
                BigInteger(getStringExtra(C.EXTRA_GAS_PRICE))
            } else BigInteger.ZERO
            val maxPriorityFee = if (hasExtra(C.EXTRA_MIN_GAS_PRICE)) {
                BigInteger(getStringExtra(C.EXTRA_MIN_GAS_PRICE))
            } else BigInteger.ZERO
            val customGasLimit = BigDecimal(getStringExtra(C.EXTRA_GAS_LIMIT))
            val expectedTxTime = getLongExtra(C.EXTRA_AMOUNT, 0)
            gasWidgetInterface?.setCurrentGasIndex(gasSelectionIndex, maxFeePerGas, maxPriorityFee, customGasLimit, expectedTxTime, customNonce)
        }
    }

    private fun isSendingTransaction(): Boolean {
        return mode != ActionSheetMode.SIGN_MESSAGE && mode != ActionSheetMode.SIGN_TRANSACTION
    }

    fun setupResendTransaction(callingMode: ActionSheetMode) {
        mode = callingMode
        gasWidgetInterface?.setupResendSettings(mode, candidateTransaction?.gasPrice)
        balanceDisplay?.visibility = View.GONE
        networkDisplay?.visibility = View.GONE
        receiptAddressDetail?.visibility = View.GONE
        detailWidget?.visibility = View.GONE
        amountDisplay?.visibility = View.GONE
    }

    override fun updateAmount() {
        showAmount(transactionAmount.toBigInteger())
    }

    override fun handleClick(action: String?, actionId: Int) {
        if (walletType == WalletType.HARDWARE) {
            //TODO: Hardware - Maybe flick a toast to tell user to apply card
            return
        }

        when (mode) {
            ActionSheetMode.SEND_TRANSACTION_WC,
            ActionSheetMode.SEND_TRANSACTION,
            ActionSheetMode.SEND_TRANSACTION_DAPP,
            ActionSheetMode.SPEEDUP_TRANSACTION,
            ActionSheetMode.CANCEL_TRANSACTION -> {
                gasToUse = getGasSettingsToUse()
                if (gasToUse == null) {
                    functionBar?.setPrimaryButtonWaiting()
                    callGasUpdateAndRePushTx()
                } else {
                    checkGasAndSend()
                }
            }
            ActionSheetMode.SIGN_TRANSACTION -> handleTransactionOperation()
            ActionSheetMode.MESSAGE -> actionSheetCallback?.buttonClick(callbackId, token)
            ActionSheetMode.WALLET_CONNECT_REQUEST -> {
                if (actionId == R.string.approve) {
                    actionSheetCallback?.notifyWalletConnectApproval(walletConnectRequestWidget!!.getChainIdOverride())
                    tryDismiss()
                } else {
                    actionSheetCallback?.denyWalletConnect()
                }
            }
            else -> { /* Do nothing */ }
        }
    }

    private fun checkGasAndSend() {
        functionBar?.setPrimaryButtonEnabled(true)
        if (gasWidgetInterface?.checkSufficientGas() == false) {
            askUserForInsufficientGasConfirm()
        } else {
            handleTransactionOperation()
        }
    }

    private fun callGasUpdateAndRePushTx() {
        val currentToken = token ?: return
        val gasService = actionSheetCallback?.gasService ?: return

        backupGasFetchJob?.cancel()
        backupGasFetchJob = dialogScope.launch {
            try {
                val gasPriceStandard = gasService.fetchGasPriceSuspend(currentToken.tokenInfo.chainId, use1559Transactions)
                gasToUse = gasPriceStandard
                if (use1559Transactions && gasToUse?.maxFeePerGas == BigInteger.ZERO && gasToUse?.priorityFee == BigInteger.ZERO) {
                    use1559Transactions = false
                }
                checkGasAndSend()
            } catch (_: CancellationException) {
                // 协程取消时无需处理
            } catch (_: Throwable) {
                // 保持与旧实现一致，不在此处理异常
            }
        }
    }

    override fun gasEstimateReady() {
        functionBar?.setPrimaryButtonEnabled(true)
    }

    override fun gasSelectLauncher(): ActivityResultLauncher<Intent>? {
        return actionSheetCallback?.gasSelectLauncher()
    }

    private fun getGasSettingsToUse(): EIP1559FeeOracleResult? {
        if (gasWidgetInterface?.gasPriceReady() == true) {
            return if (use1559Transactions) {
                gasWidgetInterface.gasPrice?.let {
                    EIP1559FeeOracleResult(
                        gasWidgetInterface.gasMax,
                        gasWidgetInterface.priorityFee,
                        it
                    )
                }
            } else {
                gasWidgetInterface.getGasPrice(candidateTransaction?.gasPrice)?.let {
                    EIP1559FeeOracleResult(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        it
                    )
                }
            }
        }
        return null
    }

    private val transactionAmount: BigDecimal
        get() {
            return when {
                token?.isEthereum() == true -> gasWidgetInterface?.value?.let { BigDecimal(it) } ?: BigDecimal.ZERO
                isSendingTransaction() && token != null && transactionDetail != null ->
                    transactionDetail.let { BigDecimal(it.transactionInput?.let { it1 -> token.getTransferValueRaw(it1) }) } ?: BigDecimal.ZERO
                else -> BigDecimal.ZERO
            }
        }

    /**
     * Popup a dialogbox to ask user if they really want to try to send this transaction,
     * as we calculate it will fail due to insufficient gas. User knows best though.
     */
    private fun askUserForInsufficientGasConfirm() {
        AWalletAlertDialog(context).apply {
            setIcon(AWalletAlertDialog.WARNING)
            setTitle(R.string.insufficient_gas)
            setMessage(context.getString(R.string.not_enough_gas_message))
            setButtonText(R.string.action_send)
            setSecondaryButtonText(R.string.cancel_transaction)
            setButtonListener {
                dismiss()
                handleTransactionOperation()
            }
            setSecondaryButtonListener {
                dismiss()
            }
        }.show()
    }

    fun transactionWritten(tx: String) {
        txHash = tx
        confirmationWidget?.completeProgressMessage(txHash, this::showTransactionSuccess)
        if (!tx.isNullOrEmpty() && tx.startsWith("0x")) {
            updateRealmTransactionFinishEstimate(tx)
        }
    }

    private fun showTransactionSuccess() {
        when (mode) {
            ActionSheetMode.SEND_TRANSACTION -> {
                actionSheetCallback?.dismissed(txHash, callbackId, true)
                tryDismiss()
            }
            ActionSheetMode.SEND_TRANSACTION_WC,
            ActionSheetMode.SEND_TRANSACTION_DAPP,
            ActionSheetMode.SPEEDUP_TRANSACTION,
            ActionSheetMode.CANCEL_TRANSACTION,
            ActionSheetMode.SIGN_TRANSACTION -> {
                tryDismiss()
            }
            else -> { /* Do nothing */ }
        }
    }

    private fun tryDismiss() {
        if (context.stillAvailable() && isShowing) dismiss()
    }

    private fun updateRealmTransactionFinishEstimate(txHash: String) {
        if (tokensService == null || gasWidgetInterface == null) return
        val expectedTime = System.currentTimeMillis() + gasWidgetInterface.expectedTransactionTime * 1000
        try {
            tokensService.getWalletRealmInstance().use { realm ->
                realm?.executeTransactionAsync { r ->
                    r.where(RealmTransaction::class.java)
                        .equalTo("hash", txHash)
                        .findFirst()
                        ?.let {
                            it.expectedCompletion = expectedTime
                            r.insertOrUpdate(it)
                        }
                }
            }
        } catch (e: Exception) {
            //
        }
    }

    private fun setupCancelListeners() {
        toolbar?.setCloseListener { dismiss() }

        setOnDismissListener {
            actionSheetCallback?.dismissed(txHash, callbackId, actionCompleted)
            gasWidgetInterface?.onDestroy()
            backupGasFetchJob?.cancel()
            dialogScope.cancel()
        }
    }

    fun completeSignRequest(gotAuth: Boolean) {
        if (signCallback == null) return
        actionCompleted = true

        when (mode) {
            ActionSheetMode.SEND_TRANSACTION_WC,
            ActionSheetMode.SEND_TRANSACTION,
            ActionSheetMode.SEND_TRANSACTION_DAPP,
            ActionSheetMode.SPEEDUP_TRANSACTION,
            ActionSheetMode.CANCEL_TRANSACTION,
            ActionSheetMode.SIGN_TRANSACTION -> signCallback?.gotAuthorisation(gotAuth)
            ActionSheetMode.SIGN_MESSAGE -> {
                confirmationWidget?.startProgressCycle(1)
                signCallback?.gotAuthorisation(gotAuth)
            }
            else -> { /* Do nothing */ }
        }
    }

    private fun formTransaction(): Web3Transaction {
        // Ensure properties are non-null for this path
        requireNotNull(candidateTransaction)
        requireNotNull(gasWidgetInterface)
        requireNotNull(gasToUse)

        establishedTransaction = if (!use1559Transactions) {
            Web3Transaction(
                candidateTransaction.recipient,
                candidateTransaction.contract,
                candidateTransaction.sender,
                gasWidgetInterface.value,
                gasToUse!!.baseFee,
                gasWidgetInterface.gasLimit,
                gasWidgetInterface.nonce,
                candidateTransaction.payload,
                candidateTransaction.leafPosition
            )
        } else {
            Web3Transaction(
                candidateTransaction.recipient,
                candidateTransaction.contract,
                candidateTransaction.sender,
                gasWidgetInterface.value,
                gasToUse!!.maxFeePerGas,
                gasToUse!!.priorityFee,
                gasWidgetInterface.gasLimit,
                gasWidgetInterface.nonce,
                candidateTransaction.payload,
                candidateTransaction.leafPosition
            )
        }
        return establishedTransaction!!
    }

    /**
     * Either Send or Sign (WalletConnect only) the transaction
     */
    private fun handleTransactionOperation() {
        if (walletType != WalletType.HARDWARE) {
            functionBar?.visibility = View.GONE
        }

        signCallback = object : SignAuthenticationCallback {
            override fun gotAuthorisation(gotAuth: Boolean) {
                actionCompleted = true
                if (!gotAuth) {
                    cancelAuthentication()
                    return
                }
                if (walletType != WalletType.HARDWARE) {
                    confirmationWidget?.startProgressCycle(4)
                }

                when (mode) {
                    ActionSheetMode.SEND_TRANSACTION,
                    ActionSheetMode.SEND_TRANSACTION_DAPP,
                    ActionSheetMode.SEND_TRANSACTION_WC,
                    ActionSheetMode.SPEEDUP_TRANSACTION,
                    ActionSheetMode.CANCEL_TRANSACTION -> actionSheetCallback?.sendTransaction(formTransaction())
                    ActionSheetMode.SIGN_TRANSACTION -> actionSheetCallback?.signTransaction(formTransaction())
                    else -> { /* Do nothing */ }
                }
                actionSheetCallback?.notifyConfirm(mode.value)
            }

            override fun cancelAuthentication() {
                confirmationWidget?.hide()
                functionBar?.visibility = View.VISIBLE
            }

            override fun gotSignature(signature: SignatureFromKey?) {
                if (signature?.sigType == SignatureReturnType.SIGNATURE_GENERATED) {
                    functionBar?.visibility = View.GONE
                    confirmationWidget?.startProgressCycle(4)
                    if (mode == ActionSheetMode.SIGN_TRANSACTION) {
                        actionSheetCallback?.completeSignTransaction(establishedTransaction!!, signature)
                    } else {
                        actionSheetCallback?.completeSendTransaction(establishedTransaction!!, signature)
                    }
                } else {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "ERROR: ${signature?.failMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        actionSheetCallback?.getAuthorisation(signCallback!!)
    }

    override fun setGasEstimate(estimate: GasEstimate?) {
        if (!estimate?.error.isNullOrEmpty()) {
            // Display error
        } else {
            gasWidgetInterface?.setGasEstimate(estimate?.value)
            functionBar?.setPrimaryButtonEnabled(true)
        }
    }

    private fun showAmount(amountVal: BigInteger) {
        if (token == null || tokensService == null || gasWidgetInterface == null || candidateTransaction == null) return

        amountDisplay?.setAmountUsingToken(amountVal, token, tokensService)

        val networkFee = gasWidgetInterface.gasLimit?.let { gasWidgetInterface.getGasPrice(candidateTransaction.gasPrice)?.multiply(it) }
        val balanceAfterTransaction = gasWidgetInterface.value?.let { token.balance.toBigInteger().subtract(it) }
        balanceDisplay?.setNewBalanceText(token, transactionAmount, networkFee, balanceAfterTransaction)
    }

    override fun success() {
        if (context.stillAvailable() && isShowing) {
            confirmationWidget?.completeProgressMessage(".", this::dismiss)
        }
    }

    fun waitForEstimate() {
        functionBar?.setPrimaryButtonWaiting()
    }

    override fun updateChain(chainId: Long) {
        walletConnectRequestWidget?.updateChain(chainId)
    }

    private fun has1559Gas(): Boolean {
        if (tokensService == null || token == null) return false
        try {
            tokensService.getTickerRealmInstance()?.use { realm ->
                val rgs = realm.where(Realm1559Gas::class.java)
                    .equalTo("chainId", token.tokenInfo.chainId)
                    .findFirst()
                return rgs != null
            }
        } catch (e: Exception) {
            //
        }
        return false
    }

    fun setSigningWallet(address: String) {
        val prefs = SharedPreferenceRepository(context)
        if (!prefs.currentWalletAddress.equals(address, ignoreCase = true)) {
            receiptAddressDetail?.addMessage(context.getString(R.string.message_wc_wallet_different_from_active_wallet), R.drawable.ic_red_warning)
        }
    }
}
