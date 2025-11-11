package com.alphawallet.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.FinishReceiver
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.GasService
import com.alphawallet.app.ui.widget.adapter.NonFungibleTokenAdapter
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.viewmodel.TokenFunctionViewModel
import com.alphawallet.app.web3.Web3TokenView
import com.alphawallet.app.web3.entity.PageReadyCallback
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.ActionSheetDialog
import com.alphawallet.app.widget.CertifiedToolbarView
import com.alphawallet.app.widget.FunctionButtonBar
import com.alphawallet.app.widget.SystemView
import com.alphawallet.ethereum.EthereumNetworkBase
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.TSAction
import com.alphawallet.token.entity.TicketRange
import com.alphawallet.token.entity.ViewType
import com.alphawallet.token.entity.XMLDsigDescriptor
import com.alphawallet.token.tools.TokenDefinition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigInteger

/**
 * 展示 NFT 资产列表的页面，同时负责 TokenScript 渲染与相关功能入口。
 */
@AndroidEntryPoint
class AssetDisplayActivity : BaseActivity(), StandardFunctionInterface, PageReadyCallback,
    Runnable, ActionSheetCallback {

    private lateinit var viewModel: TokenFunctionViewModel
    private lateinit var systemView: SystemView
    private lateinit var progressView: ProgressBar
    private lateinit var toolbarView: CertifiedToolbarView
    private lateinit var functionBar: FunctionButtonBar
    private lateinit var tokenView: RecyclerView

    private val handler = Handler(Looper.getMainLooper())
    private var finishReceiver: FinishReceiver? = null
    private lateinit var token: Token
    private lateinit var wallet: Wallet
    private var adapter: NonFungibleTokenAdapter? = null
    private var dialog: AWalletAlertDialog? = null
    private var testView: Web3TokenView? = null
    private var confirmationDialog: ActionSheetDialog? = null
    private var itemViewHeight: Int = 0
    private var checkVal: Int = 0
    private val assetDefinitionService get() = viewModel.getAssetDefinitionService()
    private val openseaService get() = viewModel.getOpenseaService()
    private val tokensService get() = viewModel.getTokenService()

    /**
     * 初始化界面与 ViewModel，以及解析传入的钱包和 Token 信息。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val parcelWallet = intent.getParcelableExtra<Wallet>(C.Key.WALLET) ?: run {
            finish()
            return
        }
        wallet = parcelWallet
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[TokenFunctionViewModel::class.java]
        val address = intent.getStringExtra(C.EXTRA_ADDRESS) ?: ""
        val chainId = intent.getLongExtra(C.EXTRA_CHAIN_ID, EthereumNetworkBase.MAINNET_ID)
        token = viewModel.getToken(chainId, address)
            ?: run {
                finish()
                return
            }
        setContentView(R.layout.activity_asset_display)
        toolbar()
        setupViews()
        subscribeViewModel()
        finishReceiver = FinishReceiver(this)
        viewModel.checkTokenScriptValidity(token)
        token.clearResultMap()
        if (token.getArrayBalance().isNotEmpty() && assetDefinitionService.hasDefinition(token)) {
            loadItemViewHeight()
        } else {
            displayTokens()
        }
        viewModel.checkForNewScript(token)
        viewModel.updateTokensCheck(token)
    }

    /**
     * 创建并订阅需要的 LiveData 以及设置标题等 UI。
     */
    private fun setupViews() {
        val currentToken = token
        setTitle(currentToken.getShortName())
        systemView = findViewById(R.id.system_view)
        progressView = findViewById(R.id.progress_view)
        testView = findViewById(R.id.test_web3)
        tokenView = findViewById(R.id.token_view)
        toolbarView = findViewById(R.id.certified_toolbar)
        functionBar = findViewById(R.id.layoutButtons)
        systemView.hide()
        progressView.visibility = View.VISIBLE
        tokenView.layoutManager = LinearLayoutManager(this)
        tokenView.setHapticFeedbackEnabled(true)
        findViewById<SwipeRefreshLayout>(R.id.refresh_layout).apply {
            systemView.attachSwipeRefreshLayout(this)
            setOnRefreshListener { refreshAssets() }
        }
    }

    /**
     * 注册所有需要的 LiveData 回调更新界面。
     */
    private fun subscribeViewModel() {
        viewModel.pushToast().observe(this, this::displayToast)
        viewModel.sig.observe(this, this::onSigData)
        viewModel.insufficientFunds.observe(this, this::errorInsufficientFunds)
        viewModel.invalidAddress.observe(this, this::errorInvalidAddress)
        viewModel.newScriptFound.observe(this, this::onNewScript)
        viewModel.gasEstimateComplete.observe(this, this::checkConfirm)
        viewModel.transactionFinalised.observe(this, this::txWritten)
    }

    /**
     * 加载 TokenScript 模板的行高，如果需要则先渲染隐藏的 WebView 获取高度。
     */
    private fun loadItemViewHeight() {
        lifecycleScope.launch {
            runCatching {
                assetDefinitionService.fetchViewHeightAsync(token.tokenInfo.chainId, token.getAddress())
            }.onSuccess { viewHeight(it) }
                .onFailure { viewHeight(0) }
        }
    }

    /**
     * 收到高度后决定是否需要 WebView 预渲染。
     */
    private fun viewHeight(fetchedViewHeight: Int) {
        if (fetchedViewHeight < 100) {
            initWebViewCheck(assetDefinitionService.getAssetDefinition(token))
            handler.postDelayed(this, TOKEN_SIZING_DELAY)
        } else {
            token.itemViewHeight = fetchedViewHeight
            displayTokens()
        }
    }

    /**
     * 当发现新的 TokenScript 时重新测量 WebView。
     */
    private fun onNewScript(td: TokenDefinition?) {
        if (td != null && td.isChanged) {
            initWebViewCheck(td)
            handler.postDelayed(this, TOKEN_SIZING_DELAY)
        }
    }

    /**
     * 初始化隐藏的 WebView 以获取行高。
     */
    private fun initWebViewCheck(td: TokenDefinition?) {
        checkVal = 0
        itemViewHeight = 0
        if (token.getArrayBalance().isNotEmpty()) {
            val tokenId = token.getArrayBalance()[0]
            val data = TicketRange(tokenId, token.getAddress())
            testView?.apply {
                setChainId(token.tokenInfo.chainId)
                renderTokenScriptInfoView(token, data, assetDefinitionService, ViewType.ITEM_VIEW, td)
                setOnReadyCallback(this@AssetDisplayActivity)
            }
        } else {
            displayTokens()
        }
    }

    /**
     * 获取签名信息后更新认证状态条。
     */
    private fun onSigData(sigData: XMLDsigDescriptor) {
        toolbarView.onSigData(sigData, this)
        adapter?.notifyItemChanged(0)
    }

    /**
     * 页面重新可见时刷新数据并确保功能栏初始化。
     */
    override fun onResume() {
        super.onResume()
        viewModel.prepare()
        if (!::functionBar.isInitialized) {
            functionBar = findViewById(R.id.layoutButtons)
        }
    }

    /**
     * 退出页面时清理监听器与资源。
     */
    override fun onDestroy() {
        super.onDestroy()
        finishReceiver?.unregister()
        viewModel.clearFocusToken()
        adapter?.onDestroy(tokenView)
    }

    /**
     * 刷新 NFT 内容，例如动态图或图片。
     */
    private fun refreshAssets() {
        adapter?.reloadAssets(this)
        systemView.hide()
    }

    /**
     * 构建右上角菜单，提供“查看合约”入口。
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_show_contract, menu)
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * 处理菜单点击事件。
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_show_contract) {
            viewModel.showContractInfo(this, token)
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 选择赎回的票券列表时，由 FunctionBar 调用。
     */
    override fun selectRedeemTokens(selection: List<BigInteger>) {
        viewModel.selectRedeemTokens(this, token, selection)
    }

    /**
     * 打开售卖票券的流程。
     */
    override fun sellTicketRouter(selection: List<BigInteger>) {
        viewModel.sellTicketRouter(this, token, selection)
    }

    /**
     * 打开转移票券的流程。
     */
    override fun showTransferToken(selection: List<BigInteger>) {
        viewModel.showTransferToken(this, token, selection)
    }

    /**
     * 当 TokenScript 函数对选择有要求时提示用户。
     */
    override fun displayTokenSelectionError(action: TSAction?) {
        val requirement = action?.function?.tokenRequirement ?: return
        if (dialog == null) dialog = AWalletAlertDialog(this)
        dialog?.apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(R.string.token_selection)
            setMessage(getString(R.string.token_requirement, requirement.toString()))
            setButtonText(R.string.dialog_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 控制等待对话框显示或隐藏。
     */
    override fun showWaitSpinner(show: Boolean) {
        if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
        if (!show) return
        dialog = AWalletAlertDialog(this).apply {
            setTitle(getString(R.string.check_function_availability))
            setIcon(AWalletAlertDialog.NONE)
            setProgressMode()
            setCancelable(false)
            show()
        }
    }

    /**
     * 当函数被拒绝执行时提示用户。
     */
    override fun handleFunctionDenied(denialMessage: String?) {
        if (dialog == null) dialog = AWalletAlertDialog(this)
        dialog?.apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(R.string.token_selection)
            setMessage(denialMessage ?: getString(R.string.token_selection))
            setButtonText(R.string.dialog_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 执行 TokenScript 定义的函数，支持无视图的交易型动作。
     */
    override fun handleTokenScriptFunction(function: String?, selection: List<BigInteger?>?) {
        val actionKey = function ?: return
        val cleanSelection = selection?.filterNotNull()
        if (cleanSelection.isNullOrEmpty()) return
        val functions = assetDefinitionService.getTokenFunctionMap(token)
        val action = functions?.get(actionKey)
        token.clearResultMap()
        if (action != null && action.view == null && action.function != null) {
            val web3Tx = viewModel.handleFunction(action, cleanSelection[0], token, this)
            if (web3Tx?.gasLimit == BigInteger.ZERO) {
                calculateEstimateDialog()
                viewModel.estimateGasLimit(web3Tx, token.tokenInfo.chainId)
            } else {
                web3Tx?.let { checkConfirm(it) }
            }
        } else {
            viewModel.showFunction(this, token, actionKey, cleanSelection, null)
        }
    }

    /**
     * 在获取 Gas 估算后弹出确认对话框。
     */
    private fun checkConfirm(w3tx: Web3Transaction) {
        if (w3tx.gasLimit == BigInteger.ZERO) {
            estimateError(w3tx)
        } else {
            dialog?.takeIf { it.isShowing }?.dismiss()
            confirmationDialog = ActionSheetDialog(
                this,
                w3tx,
                token,
                "",
                w3tx.recipient.toString(),
                viewModel.getTokenService(),
                this
            ).apply {
                setURL("TokenScript")
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }

    /**
     * 交易写入后更新 ActionSheet 状态。
     */
    private fun txWritten(txData: TransactionReturn) {
        confirmationDialog?.transactionWritten(txData.hash)
    }

    /**
     * 显示计算 Gas 限制的等待对话框。
     */
    private fun calculateEstimateDialog() {
        dialog?.takeIf { it.isShowing }?.dismiss()
        dialog = AWalletAlertDialog(this).apply {
            setTitle(getString(R.string.calc_gas_limit))
            setIcon(AWalletAlertDialog.NONE)
            setProgressMode()
            setCancelable(false)
            show()
        }
    }

    /**
     * WebView 页面开始加载完成时调用，触发前端刷新以准备高度测量。
     */
    override fun onPageLoaded(view: WebView?) {
        testView?.callToJS("refresh()")
    }

    /**
     * WebView 渲染完成后计算最终高度，并在多次回调后取最终值。
     */
    override fun onPageRendered(view: WebView?) {
        testView?.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            itemViewHeight = bottom - top
            checkVal++
            if (checkVal == 3) {
                addRunCall(0)
            } else {
                addRunCall(400)
            }
        }
    }

    /**
     * 调整 Handler 调度，延迟执行高度测量结果。
     */
    private fun addRunCall(delay: Int) {
        handler.removeCallbacks(this)
        handler.postDelayed(this, delay.toLong())
    }

    /**
     * Handler 触发后保存高度、刷新列表并清理测试用 WebView。
     */
    override fun run() {
        token.itemViewHeight = itemViewHeight
        viewModel.updateTokenScriptViewSize(token, itemViewHeight)
        displayTokens()
        testView?.destroyDrawingCache()
        testView?.removeAllViews()
        testView?.loadUrl("about:blank")
        testView?.visibility = View.GONE
    }

    /**
     * 将 NFT 数据绑定到列表适配器并初始化功能按钮。
     */
    private fun displayTokens() {
        handler.removeCallbacks(this)
        progressView.visibility = View.GONE
        val walletType = wallet.type
        adapter = NonFungibleTokenAdapter(
            functionBar,
            token,
            assetDefinitionService,
            openseaService
        )
        functionBar.setupFunctions(this, assetDefinitionService, token, adapter, token.getArrayBalance())
        functionBar.setWalletType(walletType)
        tokenView.adapter = adapter
    }

    /**
     * 将资产缓存到 TokenService，供后续快速读取。
     */
    fun storeAsset(tokenId: BigInteger, asset: NFTAsset) {
        tokensService.storeAsset(token, tokenId, asset)
    }

    /**
     * 余额不足时提示用户需要充值。
     */
    private fun errorInsufficientFunds(currency: Token?) {
        if (dialog?.isShowing == true) dialog?.dismiss()
        val message = currency?.let {
            getString(R.string.current_funds, it.getCorrectedBalance(it.tokenInfo.decimals), it.getSymbol())
        } ?: getString(R.string.error_insufficient_funds)
        dialog = AWalletAlertDialog(this).apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(R.string.error_insufficient_funds)
            setMessage(message)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 地址无效时弹窗提示。
     */
    private fun errorInvalidAddress(address: String) {
        if (dialog?.isShowing == true) dialog?.dismiss()
        dialog = AWalletAlertDialog(this).apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(R.string.error_invalid_address)
            setMessage(getString(R.string.invalid_address_explain, address))
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * Gas 估算失败时提示风险。
     */
    private fun estimateError(w3tx: Web3Transaction) {
        if (dialog?.isShowing == true) dialog?.dismiss()
        dialog = AWalletAlertDialog(this).apply {
            setIcon(AWalletAlertDialog.WARNING)
            setTitle(R.string.confirm_transaction)
            setMessage(R.string.error_transaction_may_fail)
            setButtonText(R.string.button_ok)
            setSecondaryButtonText(R.string.action_cancel)
            setButtonListener {
                val gasEstimate = GasService.getDefaultGasLimit(token, w3tx)
                val tx = Web3Transaction(
                    w3tx.recipient,
                    w3tx.contract,
                    w3tx.value,
                    w3tx.gasPrice,
                    gasEstimate,
                w3tx.nonce,
                    w3tx.payload,
                    w3tx.description
                )
                checkConfirm(tx)
            }
            setSecondaryButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 接收子页面回传，例如交易完成后的结果。
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == C.TERMINATE_ACTIVITY && resultCode == RESULT_OK && data != null) {
            Intent().apply {
                putExtra(C.EXTRA_TXHASH, data.getStringExtra(C.EXTRA_TXHASH))
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    /**
     * ActionSheet 调用钱包验证授权。
     */
    override fun getAuthorisation(callback: SignAuthenticationCallback?) {
        callback?.let { viewModel.getAuthentication(this, it) }
    }

    /**
     * 由 ActionSheet 发起交易请求。
     */
    override fun sendTransaction(tx: Web3Transaction?) {
        val web3Tx = tx ?: return
        viewModel.requestSignature(web3Tx, wallet, token.tokenInfo.chainId)
    }

    /**
     * 签名完成后发送交易。
     */
    override fun completeSendTransaction(tx: Web3Transaction?, signature: SignatureFromKey?) {
        if (tx == null || signature == null) return
        viewModel.sendTransaction(wallet, token.tokenInfo.chainId, tx, signature)
    }

    /**
     * ActionSheet 关闭后的回调，必要时返回交易哈希给父页面。
     */
    override fun dismissed(txHash: String?, callbackId: Long, actionCompleted: Boolean) {
        if (actionCompleted) {
            Intent().apply {
                putExtra(C.EXTRA_TXHASH, txHash)
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    /**
     * 用户确认 ActionSheet 动作时上报给 ViewModel。
     */
    override fun notifyConfirm(mode: String?) {
        mode?.let { viewModel.actionSheetConfirm(it) }
    }

    /**
     * Gas 设置页的 Activity Result Launcher。
     */
    private val gasSettingsLauncher: ActivityResultLauncher<Intent?> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            confirmationDialog?.setCurrentGasIndex(result)
        }

    /**
     * 提供给 ActionSheet 使用的 Gas 选择入口。
     */
    override fun gasSelectLauncher(): ActivityResultLauncher<Intent?>? = gasSettingsLauncher

    override val walletType: WalletType
        get() = wallet.type

    override val gasService: GasService
        get() = viewModel.getGasService()

    companion object {
        private const val TOKEN_SIZING_DELAY = 3000L
    }
}
