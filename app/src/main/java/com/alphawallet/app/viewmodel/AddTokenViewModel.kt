package com.alphawallet.app.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.alphawallet.app.C
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.interact.FetchTransactionsInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.ImportTokenActivity
import com.alphawallet.app.ui.SendActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 添加代币视图模型
 *
 * 负责管理添加代币的业务逻辑和数据状态，包括：
 * - 代币搜索和发现
 * - 多链网络扫描
 * - 代币类型识别
 * - 代币保存和启用
 *
 * 技术特点：
 * - 使用 Hilt 进行依赖注入
 * - 使用协程处理异步操作
 * - 继承自 BaseViewModel 提供基础功能
 */
@HiltViewModel
class AddTokenViewModel @Inject constructor(
    private val genericWalletInteract: GenericWalletInteract,
    private val ethereumNetworkRepository: EthereumNetworkRepositoryType,
    private val fetchTransactionsInteract: FetchTransactionsInteract,
    private val assetDefinitionService: AssetDefinitionService,
    private val tokensService: TokensService
) : BaseViewModel() {

    // LiveData 数据状态管理
    private val _wallet = MutableLiveData<Wallet>()
    private val _switchNetwork = MutableLiveData<Long>()
    private val _finalisedToken = MutableLiveData<Token>()
    private val _tokenType = MutableLiveData<Token>()
    private val _noContract = MutableLiveData<Boolean>()
    private val _scanCount = MutableLiveData<Int>()
    private val _onToken = MutableLiveData<Token>()
    private val _allTokens = MutableLiveData<Array<Token>>()

    // 临时状态变量
    private var foundNetwork = false
    private var networkCount = 0
    private var primaryChainId = 1L
    private val discoveredTokenList = ArrayList<Token>()
    private val handler = Handler(Looper.getMainLooper())

    // 使用 BaseViewModel 提供的协程作用域

    // 公共访问器方法
    fun wallet(): LiveData<Wallet> = _wallet
    fun tokenType(): LiveData<Token> = _tokenType
    fun switchNetwork(): LiveData<Long> = _switchNetwork
    fun chainScanCount(): LiveData<Int> = _scanCount
    fun onToken(): LiveData<Token> = _onToken
    fun allTokens(): LiveData<Array<Token>> = _allTokens

    /**
     * 保存代币列表
     * @param toSave 要保存的代币列表
     */
    fun saveTokens(toSave: List<Token>) {
        tokensService.addTokens(toSave)
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 设置主要链ID
     * @param chainId 链ID
     */
    fun setPrimaryChain(chainId: Long) {
        primaryChainId = chainId
    }

    /**
     * 获取当前选择的链ID
     * @return 当前选择的链ID
     */
    fun getSelectedChain(): Long {
        return primaryChainId
    }

    /**
     * 检查代币类型
     * 当代币类型检测失败时，尝试判断是否为ERC1155类型
     */
    private fun checkType(throwable: Throwable, chainId: Long, address: String, type: ContractType) {
        if (type == ContractType.ERC1155) {
            onTokensSetup(TokenInfo(address, "Holding Contract", "", 0, true, chainId))
        } else {
            handleError(throwable)
        }
    }

    /**
     * 获取指定链上的代币信息
     * @param chainId 链ID
     * @param addr 代币合约地址
     */
    fun fetchToken(chainId: Long, addr: String) {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val tokenInfo = withIO {
                tokensService.update(addr, chainId, ContractType.NOT_SET)
            }
            tokenInfo?.let {
                gotTokenUpdate(it)
            }

        }
    }

    /**
     * 处理代币更新信息
     * @param tokenInfo 代币信息
     */
    private fun gotTokenUpdate(tokenInfo: TokenInfo) {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val token = withIO {
                tokensService.addToken(tokenInfo, _wallet.value?.address ?: "")
            }
            resumeSend(token)
        }
    }

    /**
     * 完成代币发送准备
     * @param token 代币
     */
    private fun resumeSend(token: Token) {
        _finalisedToken.postValue(token)
    }

    /**
     * 获取网络信息
     * @param chainId 链ID
     * @return 网络信息
     */
    fun getNetworkInfo(chainId: Long): NetworkInfo {
        return ethereumNetworkRepository.getNetworkByChain(chainId)
    }

    /**
     * 查找钱包
     */
    private fun findWallet() {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val foundWallet = withIO {
                genericWalletInteract.find()
            }
            withMain {
                _wallet.value = foundWallet
            }
        }
    }

    /**
     * 设置代币信息
     * @param info 代币信息
     */
    private fun onTokensSetup(info: TokenInfo) {
        launchSafely(
            onError = { throwable -> tokenTypeError(throwable, info) }
        ) {
            val token = withIO {
                tokensService.addToken(info, _wallet.value?.address ?: "")
            }
            finaliseToken(token)
        }
    }

    /**
     * 完成代币设置
     * @param token 代币
     */
    private fun finaliseToken(token: Token) {
        checkNetworkCount()
        discoveredTokenList.add(token)
        _onToken.postValue(token)
    }

    /**
     * 处理代币类型错误
     * @param throwable 异常
     * @param data 代币信息
     */
    private fun tokenTypeError(throwable: Throwable, data: TokenInfo) {
        checkNetworkCount()
        val badToken = Token(data, BigDecimal.ZERO, 0, "", ContractType.NOT_SET)
        _tokenType.postValue(badToken)
    }

    /**
     * 准备视图模型
     * 初始化钱包信息
     */
    fun prepare() {
        findWallet()
    }

    /**
     * 显示发送界面
     * @param ctx 上下文
     * @param result QR扫描结果
     * @param token 代币
     */
    fun showSend(ctx: Context, result: QRResult, token: Token) {
        val intent = Intent(ctx, SendActivity::class.java)
        val sendingTokens = (result.function != null && result.function.isNotEmpty())
        var address = _wallet.value?.address ?: ""
        var decimals = 18

        if (sendingTokens) {
            address = result.address
            decimals = token.tokenInfo.decimals
        }

        intent.putExtra(C.EXTRA_SENDING_TOKENS, sendingTokens)
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address)
        intent.putExtra(C.EXTRA_NETWORKID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_SYMBOL, ethereumNetworkRepository.getNetworkByChain(result.chainId).symbol)
        intent.putExtra(C.EXTRA_DECIMALS, decimals)
        intent.putExtra(C.Key.WALLET, _wallet.value)
        intent.putExtra(C.EXTRA_AMOUNT, result)
        intent.flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        ctx.startActivity(intent)
    }

    /**
     * 获取网络ID列表
     * 优先返回主选链，然后是已过滤的网络，最后是所有可用网络
     * @return 网络ID列表
     */
    private fun getNetworkIds(): List<Long> {
        val networkIds = ArrayList<Long>()
        networkIds.add(primaryChainId) // 首先测试选定的链
        for (chainId in tokensService.getNetworkFilters()) {
            if (!networkIds.contains(chainId)) networkIds.add(chainId)
        }

        // 扫描未选择的网络
        for (networkInfo in ethereumNetworkRepository.getAvailableNetworkList()) {
            if (!networkIds.contains(networkInfo.chainId)) networkIds.add(networkInfo.chainId)
        }

        return networkIds
    }

    /**
     * 测试多个网络上的代币
     * 在所有支持的网络上查找代币
     * @param address 代币合约地址
     */
    fun testNetworks(address: String) {
        foundNetwork = false
        discoveredTokenList.clear()
        networkCount = ethereumNetworkRepository.getAvailableNetworkList().size
        _scanCount.postValue(networkCount)

        ethereumNetworkRepository.getAllActiveNetworks()
        _scanCount.postValue(networkCount)

        launchSafely {
            for (networkId in getNetworkIds()) {
                val tokenInfo = TokenInfo(address, "", "", 0, true, networkId)
                launchSafely(
                    onError = { throwable -> onTestError(throwable) }
                ) {
                    val type = withIO {
                        fetchTransactionsInteract.queryInterfaceSpec(tokenInfo)
                    }
                    if (type != null) {
                        testNetworkResult(tokenInfo, type)
                    }
                }
            }
        }

        handler.postDelayed({ stopScan() }, 60 * DateUtils.SECOND_IN_MILLIS)
    }

    /**
     * 处理网络测试结果
     * @param info 代币信息
     * @param type 合约类型
     */
    private fun testNetworkResult(info: TokenInfo, type: ContractType) {
        if (type != ContractType.OTHER) {
            foundNetwork = true
            launchSafely(
                onError = { throwable -> checkType(throwable, info.chainId, info.address.toString(), type) }
            ) {
                val updatedInfo = withIO {
                    tokensService.update(info.address.toString(), info.chainId, type)
                }
                if (updatedInfo != null) {
                    onTokensSetup(updatedInfo)
                }
            }
        } else {
            checkNetworkCount()
        }
    }

    /**
     * 停止扫描
     * 取消所有正在进行的网络扫描
     */
    fun stopScan() {
        _scanCount.postValue(0)
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 处理测试错误
     * @param throwable 异常
     */
    private fun onTestError(throwable: Throwable) {
        checkNetworkCount()
//        OnError(throwable)
    }

    /**
     * 检查网络计数
     * 更新扫描进度并处理扫描完成逻辑
     */
    private fun checkNetworkCount() {
        networkCount--
        _scanCount.postValue(networkCount)
        if (networkCount == 0 && !foundNetwork) {
            _noContract.postValue(true)
        }
        if (networkCount == 0 && discoveredTokenList.size > 0) {
            _allTokens.postValue(discoveredTokenList.toTypedArray())
        }
    }

    /**
     * 显示导入链接
     * @param context 上下文
     * @param importTxt 导入文本
     */
    fun showImportLink(context: Context, importTxt: String) {
        val intent = Intent(context, ImportTokenActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(C.IMPORT_STRING, importTxt)
        context.startActivity(intent)
    }

    /**
     * 获取指定链上的代币
     * @param chainId 链ID
     * @param address 代币合约地址
     * @return 代币对象
     */
    fun getToken(chainId: Long, address: String): Token? {
        return tokensService.getToken(chainId, address)
    }

    /**
     * 获取代币服务
     * @return 代币服务
     */
    fun getTokensService(): TokensService {
        return tokensService
    }

    /**
     * 获取资产定义服务
     * @return 资产定义服务
     */
    fun getAssetDefinitionService(): AssetDefinitionService {
        return assetDefinitionService
    }

    /**
     * 获取以太坊网络仓库
     * @return 以太坊网络仓库
     */
    fun ethereumNetworkRepository(): EthereumNetworkRepositoryType {
        return ethereumNetworkRepository
    }

    /**
     * 选择额外的链
     * 将新链添加到链选择中
     * @param selectedChains 选择的链列表
     */
    fun selectExtraChains(selectedChains: List<Long>) {
        // 添加新链到链选择
        // 获取当前列表并添加
        val uniqueList = HashSet(selectedChains)
        uniqueList.addAll(ethereumNetworkRepository.getFilterNetworkList())
        ethereumNetworkRepository.setFilterNetworkList(uniqueList.toTypedArray())
        ethereumNetworkRepository.commitPrefs()
        tokensService.setupFilter(true)
    }

    /**
     * 标记代币为启用状态
     * 设置所有选定的代币为启用和可见
     * 注意：我们需要更新'visibility changed'设置，以标记代币已被明确设置为可见
     * @param selected 选定的代币元数据列表
     */
    fun markTokensEnabled(selected: List<TokenCardMeta>) {
        if (_wallet.value == null) return
        launchIO {
            selected.forEach { tcm ->
                try {
                    tokensService.enableToken(_wallet.value?.address ?: "", tcm.contractAddress)
                } catch (e: Exception) {
                    // 忽略单个代币启用失败
                }
            }
        }
    }
}
