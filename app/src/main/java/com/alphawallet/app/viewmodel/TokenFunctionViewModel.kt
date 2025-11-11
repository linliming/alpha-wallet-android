package com.alphawallet.app.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.DisplayState
import com.alphawallet.app.entity.GasEstimate
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.TSAttrCallback
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.UpdateType
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.opensea.OpenSeaAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.interact.CreateTransactionInteract
import com.alphawallet.app.interact.FetchTransactionsInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.service.OpenSeaService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.service.TransactionSendHandlerInterface
import com.alphawallet.app.ui.AssetDisplayActivity
import com.alphawallet.app.ui.Erc1155AssetSelectActivity
import com.alphawallet.app.ui.Erc20DetailActivity
import com.alphawallet.app.ui.FunctionActivity
import com.alphawallet.app.ui.MyAddressActivity
import com.alphawallet.app.ui.RedeemAssetSelectActivity
import com.alphawallet.app.ui.SellDetailActivity
import com.alphawallet.app.ui.TransferNFTActivity
import com.alphawallet.app.ui.TransferTicketDetailActivity
import com.alphawallet.app.ui.widget.entity.TicketRangeParcel
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.JsonUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3.entity.Address
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.FunctionDefinition
import com.alphawallet.token.entity.SigReturnType
import com.alphawallet.token.entity.TSAction
import com.alphawallet.token.entity.TicketRange
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.entity.XMLDsigDescriptor
import com.alphawallet.token.tools.TokenDefinition
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

/**
 * TokenFunctionViewModel - 代币功能视图模型类
 *
 * 这个类是AlphaWallet中处理代币功能操作的核心ViewModel，负责管理所有与代币功能相关的业务逻辑。
 * 主要功能包括：
 * 1. 代币脚本(TokenScript)的验证和管理
 * 2. 代币交易的创建和发送
 * 3. Gas费用估算
 * 4. NFT资产元数据获取
 * 5. OpenSea集成
 * 6. 钱包认证和签名
 * 7. 代币转账和销售
 * 8. 属性解析和更新
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@HiltViewModel
class TokenFunctionViewModel @Inject constructor(
    private val assetDefinitionService: AssetDefinitionService,
    private val createTransactionInteract: CreateTransactionInteract,
    private val gasService: GasService,
    private val tokensService: TokensService,
    private val keyService: KeyService,
    private val genericWalletInteract: GenericWalletInteract,
    private val openseaService: OpenSeaService,
    private val fetchTransactionsInteract: FetchTransactionsInteract,
    private val preferences: PreferenceRepositoryType
) : BaseViewModel(), TransactionSendHandlerInterface {

    companion object {
        private const val TAG = "TokenFunctionViewModel"
        
        // 常量定义
        private const val DEFAULT_GAS_LIMIT = 21000L
        private const val DEFAULT_GAS_PRICE = 0L
        private const val DEFAULT_NONCE = -1L
        private const val DEFAULT_VALUE = "0"
        private const val DEFAULT_PAYLOAD = "0x"
    }

    // 协程作业管理
    private val coroutineJobs = mutableListOf<Job>()

    // LiveData 状态管理
    private val _insufficientFunds = MutableLiveData<Token?>()
    private val _invalidAddress = MutableLiveData<String>()
    private val _sig = MutableLiveData<XMLDsigDescriptor>()
    private val _newScriptFound = MutableLiveData<TokenDefinition>()
    private val _attrFetchComplete = MutableLiveData<TokenDefinition>()
    private val _walletUpdate = MutableLiveData<Wallet>()
    private val _transactionFinalised = MutableLiveData<TransactionReturn>()
    private val _transactionError = MutableLiveData<TransactionReturn>()
    private val _gasEstimateComplete = MutableLiveData<Web3Transaction>()
    private val _gasEstimateError = MutableLiveData<Pair<GasEstimate, Web3Transaction>>()
    private val _nftAsset = MutableLiveData<NFTAsset?>()
    private val _scriptUpdateInProgress = MutableLiveData<Boolean>()

    // 状态Flow (Kotlin协程推荐)
    private val _uiState = MutableStateFlow<TokenFunctionUiState>(TokenFunctionUiState.Idle)
    val uiState: StateFlow<TokenFunctionUiState> = _uiState.asStateFlow()

    // 钱包状态
    private var wallet: Wallet? = null

    // 公开的LiveData接口
    val insufficientFunds: LiveData<Token?> = _insufficientFunds
    val invalidAddress: LiveData<String> = _invalidAddress
    val sig: LiveData<XMLDsigDescriptor> = _sig
    val walletUpdate: LiveData<Wallet> = _walletUpdate
    val newScriptFound: LiveData<TokenDefinition> = _newScriptFound
    val attrFetchComplete: LiveData<TokenDefinition> = _attrFetchComplete
    val scriptUpdateInProgress: LiveData<Boolean> = _scriptUpdateInProgress
    val transactionFinalised: MutableLiveData<TransactionReturn> = _transactionFinalised
    val transactionError: MutableLiveData<TransactionReturn> = _transactionError
    val gasEstimateComplete: MutableLiveData<Web3Transaction> = _gasEstimateComplete
    val gasEstimateError: MutableLiveData<Pair<GasEstimate, Web3Transaction>> = _gasEstimateError
    val nftAsset: MutableLiveData<NFTAsset?> = _nftAsset

    /**
     * 准备ViewModel，初始化钱包
     */
    fun prepare() {
        getCurrentWallet()
    }

    /**
     * 获取当前钱包
     */
    fun getCurrentWallet() {
        viewModelScope.launch {
            try {
                _uiState.value = TokenFunctionUiState.Loading
                val currentWallet = withContext(Dispatchers.IO) {
                    genericWalletInteract.find()
                }
                onDefaultWallet(currentWallet)
            } catch (e: Exception) {
                _uiState.value = TokenFunctionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 处理默认钱包更新
     */
    private fun onDefaultWallet(w: Wallet) {
        progress.postValue(false)
        wallet = w
        _walletUpdate.postValue(w)
        _uiState.value = TokenFunctionUiState.Success(w)
    }

    /**
     * 获取资产定义服务
     */
    fun getAssetDefinitionService(): AssetDefinitionService = assetDefinitionService
    fun getOpenseaService(): OpenSeaService = openseaService

    /**
     * 检查代币脚本有效性
     */
    fun checkTokenScriptValidity(token: Token) {
        viewModelScope.launch {
            try {
                val signatureData = withContext(Dispatchers.IO) {
                    assetDefinitionService.getSignatureData(token)
                }
                _sig.postValue(signatureData)
            } catch (e: Exception) {
                onSigCheckError(e)
            }
        }
    }

    /**
     * 处理签名检查错误
     */
    private fun onSigCheckError(throwable: Throwable) {
        val failSig = XMLDsigDescriptor().apply {
            result = "fail"
            type = SigReturnType.NO_TOKENSCRIPT
            subject = throwable.message
        }
        _sig.postValue(failSig)
    }

    /**
     * 获取交易字节数据
     */
    fun getTransactionBytes(token: Token, tokenId: BigInteger, def: FunctionDefinition?): String? {
        return def?.let { assetDefinitionService.generateTransactionPayload(token, tokenId, it) }
    }

    /**
     * 获取代币
     */
    fun getToken(chainId: Long, contractAddress: String): Token? {
        return tokensService.getToken(chainId, contractAddress)
    }

    /**
     * 获取货币代币
     */
    fun getCurrency(chainId: Long): Token? {
        return wallet?.let { tokensService.getToken(chainId, it.address) }
    }

    /**
     * 处理代币功能
     */
    fun handleFunction(action: TSAction, tokenId: BigInteger, token: Token, context: Context): Web3Transaction? {
        val functionEffect = action.function?.method
        
        return if (action.function?.tx != null &&
                   (action.function?.method.isNullOrEmpty()) &&
                   (action.function?.parameters.isNullOrEmpty())) {
            // 无参数，这是原生风格交易
            nativeSend(action, token, context)
        } else {
            // 构建函数调用交易
            buildFunctionCallTransaction(action, tokenId, token, functionEffect)
        }
    }

    /**
     * 构建函数调用交易
     */
    private fun buildFunctionCallTransaction(
        action: TSAction, 
        tokenId: BigInteger, 
        token: Token, 
        functionEffect: String?
    ): Web3Transaction? {
        val cAddr = ContractAddress(action.function)
        val functionData = getTransactionBytes(token, tokenId, action.function) ?: return null
        
        val value = if (action.function?.tx?.args?.containsKey("value") == true) {
            val arg = action.function?.tx?.args?.get("value")
            val resolvedValue = arg?.let { assetDefinitionService.resolveReference(token, action, it, tokenId) }
            val currency = getCurrency(token.tokenInfo.chainId)
            val scaledValue = BalanceUtils.getScaledValue(resolvedValue?:"", 18, Token.TOKEN_BALANCE_PRECISION)
            "$scaledValue ${currency?.getSymbol() ?: ""} to ${action.function?.method ?: ""}"
            resolvedValue
        } else {
            buildFunctionEffectString(action, functionEffect)
            DEFAULT_VALUE
        }

        // 清除缓存以刷新解析的值
        assetDefinitionService.clearCache()

        return buildWeb3Transaction(functionData, cAddr.address, functionEffect ?: "", value.toString())
    }

    /**
     * 构建函数效果字符串
     */
    private fun buildFunctionEffectString(action: TSAction, functionEffect: String?): String {
        var effect = functionEffect + "("
        var firstArg = true
        
        action.function?.parameters?.forEach { arg ->
            if (!firstArg) effect += ", "
            firstArg = false
            
            effect += when {
                arg.element.ref == "tokenId" -> "TokenId"
                else -> arg.element.value
            }
        }
        
        effect += ")"
        return effect
    }

    /**
     * 原生发送交易
     */
    private fun nativeSend(action: TSAction, token: Token, context: Context): Web3Transaction? {
        var isValid = true

        // 计算原生金额
        val value = BigDecimal(action.function?.tx?.args?.get("value")?.value ?: "0")
        val currency = getCurrency(token.tokenInfo.chainId)

        // 检查余额
        if (currency?.balance?.subtract(value)?.compareTo(BigDecimal.ZERO) == -1) {
            _insufficientFunds.postValue(currency)
            isValid = false
        }

        // 检查目标地址
        val to = action.function?.tx?.args?.get("to")?.value
            ?: action.function?.contract?.addresses?.get(token.tokenInfo.chainId)?.firstOrNull() ?: ""

        if (!Utils.isAddressValid(to)) {
            _invalidAddress.postValue(to)
            isValid = false
        }

        val valCorrected = BalanceUtils.getScaledValue(value, token.tokenInfo.decimals.toLong(), Token.TOKEN_BALANCE_PRECISION)
        val extraInfo = context.getString(
            R.string.tokenscript_send_native, 
            valCorrected, 
            token.getSymbol(),
            action.function?.method,
            to
        )

        // 清除缓存
        assetDefinitionService.clearCache()

        return if (isValid) {
            Web3Transaction(
                recipient = Address(to),
                contract = Address(token.getAddress()),
                value = value.toBigInteger(),
                gasPrice = BigInteger.ZERO,
                gasLimit = BigInteger.valueOf(DEFAULT_GAS_LIMIT),
                nonce = DEFAULT_NONCE,
                payload = DEFAULT_PAYLOAD,
                description = extraInfo
            )
        } else {
            null
        }
    }

    /**
     * 构建Web3交易
     */
    private fun buildWeb3Transaction(
        functionData: String,
        contractAddress: String, 
        additionalDetails: String, 
        value: String
    ): Web3Transaction {
        return Web3Transaction(
            recipient = Address(contractAddress),
            contract = Address(contractAddress),
            value = BigInteger(value),
            gasPrice = BigInteger.ZERO,
            gasLimit = BigInteger.ZERO, // 需要计算gas限制
            nonce = DEFAULT_NONCE,
            payload = functionData,
            description = additionalDetails
        )
    }

    /**
     * 估算Gas限制
     */
    fun estimateGasLimit(w3tx: Web3Transaction, chainId: Long) {
        viewModelScope.launch {
            try {
                val estimate = withContext(Dispatchers.IO) {
                    gasService.calculateGasEstimateSuspend(
                        Numeric.hexStringToByteArray(w3tx.payload),
                        chainId,
                        w3tx.contract.toString(),
                        w3tx.value,
                        wallet,
                        BigInteger.ZERO
                    )
                }
                buildNewConfirmation(estimate, w3tx)
            } catch (error: Exception) {
                // 节点不喜欢这个交易
                buildNewConfirmation(GasEstimate(BigInteger.ZERO), w3tx)
            }
        }
    }

    /**
     * 构建新的确认
     */
    private fun buildNewConfirmation(estimate: GasEstimate, w3tx: Web3Transaction) {
        if (estimate.hasError()) {
            _gasEstimateError.postValue(Pair(estimate, w3tx))
        } else {
            val newTransaction = Web3Transaction(
                recipient = w3tx.recipient,
                contract = w3tx.contract,
                value = w3tx.value,
                gasPrice = w3tx.gasPrice,
                gasLimit = estimate.value,
                nonce = w3tx.nonce,
                payload = w3tx.payload,
                description = w3tx.description
            )
            _gasEstimateComplete.postValue(newTransaction)
        }
    }

    /**
     * 获取认证
     */
    fun getAuthentication(activity: Activity, callback: SignAuthenticationCallback) {
        viewModelScope.launch {
            try {
                val currentWallet = withContext(Dispatchers.IO) {
                    genericWalletInteract.find()
                }
                keyService.getAuthenticationForSignature(currentWallet, activity, callback)
            } catch (e: Exception) {
//                onError(e)
            }
        }
    }

    /**
     * 获取认证（指定钱包）
     */
    fun getAuthentication(activity: Activity, wallet: Wallet, callback: SignAuthenticationCallback) {
        keyService.getAuthenticationForSignature(wallet, activity, callback)
    }

    /**
     * 请求签名
     */
    fun requestSignature(finalTx: Web3Transaction, wallet: Wallet, chainId: Long) {
        createTransactionInteract.requestSignature(finalTx, wallet, chainId, this)
    }

    /**
     * 发送交易
     */
    fun sendTransaction(wallet: Wallet, chainId: Long, tx: Web3Transaction, signatureFromKey: SignatureFromKey) {
        createTransactionInteract.sendTransaction(wallet, chainId, tx, signatureFromKey)
    }

    /**
     * 交易完成
     */
    override fun transactionFinalised(txData: TransactionReturn) {
        _transactionFinalised.postValue(txData)
    }

    /**
     * 交易错误
     */
    override fun transactionError(txError: TransactionReturn) {
        _transactionError.postValue(txError)
    }

    /**
     * 获取钱包
     */
    fun getWallet(): Wallet? = wallet

    /**
     * 获取Realm实例
     */
    fun getRealmInstance(w: Wallet): Realm? = tokensService.getRealmInstance(w)

    /**
     * 获取代币服务
     */
    fun getTokensService(): TokensService = tokensService

    /**
     * 获取交易交互器
     */
    fun getTransactionsInteract(): FetchTransactionsInteract = fetchTransactionsInteract

    /**
     * 获取交易
     */
    fun fetchTransaction(txHash: String): Transaction? {
        return wallet?.let { fetchTransactionsInteract.fetchCached(it.address.orEmpty(), txHash) }
    }

    /**
     * 检查是否有代币脚本
     */
    fun hasTokenScript(token: Token): Boolean {
        return token != null && assetDefinitionService.getAssetDefinition(token) != null
    }

    /**
     * 是否授权执行功能
     */
    fun isAuthorizeToFunction(): Boolean {
        return wallet?.type != WalletType.WATCH
    }

    /**
     * 获取Gas服务
     */
    fun getGasService(): GasService = gasService

    /**
     * 获取是否使用TS查看器
     */
    fun getUseTSViewer(): Boolean = preferences.useTSViewer

    /**
     * 开始Gas价格更新
     */
    fun startGasPriceUpdate(chainId: Long) {
        gasService.startGasPriceCycle(chainId)
    }

    /**
     * 停止Gas设置获取
     */
    fun stopGasSettingsFetch() {
        gasService.stopGasPriceCycle()
    }

    /**
     * 重置签名对话框
     */
    fun resetSignDialog() {
        keyService.resetSigningDialog()
    }

    /**
     * 完成认证
     */
    fun completeAuthentication(signData: Operation) {
        keyService.completeAuthentication(signData)
    }

    /**
     * 认证失败
     */
    fun failedAuthentication(signData: Operation) {
        keyService.failedAuthentication(signData)
    }

    /**
     * 重启服务
     */
    fun restartServices() {
        fetchTransactionsInteract.restartTransactionService()
    }

    /**
     * 更新代币检查
     */
    fun updateTokensCheck(token: Token) {
        tokensService.setFocusToken(token)
    }

    /**
     * 清除焦点代币
     */
    fun clearFocusToken() {
        tokensService.clearFocusToken()
    }

    /**
     * 获取代币服务
     */
    fun getTokenService(): TokensService = tokensService

    /**
     * 操作表确认
     */
    fun actionSheetConfirm(mode: String) {
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_ACTION_SHEET_MODE, mode)
        }
        track(Analytics.Action.ACTION_SHEET_COMPLETED, props)
    }

    /**
     * 更新代币脚本视图大小
     */
    fun updateTokenScriptViewSize(token: Token, itemViewHeight: Int) {
        assetDefinitionService.storeTokenViewHeight(token.tokenInfo.chainId, token.getAddress(), itemViewHeight)
    }

    /**
     * 检查新脚本
     */
    fun checkForNewScript(token: Token) {
        if (token == null) return
        
        viewModelScope.launch {
            try {
                _scriptUpdateInProgress.postValue(true)
                val td = withContext(Dispatchers.IO) {
                    assetDefinitionService.checkServerForScriptAsync(token, _scriptUpdateInProgress)
                }
                td?.let { handleDefinition(it) }
            } catch (e: Exception) {
                _scriptUpdateInProgress.postValue(false)
                handleError(e)
            }
        }
    }

    /**
     * 处理定义
     */
    private fun handleDefinition(td: TokenDefinition) {
        when (td.nameSpace) {
            TokenDefinition.UNCHANGED_SCRIPT -> {
                td.nameSpace = TokenDefinition.UNCHANGED_SCRIPT
                _newScriptFound.postValue(td)
            }
            TokenDefinition.NO_SCRIPT -> {
                _scriptUpdateInProgress.postValue(false)
            }
            else -> {
                _newScriptFound.postValue(td)
            }
        }
    }

    /**
     * 获取资产
     */
    fun getAsset(token: Token, tokenId: BigInteger) {
        loadExistingMetadata(token, tokenId)
        reloadMetadata(token, tokenId)
    }

    /**
     * 加载现有元数据
     */
    private fun loadExistingMetadata(token: Token, tokenId: BigInteger) {
        val asset = token.getAssetForToken(tokenId)
        if (asset != null && !asset.needsLoading()) {
            _nftAsset.postValue(asset)
        }
    }

    /**
     * 重新加载元数据
     */
    fun reloadMetadata(token: Token, tokenId: BigInteger) {
        launchSafely(onStart = {}, onComplete = {}, onError = { onAssetError(it) }) {
            val result = openseaService.getAsset(token, tokenId)
            onAsset(result, token, tokenId)
        }
    }

    /**
     * 处理资产错误
     */
    private fun onAssetError(throwable: Throwable) {
        Timber.d(throwable)
    }

    /**
     * 处理资产
     */
    private fun onAsset(result: String, token: Token, tokenId: BigInteger) {
        val oldAsset = token.getAssetForToken(tokenId)
        var loadedFromApi = false
        
        if (JsonUtils.isValidAsset(result)) {
            try {
                val assetJson = JSONObject(result)
                val osAsset = Gson().fromJson(assetJson.toString(), OpenSeaAsset::class.java)
                val asset = NFTAsset(result)
                
                if (!TextUtils.isEmpty(asset.getImage())) {
                    loadedFromApi = true

                    // 如果有slug可用，检查更多集合数据
                    if (osAsset.collection != null && !TextUtils.isEmpty(osAsset.collection?.slug.toString())) {
                        getCollection(token, tokenId, asset, osAsset)
                    } else {
                        storeAsset(token, tokenId, asset, oldAsset)
                        asset.attachOpenSeaAssetData(osAsset)
                        _nftAsset.postValue(asset)
                    }
                }
            } catch (e: JSONException) {
                Timber.w(e)
                Timber.d("Error fetching from OpenSea: %s", result)
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        if (!loadedFromApi) {
            getTokenMetadata(token, tokenId, oldAsset)
        }
    }

    /**
     * 获取代币元数据
     */
    fun getTokenMetadata(token: Token, tokenId: BigInteger, oldAsset: NFTAsset?) {
        viewModelScope.launch {
            try {
                val fetchedAsset = withContext(Dispatchers.IO) {
                    token.fetchTokenMetadata(tokenId)
                }
                val storedAsset = fetchedAsset?.let { storeAsset(token, tokenId, it, oldAsset) } ?: oldAsset
                storedAsset?.let { onAssetMetadata(it) }
            } catch (e: Exception) {
                onAssetMetadataError(e)
            }
        }
    }

    /**
     * 处理资产元数据错误
     */
    private fun onAssetMetadataError(t: Throwable) {
        Timber.w(t)
    }

    /**
     * 处理资产元数据
     */
    private fun onAssetMetadata(asset: NFTAsset) {
        _nftAsset.postValue(asset)
    }

    /**
     * 存储资产
     */
    private fun storeAsset(token: Token, tokenId: BigInteger, fetchedAsset: NFTAsset, oldAsset: NFTAsset?): NFTAsset {
        fetchedAsset.updateFromRaw(oldAsset)
        tokensService.storeAsset(token, tokenId, fetchedAsset)
        token.addAssetToTokenBalanceAssets(tokenId, fetchedAsset)
        return fetchedAsset
    }

    /**
     * 获取集合
     */
    fun getCollection(token: Token, tokenId: BigInteger, asset: NFTAsset, osAsset: OpenSeaAsset) {
        viewModelScope.launch {
            try {
                val result = openseaService.getCollection(token, osAsset.collection?.slug.toString())
                onCollection(token, tokenId, asset, osAsset, result)
            } catch (e: Exception) {
                onAssetError(e)
            }
        }
    }

    /**
     * 处理集合
     */
    private fun onCollection(token: Token, tokenId: BigInteger, asset: NFTAsset, osAsset: OpenSeaAsset, result: String) {
        val oldAsset = token.getAssetForToken(tokenId)
        
        if (JsonUtils.isValidCollection(result)) {
            try {
                val assetJson = JSONObject(result)
                if (assetJson.has("collection")) {
                    val collectionData = assetJson.get("collection").toString()
                    val data = Gson().fromJson(collectionData, OpenSeaAsset.Collection::class.java)
                    if (data != null) {
                        osAsset.collection = data
                    }
                }
            } catch (e: JSONException) {
                Timber.w(e)
                Timber.d("Error fetching from OpenSea: %s", result)
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        storeAsset(token, tokenId, asset, oldAsset)
        asset.attachOpenSeaAssetData(osAsset)
        _nftAsset.postValue(asset)
    }

    /**
     * 完成代币脚本设置
     */
    fun completeTokenScriptSetup(token: Token, tokenId: BigInteger, prevResult: String, tsCb: TSAttrCallback) {
        if (!hasTokenScript(token)) return

        launchSafely(onStart = {}, onComplete = {}, onError = { handleError(it) }) {
            val attrs = mutableListOf<TokenScriptResult.Attribute>()
            val attrsTxt = StringBuilder()

            withContext(Dispatchers.IO) {
                assetDefinitionService
                    .resolveAttrs(token, listOf(tokenId), null, UpdateType.USE_CACHE)
                    .filter { !it.userInput }
                    .collect { attr ->
                        attrs.add(attr)
                        attrsTxt.append(attr.text)
                    }
            }

            checkUpdatedAttrs(prevResult, attrsTxt.toString(), attrs, tsCb)
        }
    }

    /**
     * 检查更新的属性
     */
    private fun checkUpdatedAttrs(prevResult: String, attrsText: String, attrs: List<TokenScriptResult.Attribute>, tsCb: TSAttrCallback) {
        tsCb.showTSAttributes(attrs, prevResult != attrsText)
    }

    /**
     * 更新本地属性
     */
    fun updateLocalAttributes(token: Token, tokenId: BigInteger) {
        launchSafely(onStart = {}, onComplete = {}, onError = { handleError(it) }) {
            val availableActions = withContext(Dispatchers.IO) {
                assetDefinitionService.fetchFunctionMap(
                    token,
                    listOf(tokenId),
                    token.getInterfaceSpec(),
                    UpdateType.USE_CACHE,
                )
            }
            updateAllowedAttrs(token, availableActions)
        }
    }

    /**
     * 更新允许的属性
     */
    private fun updateAllowedAttrs(token: Token, availableActions: Map<BigInteger, List<String>>) {
        val firstKey = availableActions.keys.firstOrNull() ?: return
        val td = assetDefinitionService.getAssetDefinition(token) ?: return
        
        val localAttrList = assetDefinitionService.getLocalAttributes(td, availableActions)

        launchSafely(onStart = {}, onComplete = {}, onError = { handleError(it) }) {
            withContext(Dispatchers.IO) {
                assetDefinitionService.refreshAttributesAsync(token, td, firstKey, localAttrList)
            }
            _attrFetchComplete.postValue(td)
        }
    }

    /**
     * 添加认证属性
     */
    fun addAttestationAttrs(asset: NFTAsset?, token: Token, action: TSAction): String {
        val attrs = StringBuilder()
        
        if (asset?.isAttestation() == true) {
            val attestationAttrs = assetDefinitionService.getAttestationAttrs(token, action, asset.getAttestationID())
            attestationAttrs?.forEach { attr ->
                onAttr(attrs, attr)
            }
        }

        return attrs.toString()
    }

    /**
     * 处理属性
     */
    private fun onAttr(attrs: StringBuilder, attribute: TokenScriptResult.Attribute) {
        if (!TextUtils.isEmpty(attribute.id)) {
            Timber.d("ATTR/FA: ${attribute.id} (${attribute.name}) : ${attribute.text}")
            TokenScriptResult.addPair(attrs, attribute)
        }
    }

    // ==================== UI 导航方法 ====================

    /**
     * 打开通用链接
     */
    fun openUniversalLink(context: Context, token: Token, selection: List<BigInteger>) {
        val intent = Intent(context, SellDetailActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(selection, false))
            putExtra(C.EXTRA_STATE, SellDetailActivity.SET_A_PRICE)
            putExtra(C.EXTRA_PRICE, 0)
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 显示转账代币
     */
    fun showTransferToken(ctx: Context, token: Token, selection: List<BigInteger>) {
        val intent = Intent(ctx, TransferTicketDetailActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(selection, false))

            if (token.isERC721()) {
                // 跳过数值选择 - ERC721没有多重代币转账
                putExtra(C.EXTRA_STATE, DisplayState.TRANSFER_TO_ADDRESS.ordinal)
            }

            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        ctx.startActivity(intent)
    }

    /**
     * 显示功能
     */
    fun showFunction(ctx: Context, token: Token, method: String, tokenIds: List<BigInteger>?, asset: NFTAsset?) {
        val intent = Intent(ctx, FunctionActivity::class.java).apply {
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_STATE, method)
            
            asset?.let { putExtra(C.EXTRA_NFTASSET, it) }

            val finalTokenIds = tokenIds?.takeIf { it.isNotEmpty() } ?: listOf(BigInteger.ZERO)
            putExtra(C.EXTRA_TOKEN_ID, Utils.bigIntListToString(finalTokenIds, true))
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        ctx.startActivity(intent)
    }

    /**
     * 选择赎回代币
     */
    fun selectRedeemToken(ctx: Context, token: Token, idList: List<BigInteger>) {
        val parcel = TicketRangeParcel(TicketRange(idList, token.getAddress(), true))
        val intent = Intent(ctx, RedeemAssetSelectActivity::class.java).apply {
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.Key.TICKET_RANGE, parcel)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        ctx.startActivity(intent)
    }

    /**
     * 选择赎回代币（复数）
     */
    fun selectRedeemTokens(ctx: Context, token: Token, idList: List<BigInteger>) {
        val parcel = TicketRangeParcel(TicketRange(idList, token.getAddress(), true))
        val intent = Intent(ctx, RedeemAssetSelectActivity::class.java).apply {
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.Key.TICKET_RANGE, parcel)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        ctx.startActivity(intent)
    }

    /**
     * 销售票据路由器
     */
    fun sellTicketRouter(context: Context, token: Token, idList: List<BigInteger>) {
        val intent = Intent(context, SellDetailActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(idList, false))
            putExtra(C.EXTRA_STATE, SellDetailActivity.SET_A_PRICE)
            putExtra(C.EXTRA_PRICE, 0)
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 显示合约信息
     */
    fun showContractInfo(ctx: Context, token: Token) {
        val intent = Intent(ctx, MyAddressActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        ctx.startActivity(intent)
    }

    /**
     * 显示ERC20代币详情
     */
    override fun showErc20TokenDetail(context: Activity, address: String, symbol: String, decimals: Int, token: Token) {
        val hasDefinition = assetDefinitionService.hasDefinition(token)
        val intent = Intent(context, Erc20DetailActivity::class.java).apply {
            putExtra(C.EXTRA_SENDING_TOKENS, !token.isEthereum())
            putExtra(C.EXTRA_CONTRACT_ADDRESS, address)
            putExtra(C.EXTRA_SYMBOL, symbol)
            putExtra(C.EXTRA_DECIMALS, decimals)
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_ADDRESS, address)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition)
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 显示代币列表
     */
    override fun showTokenList(activity: Activity, token: Token) {
        val intent = Intent(activity, AssetDisplayActivity::class.java).apply {
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.Key.WALLET, wallet)
            flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        activity.startActivityForResult(intent, C.TERMINATE_ACTIVITY)
    }

    /**
     * 显示转账选择计数
     */
    suspend fun showTransferSelectCount(ctx: Context, token: Token, tokenId: BigInteger): Intent {
        val wallet = withContext(Dispatchers.IO) {
            genericWalletInteract.find()
        }
        return completeTransferSelect(ctx, token, tokenId, wallet)
    }

    /**
     * 完成转账选择
     */
    private fun completeTransferSelect(ctx: Context, token: Token, tokenId: BigInteger, wallet: Wallet): Intent {
        return Intent(ctx, Erc1155AssetSelectActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.EXTRA_TOKEN_ID, tokenId.toString(16))
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    /**
     * 获取转账意图
     */
    fun getTransferIntent(ctx: Context, token: Token, tokenIds: List<BigInteger>, selection: ArrayList<NFTAsset>): Intent {
        return completeTransferIntent(ctx, token, tokenIds, selection, getWallet())
    }

    /**
     * 完成转账意图
     */
    private fun completeTransferIntent(ctx: Context, token: Token, tokenIds: List<BigInteger>, selection: ArrayList<NFTAsset>, wallet: Wallet?): Intent {
        return Intent(ctx, TransferNFTActivity::class.java).apply {
            putExtra(C.Key.WALLET, wallet?.address)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(tokenIds, true))
            putParcelableArrayListExtra(C.EXTRA_NFTASSET_LIST, selection)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    /**
     * 加载钱包
     */
    fun loadWallet(address: String) {
        viewModelScope.launch {
            try {
                val wallet = withContext(Dispatchers.IO) {
                    genericWalletInteract.findWallet(address)
                }
                onDefaultWallet(wallet)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * 查找钱包
     */
    suspend fun findWallet(walletAddress: String): Wallet {
        return withContext(Dispatchers.IO) {
            genericWalletInteract.findWallet(walletAddress)
        }
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        coroutineJobs.forEach { it.cancel() }
        coroutineJobs.clear()
    }

    /**
     * UI状态密封类
     */
    sealed class TokenFunctionUiState {
        object Idle : TokenFunctionUiState()
        object Loading : TokenFunctionUiState()
        data class Success(val wallet: Wallet) : TokenFunctionUiState()
        data class Error(val message: String) : TokenFunctionUiState()
    }
}
