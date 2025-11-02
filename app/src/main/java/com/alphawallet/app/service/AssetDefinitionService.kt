package com.alphawallet.app.service

// ioDispatcher 已在 CoroutineUtils 中定义

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.entity.FragmentMessenger
import com.alphawallet.app.entity.QueryResponse
import com.alphawallet.app.entity.TokenLocator
import com.alphawallet.app.entity.UpdateType
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokenscript.EventUtils
import com.alphawallet.app.entity.tokenscript.TokenScriptFile
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction
import com.alphawallet.app.repository.TokenLocalSource
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.repository.entity.RealmCertificateData
import com.alphawallet.app.repository.entity.RealmTokenScriptData
import com.alphawallet.app.ui.HomeActivity
import com.alphawallet.app.util.CoroutineUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.HomeViewModel
import com.alphawallet.ethereum.EthereumNetworkBase
import com.alphawallet.token.entity.ActionModifier
import com.alphawallet.token.entity.Attribute
import com.alphawallet.token.entity.AttributeInterface
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.ContractInfo
import com.alphawallet.token.entity.EvaluateSelection
import com.alphawallet.token.entity.EventDefinition
import com.alphawallet.token.entity.FunctionDefinition
import com.alphawallet.token.entity.MethodArg
import com.alphawallet.token.entity.ParseResult
import com.alphawallet.token.entity.SigReturnType
import com.alphawallet.token.entity.TSAction
import com.alphawallet.token.entity.TSSelection
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.entity.TokenscriptContext
import com.alphawallet.token.entity.TokenscriptElement
import com.alphawallet.token.entity.TransactionResult
import com.alphawallet.token.entity.ViewType
import com.alphawallet.token.entity.XMLDsigDescriptor
import com.alphawallet.token.tools.TokenDefinition
import io.reactivex.Observable
import io.reactivex.Single
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import io.realm.exceptions.RealmException
import io.realm.exceptions.RealmPrimaryKeyConstraintException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.web3j.abi.DefaultFunctionEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.crypto.Keys
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.EthLog.LogResult
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import org.web3j.utils.Strings
import org.xml.sax.SAXException
import timber.log.Timber
import wallet.core.jni.Hash
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * AssetDefinitionService 是处理 TokenScript 文件的核心服务类
 *
 * 主要功能：
 * 1. 加载和管理 TokenScript 文件
 * 2. 解析 XML 格式的 TokenScript
 * 3. 处理智能合约事件监听
 * 4. 管理 TokenScript 签名验证
 * 5. 提供 TokenScript 相关的查询服务
 *
 * 设计模式：
 * - 单例模式：确保全局只有一个实例
 * - 依赖注入：通过构造函数注入依赖
 * - 观察者模式：使用协程和 Flow 处理异步操作
 *
 * 协程优化：
 * - 使用 CoroutineUtils 进行安全的协程操作
 * - 将 RxJava 操作转换为协程
 * - 优化异步操作的错误处理
 *
 * @author AlphaWallet Team
 * @since 1.0.0
 */
class AssetDefinitionService(
    private val ipfsService: IPFSServiceType,
    private val context: Context,
    private val notificationService: NotificationService,
    private val realmManager: RealmManager,
    private val tokensService: TokensService,
    private val tokenLocalSource: TokenLocalSource,
    private val alphaWalletService: AlphaWalletService,
) : ParseResult,
    AttributeInterface {
    // ==================== 协程相关配置 ====================

    /**
     * 协程作用域，用于管理所有协程生命周期
     */
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 协程调度器
     */
    private val ioDispatcher = Dispatchers.IO
    private val mainDispatcher = Dispatchers.Main

    // ==================== 核心数据成员 ====================

    companion object {
        const val ASSET_SUMMARY_VIEW_NAME: String = "item-view"
        const val ASSET_DETAIL_VIEW_NAME: String = "view"
        private const val ASSET_DEFINITION_DB = "ASSET-db.realm"
        private const val BUNDLED_SCRIPT = "bundled"
        private const val CHECK_TX_LOGS_INTERVAL: Long = 20
        private const val EIP5169_ISSUER = "EIP5169-IPFS"
        private const val TS_EXTENSION = ".tsml"
    }

    /**
     * 资产检查缓存：记录每个合约地址的最后检查时间
     * Key: 合约地址, Value: 最后检查时间戳
     */
    private val assetChecked: MutableMap<String, Long?> = ConcurrentHashMap()

    /**
     * 缓存的 TokenScript 定义，避免重复解析
     */
    private var cachedDefinition: TokenDefinition? = null

    /**
     * 事件定义列表：存储所有已加载的事件定义
     * Key: 事件键值, Value: 事件定义
     */
    private val eventList: ConcurrentHashMap<String, EventDefinition> = ConcurrentHashMap()

    /**
     * 资产加载锁：防止并发加载时的竞态条件
     */
    private val assetLoadingLock = Semaphore(1)

    /**
     * 事件监听器：定期检查事件日志
     */
    private var eventListenerJob: Job? = null

    /**
     * 事件连接锁：防止调试时的并发事件调用
     */
    private val eventConnection = Semaphore(1)

    /**
     * 主界面消息传递器
     */
    private var homeMessenger: FragmentMessenger? = null

    /**
     * TokenScript 工具类实例
     */
    val tokenscriptUtility: TokenscriptFunction =
        object : TokenscriptFunction() {
            // 默认实现，无重写方法
        }

    /**
     * 事件检查调度器
     */
    private var checkEventJob: Job? = null

    // ==================== 初始化 ====================

    /**
     * 初始化方法
     *
     * 设计假设：在任何给定时间只有一个实例
     * 这是项目的设计模式，其他类通过依赖注入获取此服务
     * 参见 RepositoriesModule 中的构造函数，仅在应用初始化时调用
     */
    init {
        // 删除所有事件数据（调试用）
        // deleteAllEventData()

        // 加载资产脚本
        loadAssetScripts()
    }

    // ==================== 公共接口方法 ====================

    /**
     * 获取 Token 本地数据源
     * @return TokenLocalSource 实例
     */
    fun getTokenLocalSource(): TokenLocalSource = tokenLocalSource

    /**
     * 加载所有 TokenScript 文件
     *
     * 加载顺序必须遵守，因为这是预期的开发者覆盖顺序：
     * 1. 如果脚本放在 /AlphaWallet 目录中，它应该覆盖从仓库服务器获取的脚本
     * 2. 如果开发者点击脚本意图，此脚本应该覆盖从服务器获取的脚本
     */
    private fun loadAssetScripts() {
        try {
            // 获取信号量以防止在加载完成前获取属性
            assetLoadingLock.acquire()
        } catch (e: InterruptedException) {
            Timber.e(e)
        }

        // 检查 Realm 脚本变更
//        val handledHashes = checkRealmScriptsForChanges()

        // 加载新文件
//        loadNewFiles(handledHashes.toMutableList())

        // 加载内部资产（捆绑的脚本）
        loadInternalAssets()

        // 完成加载
        finishLoading()
    }

    /**
     * 加载新文件
     *
     * 使用协程优化文件加载流程，提供更好的错误处理和并发控制
     *
     * @param handledHashes 已处理的文件哈希列表
     */
    private fun loadNewFiles(handledHashes: MutableList<String>) {
        CoroutineUtils.launchSafely(
            scope = serviceScope,
            dispatcher = ioDispatcher,
            onError = { error -> Timber.e(error, "加载新文件时发生错误") },
        ) {
            val fileList = buildFileList()

            // 并发处理文件，提高加载性能
            val validFiles =
                fileList
                    .asSequence()
                    .filter { file -> file.isFile }
                    .filter { file -> allowableExtension(file) }
                    .filter { file -> file.canRead() }
                    .toList()

            // 使用并发处理，但限制并发数量以避免资源过载
            validFiles.chunked(3).forEach { fileChunk ->
                val jobs =
                    fileChunk.map { file ->
                        async {
                            try {
                                val tsf = TokenScriptFile(context, file.absolutePath)
                                val hash = tsf.calcMD5()

                                // 跳过已处理的文件
                                if (handledHashes.contains(hash)) return@async

                                val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())

                                // 使用协程处理签名缓存
                                cacheSignature(file, td)
                                val originContracts = getOriginContracts(td)

                                withContext(mainDispatcher) {
                                    fileLoadComplete(originContracts, tsf, td)
                                }
                            } catch (e: Exception) {
                                handledHashes.add(
                                    TokenScriptFile(context, file.absolutePath).calcMD5(),
                                )
                                handleFileLoadError(e, file)
                            }
                        }
                    }
                // 等待当前批次完成再处理下一批次
                jobs.awaitAll()
            }
        }
    }

    /**
     * 加载捆绑的 TokenScript 文件
     *
     * 从 /assets 目录加载捆绑的脚本，例如 xDAI bridge
     * 使用协程优化加载流程，提供更好的错误处理
     */
    private fun loadInternalAssets() {
        CoroutineUtils.launchSafely(
            scope = serviceScope,
            dispatcher = ioDispatcher,
            onError = { error -> onError(error) },
        ) {
            // 删除所有内部脚本
            deleteAllInternalScriptFromRealm()

            // 加载捆绑的脚本
            localTSMLFilesList.forEach { asset ->
                try {
                    addContractAssets(asset)
                } catch (e: Exception) {
                    Timber.e(e, "加载捆绑资产失败: $asset")
                }
            }
        }
    }

    /**
     * 获取本地 TSML 文件列表
     *
     * 从 assets 目录中获取所有 .tsml 文件
     *
     * @return TSML 文件列表
     */
    private val localTSMLFilesList: List<String>
        get() = getLocalTSMLFiles()

    /**
     * 获取本地 TSML 文件的私有方法
     *
     * @return TSML 文件列表
     */
    private fun getLocalTSMLFiles(): List<String> {
        val localTSMLFilesStr = mutableListOf<String>()

        try {
            // 在方法中直接访问 context
            val assetManager: AssetManager = context.resources.assets
            val fileList: Array<String>? = assetManager.list("")

            // 使用传统的 for 循环避免空安全操作符问题
            if (fileList != null) {
                for (file in fileList) {
                    if (file.contains("tsml")) {
                        localTSMLFilesStr.add(file)
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "获取本地 TSML 文件失败")
        } catch (e: Exception) {
            Timber.e(e, "访问 assets 目录失败")
        }

        return localTSMLFilesStr
    }

    /**
     * 加载捆绑的TokenScript文件
     *
     * 主要功能：
     * - 从assets目录加载TokenScript文件
     * - 解析TokenDefinition并获取持有代币的合约信息
     * - 更新Realm数据库中的捆绑脚本数据
     * - 处理签名和证书数据
     *
     * @param asset assets目录中的文件路径
     * @return 是否成功加载和处理
     */
    private fun addContractAssets(asset: String): Boolean {
        return try {
            Timber.d("开始加载捆绑TokenScript: $asset")

            context.resources.assets.open(asset).use { input ->
                val token: TokenDefinition = parseFile(input)
                val tsf = TokenScriptFile(context, asset)

                // 安全获取持有代币的合约信息
                val holdingContracts: ContractInfo? = token.contracts.get(token.holdingToken)

                if (holdingContracts != null && holdingContracts.addresses.isNotEmpty()) {
                    // 处理每个网络的地址
                    for (network in holdingContracts.addresses.keys) {
                        val networkAddresses = holdingContracts.addresses[network]

                        // 安全处理网络地址列表
                        if (!networkAddresses.isNullOrEmpty()) {
                            for (address in networkAddresses) {
                                if (address.isNotBlank()) {
                                    updateRealmForBundledScript(network, address, asset, token)
                                }
                            }
                        }

                        // 处理签名和证书数据
                        processSignatureAndCertificate(tsf)
                    }
                    return true
                }
                return false
            }
        } catch (e: Exception) {
            Timber.e(e)
            return false
        }
    }

    /**
     * 处理签名和证书数据
     *
     * @param tsf TokenScriptFile对象
     */
    private fun processSignatureAndCertificate(tsf: TokenScriptFile) {
        try {
            val hash = tsf.calcMD5()
            val awSignature =
                XMLDsigDescriptor().apply {
                    result = "pass"
                    issuer = "AlphaWallet"
                    keyName = "AlphaWallet"
                    type = SigReturnType.SIGNATURE_PASS
                }

            tsf.determineSignatureType(awSignature)
            storeCertificateData(hash, awSignature)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    // ==================== realmManager 数据库操作  start ====================

    /**
     * 检查 Realm 数据库中的脚本变更
     *
     * 文件优先级顺序：
     * 1. 从服务器下载的签名文件
     * 2. 放置在 Android 外部目录的文件 (Android/data/<App Package Name>/files)
     * 3. 放置在 /AlphaWallet 目录的文件
     *
     * 根据放置顺序，文件可以被覆盖。从服务器下载的文件会被
     * 放置在 /AlphaWallet 目录中的同名脚本覆盖。
     *
     * @return 已处理的文件哈希列表
     */
    private fun checkRealmScriptsForChanges(): List<String> {
        val handledHashes: MutableList<String> = ArrayList()

        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                val realmData: RealmResults<RealmTokenScriptData> =
                    realm
                        .where(
                            RealmTokenScriptData::class.java,
                        ).findAll()

                for (entry in realmData) {
                    // 跳过已检查的文件（注意：如果一个合约有多个来源，可能有多个条目）
                    if (handledHashes.contains(entry.fileHash)) continue
                    val filePath: String? = entry.filePath
                    val fileHash: String? = entry.fileHash
                    if (filePath != null && fileHash != null) {
                        // 获取文件
                        val tsf = TokenScriptFile(context, filePath)
                        handledHashes.add(fileHash)

                        // 检查文件是否存在或是否已更改
                        if (!tsf.exists() || tsf.fileChanged(fileHash)) {
                            // 删除已更改的脚本
                            deleteTokenScriptFromRealm(realm, fileHash)

                            if (tsf.exists()) {
                                handledHashes.add(tsf.calcMD5()) // 添加新文件的哈希

                                // 重新解析脚本，文件哈希已更改
                                val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())

                                // 使用协程优化签名缓存流程
                                CoroutineUtils.launchSafely(
                                    scope = serviceScope,
                                    dispatcher = ioDispatcher,
                                    onError = { error -> handleFileLoadError(error, tsf) },
                                ) {
                                    // 1. 缓存签名（IO线程）
                                    cacheSignature(tsf, td)

                                    // 2. 获取原始合约（IO线程）
                                    val originContracts = getOriginContracts(td)

                                    // 3. 切换到主线程完成文件加载
                                    withContext(mainDispatcher) {
                                        fileLoadComplete(originContracts, tsf, td)
                                    }
                                }
                            }
                        } else if (entry.hasEvents()) {
                            // 填充事件
                            val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())
                            addToEventList(td)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "检查 Realm 脚本变更时发生错误")
        }

        return handledHashes
    }

    private fun deleteAllInternalScriptFromRealm() {
        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                realm.executeTransactionAsync { r: Realm ->
                    // have to remove all instances of this hash
                    val hashInstances: RealmResults<RealmTokenScriptData> =
                        r
                            .where(RealmTokenScriptData::class.java)
                            .equalTo("fileHash", BUNDLED_SCRIPT)
                            .findAll()
                    hashInstances.deleteAllFromRealm()
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    @Throws(RealmException::class)
    private fun storeCertificateData(
        hash: String,
        sig: XMLDsigDescriptor,
    ) {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm.executeTransaction { r: Realm ->
                // if signature present, then just update
                var realmData =
                    r
                        .where(RealmCertificateData::class.java)
                        .equalTo("instanceKey", hash)
                        .findFirst()

                if (realmData == null) {
                    realmData = r.createObject(RealmCertificateData::class.java, hash)
                }
                realmData!!.setFromSig(sig)
                r.insertOrUpdate(realmData)
            }
        }
    }

    // ==================== realmManager 数据库操作end ====================

    @Throws(RealmException::class)
    private fun deleteTokenScriptFromRealm(
        realm: Realm,
        fileHash: String,
    ) {
        // delete from realm
        realm.executeTransactionAsync { r: Realm ->
            // have to remove all instances of this hash
            val hashInstances: RealmResults<RealmTokenScriptData> =
                r
                    .where(
                        RealmTokenScriptData::class.java,
                    ).equalTo("fileHash", fileHash)
                    .findAll()

            val realmCert =
                r
                    .where(
                        RealmCertificateData::class.java,
                    ).equalTo("instanceKey", fileHash)
                    .findFirst()

            realmCert?.deleteFromRealm()

            // now delete all associated event data; script event descriptions may have changed
            for (script in hashInstances) {
                deleteEventDataForScript(script)
            }
            hashInstances.deleteAllFromRealm()
        }
    }

    private fun deleteEventDataForScript(scriptData: RealmTokenScriptData) {
        try {
//            val address  = tokensService.currentAddress ?: return
            realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                realm.executeTransaction { r: Realm ->
                    val realmEvents: RealmResults<RealmAuxData> =
                        r
                            .where(RealmAuxData::class.java)
                            .equalTo("tokenAddress", scriptData.getOriginTokenAddress())
                            .or()
                            .contains("instanceKey", scriptData.getOriginTokenAddress())
                            .findAll()
                    realmEvents.deleteAllFromRealm()
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun handleFileLoadError(
        throwable: Throwable,
        file: File,
    ) {
        Timber.d("ERROR WHILE PARSING: " + file.name + " : " + throwable.message)
    }

    /**-------------------------------完成文件加载后的相关方法   ----------------------------------------**/

    /**
     *
     *
     * 主要功能：
     * - 验证文件加载的完整性
     * - 处理合约定位器列表
     * - 更新Realm数据库中的TokenScript数据
     * - 处理认证相关的特殊逻辑
     * - 检查脚本版本和安全性
     *
     * 业务流程：
     * 1. 验证输入参数的有效性
     * 2. 检查是否需要处理认证逻辑
     * 3. 检查脚本版本和安全性
     * 4. 更新Realm数据库中的TokenScript数据
     * 5. 处理合约定位器列表
     *
     * @param originContracts 合约定位器列表，包含需要处理的合约信息
     * @param file TokenScript文件对象，包含文件路径和元数据
     * @param td TokenDefinition对象，包含代币定义和配置信息
     * @return 处理完成后的TokenDefinition对象
     *
     * 技术特点：
     * - 异常安全处理，确保单个合约错误不影响整体流程
     * - 详细的日志记录，便于调试和监控
     * - 认证逻辑的特殊处理
     * - 数据库事务确保数据一致性
     * - 脚本版本和安全性检查
     */
    private fun fileLoadComplete(
        originContracts: List<ContractLocator>,
        file: TokenScriptFile,
        td: TokenDefinition,
    ): TokenDefinition {
        // 1. 验证输入参数的有效性
        if (originContracts.isEmpty()) {
            Timber.d("合约定位器列表为空，跳过处理")
            return td
        }

        // 2. 检查认证逻辑的特殊处理
        if (td.attestation != null) {
            Timber.d("TokenDefinition包含认证信息，跳过标准合约处理")
            // TODO: Refactor once we handle multiple attestations
            return td
        }

        Timber.d("开始处理 ${originContracts.size} 个合约定位器")

        // 3. 获取主要链ID和事件状态
        val primaryChainId = getPrimaryChainId(originContracts)
        val hasEvents = td.hasEvents()

        try {
            // 4. 处理Realm数据库更新
            processRealmDatabaseUpdate(originContracts, file, td, hasEvents, primaryChainId)
        } catch (e: Exception) {
            Timber.e(e, "处理文件加载完成时发生错误: ${file.absolutePath}")
        }

        Timber.d("文件加载完成处理结束")
        return td
    }

    /**
     * 获取主要链ID
     *
     * @param originContracts 合约定位器列表
     * @return 主要链ID，如果列表为空则返回主网ID
     */
    private fun getPrimaryChainId(originContracts: List<ContractLocator>): Long =
        if (originContracts.isNotEmpty()) {
            originContracts.first().chainId
        } else {
            EthereumNetworkBase.MAINNET_ID
        }

    /**
     * 处理Realm数据库更新
     *
     * @param originContracts 合约定位器列表
     * @param file TokenScript文件对象
     * @param td TokenDefinition对象
     * @param hasEvents 是否包含事件
     * @param primaryChainId 主要链ID
     */
    private fun processRealmDatabaseUpdate(
        originContracts: List<ContractLocator>,
        file: TokenScriptFile,
        td: TokenDefinition,
        hasEvents: Boolean,
        primaryChainId: Long,
    ) {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->

            // 1. 检查安全区域的过期脚本
            if (isInSecureZone(file) && td.isSchemaLessThanMinimum) {
                Timber.w("检测到安全区域的过期脚本，删除文件并重新下载")
                removeFile(file.absolutePath)
                loadScriptFromServer(primaryChainId, getFileName(file) ?: return@use)
                return@use
            }

            // 2. 计算文件哈希
            val fileHash = file.calcMD5()
            Timber.d("文件哈希: $fileHash")

            // 3. 执行数据库事务
            realm.executeTransaction { transactionRealm ->
                originContracts.forEach { contractLocator ->
                    processContractData(
                        transactionRealm,
                        contractLocator,
                        file,
                        td,
                        hasEvents,
                        fileHash,
                    )
                }
            }
        }
    }

    /**
     * 处理单个合约数据
     *
     * @param realm Realm事务实例
     * @param contractLocator 合约定位器
     * @param file TokenScript文件对象
     * @param td TokenDefinition对象
     * @param hasEvents 是否包含事件
     * @param fileHash 文件哈希
     */
    private fun processContractData(
        realm: Realm,
        contractLocator: ContractLocator,
        file: TokenScriptFile,
        td: TokenDefinition,
        hasEvents: Boolean,
        fileHash: String,
    ) {
        try {
            // 1. 获取或创建数据库条目
            val entryKey = getTSDataKey(contractLocator.chainId, contractLocator.address)
            val entry = getOrCreateTokenScriptData(realm, entryKey)

            if (entry == null) {
                Timber.e("无法创建或获取TokenScript数据条目: $entryKey")
                return
            }

            // 2. 检查是否可以更新文件路径
            if (canUpdateFilePath(entry, file)) {
                updateTokenScriptData(entry, file, td, hasEvents, fileHash)
                Timber.v("成功更新TokenScript数据: $entryKey")
            } else {
                Timber.d("跳过更新TokenScript数据: $entryKey (安全区域限制)")
            }
        } catch (e: Exception) {
            Timber.e(e, "处理合约数据时发生错误: ${contractLocator.address}")
        }
    }

    /**
     * 获取或创建TokenScript数据条目
     *
     * @param realm Realm事务实例
     * @param entryKey 条目键值
     * @return TokenScript数据条目，如果创建失败则返回null
     */
    private fun getOrCreateTokenScriptData(
        realm: Realm,
        entryKey: String,
    ): RealmTokenScriptData? =
        try {
            // 1. 尝试查找现有条目
            var entry =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", entryKey)
                    .findFirst()

            // 2. 如果不存在则创建新条目
            if (entry == null) {
                entry = realm.createObject(RealmTokenScriptData::class.java, entryKey)
                Timber.v("创建新的TokenScript数据条目: $entryKey")
            } else {
                Timber.v("找到现有TokenScript数据条目: $entryKey")
            }

            entry
        } catch (e: Exception) {
            Timber.e(e, "获取或创建TokenScript数据条目时发生错误: $entryKey")
            null
        }

    /**
     * 检查是否可以更新文件路径
     *
     * @param entry TokenScript数据条目
     * @param file TokenScript文件对象
     * @return 是否可以更新
     */
    private fun canUpdateFilePath(
        entry: RealmTokenScriptData,
        file: TokenScriptFile,
    ): Boolean {
        val currentPath: String? = entry.filePath
        if (currentPath.isNullOrEmpty()) {
            return true
        }
        return isInSecureZone(currentPath)
    }

    /**
     * 更新TokenScript数据
     *
     * @param entry TokenScript数据条目（非空）
     * @param file TokenScript文件对象
     * @param td TokenDefinition对象
     * @param hasEvents 是否包含事件
     * @param fileHash 文件哈希
     */
    private fun updateTokenScriptData(
        entry: RealmTokenScriptData,
        file: TokenScriptFile,
        td: TokenDefinition,
        hasEvents: Boolean,
        fileHash: String,
    ) {
        try {
            // 更新文件哈希和路径
            entry.fileHash = fileHash
            entry.filePath = file.absolutePath

            // 更新Token名称列表
            entry.setNames(td.tokenNameList)

            // 更新视图列表
            entry.setViewList(td.views)

            // 更新事件状态
            entry.setHasEvents(hasEvents)

            // 更新认证模式UID
            entry.schemaUID = td.attestationSchemaUID
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

/**-------------------------------完成文件加载后的相关方法   ----------------------------------------**/

    private fun getTSDataKey(
        chainId: Long,
        address: String?,
    ): String {
        var address = address
        if (address.equals(tokenWalletAddress, ignoreCase = true)) {
            address = "ethereum"
        }

        return address?.lowercase(Locale.getDefault()) + "-" + chainId
    }

    /**
     * 销毁服务
     *
     * 清理所有资源，包括协程作用域、事件监听器等
     */
    fun onDestroy() {
        // 停止事件监听器
        stopEventListener()

        // 取消所有协程
        serviceScope.cancel()

        // 清理缓存
        cachedDefinition = null
        eventList.clear()
        assetChecked.clear()
    }

    /**
     * 构建文件列表
     *
     * 解析顺序必须改变，因为改进的解析方法：我们将第一个找到的文件写入数据库并跳过其他文件
     * 因此首先包含调试覆盖文件
     *
     * 文件优先级：
     * 1. /AlphaWallet 目录中的文件（最高优先级）
     * 2. 应用外部目录中的文件
     * 3. 从服务器下载的文件
     *
     * @return 非空 File 对象的列表
     */
    private fun buildFileList(): List<File> {
        val fileList: MutableList<File> = ArrayList()
        try {
            // 首先包含 AlphaWallet 目录中的文件 - 这些具有最高优先级
            if (checkReadPermission()) {
                val alphaWalletDir =
                    File(
                        "${Environment.getExternalStorageDirectory()}${File.separator}${HomeViewModel.ALPHAWALLET_DIR}",
                    )

                if (alphaWalletDir.exists()) {
                    alphaWalletDir.listFiles()?.let { files ->
                        fileList.addAll(files.filterNotNull())
                    }
                }
            }

            // 然后包含应用外部目录中的文件 - 当没有文件权限时放置在这里
            context.getExternalFilesDir("")?.listFiles()?.let { files ->
                fileList.addAll(files.filterNotNull())
            }

            // 最后包含从服务器下载的文件
            context.filesDir.listFiles()?.let { files ->
                fileList.addAll(files.filterNotNull())
            }
        } catch (e: Exception) {
            Timber.e(e, "构建文件列表失败")
        }

        if (fileList.isEmpty()) {
            finishLoading()
        }

        return fileList
    }

    override fun resolveOptimisedAttr(
        contract: ContractAddress,
        attr: Attribute,
        transactionResult: TransactionResult,
    ): Boolean {
        var optimised = false
        if (attr.function == null) return false
        val checkToken = tokensService.getToken(contract.chainId, contract.address) ?: return false
        val hasNoParams: Boolean = attr.function?.parameters?.isEmpty() == true

        when (attr.function?.method) {
            "balanceOf" -> // ensure the arg check for this function call is checking the correct balance address
                for (arg in attr.function!!.parameters) {
                    if (arg.parameterType == "address" && arg.element.ref == "ownerAddress") {
                        transactionResult.result = checkToken.balance.toString()
                        transactionResult.resultTime = checkToken.updateBlancaTime
                        optimised = true
                        break
                    }
                }

            "name" ->
                if (hasNoParams) {
                    transactionResult.result = checkToken.tokenInfo.name
                    transactionResult.resultTime = checkToken.updateBlancaTime
                    optimised = true
                }

            "symbol" ->
                if (hasNoParams) {
                    transactionResult.result = checkToken.tokenInfo.symbol
                    transactionResult.resultTime = checkToken.updateBlancaTime
                    optimised = true
                }

            "decimals" ->
                if (hasNoParams) {
                    transactionResult.result = checkToken.tokenInfo.decimals.toString()
                    transactionResult.resultTime = checkToken.updateBlancaTime
                    optimised = true
                }
        }

        return optimised
    }

    override fun getWalletAddr(): String {
        TODO("Not yet implemented")
    }

    private val tokenWalletAddress: String?
        get() = tokensService.getCurrentAddress()

    override fun getLastTokenUpdate(
        chainId: Long,
        address: String,
    ): Long {
        var txUpdateTime: Long = 0
        val token = tokensService.getToken(chainId, address)
        if (token != null) {
            txUpdateTime = token.lastTxTime
        }

        return txUpdateTime
    }

    override fun fetchAttribute(
        origin: ContractInfo,
        attributeName: String,
    ): Attribute? {
        var addr: String? = null
        var td: TokenDefinition? = null
        val chainId: Long =
            origin.addresses.keys
                .iterator()
                .next()
//        1. 使用 firstOrNull() (推荐，简洁且安全): 这是最符合 Kotlin 习惯的写法，
//        如果列表不为空则获取第一个元素， 否则返回 null。
        addr = origin.addresses[chainId]?.firstOrNull()
        if (addr != null) td = getAssetDefinition(chainId, addr)
        return td?.attributes?.get(attributeName)
    }

    override fun fetchAttrResult(
        origin: ContractAddress,
        attr: Attribute,
        tokenId: BigInteger,
    ): TokenScriptResult.Attribute? {
        val td: TokenDefinition? = getAssetDefinition(origin.chainId, origin.address)
        val originToken = tokensService.getToken(origin.chainId, origin.address)
        if (originToken == null || td == null) return null

        // produce result
        return runBlocking {
            withContext(ioDispatcher) {
                try {
                    // TODO: 这应该转换为 suspend 方法，但需要接口支持
                    tokenscriptUtility.fetchAttrResult(
                        originToken,
                        attr,
                        tokenId,
                        td,
                        this@AssetDefinitionService,
                        ViewType.VIEW,
                        UpdateType.UPDATE_IF_REQUIRED,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "获取属性结果失败")
                    null
                }
            }
        }
    }

    /**
     * Refreshes the stored values for a list of attributes
     * @param token
     * @return
     */

    /**
     * 刷新属性值
     *
     * 使用协程优化属性刷新操作，提供更好的并发处理
     *
     * @param token Token 实例
     * @param td TokenDefinition 实例
     * @param tokenId Token ID
     * @param attrs 属性列表
     * @return 包含操作结果的协程
     */
    suspend fun refreshAttributesAsync(
        token: Token,
        td: TokenDefinition,
        tokenId: BigInteger,
        attrs: List<Attribute>,
    ): Boolean =
        withContext(ioDispatcher) {
            try {
                // 并发处理属性更新
                attrs
                    .map { attr ->
                        async {
                            val targetTokenId = if (attr.usesTokenId()) tokenId else BigInteger.ZERO
                            updateAttributeResult(token, td, attr, targetTokenId)
                        }
                    }.awaitAll()

                true
            } catch (e: Exception) {
                Timber.e(e, "刷新属性失败")
                false
            }
        }

    /**
     * 重置属性
     *
     * 清除指定 TokenDefinition 的所有属性缓存数据
     * 使用协程优化数据库操作，提供更好的性能和错误处理
     *
     * @param td TokenDefinition 实例
     * @return 操作结果
     */
    suspend fun resetAttributesAsync(td: TokenDefinition): Boolean =
        withContext(ioDispatcher) {
            try {
                realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                    for (cInfo in td.contracts.values) {
                        for ((key, value) in cInfo.addresses.entries) {
                            for (addr in value) {
                                val dataBaseKey = "$addr-*-$key-*--func-key"
                                val functionResults: RealmResults<RealmAuxData> =
                                    realm
                                        .where(RealmAuxData::class.java)
                                        .like("instanceKey", dataBaseKey, Case.INSENSITIVE)
                                        .findAll()

                                if (functionResults.isNotEmpty()) {
                                    realm.executeTransaction { _ ->
                                        functionResults.deleteAllFromRealm()
                                    }
                                }
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Timber.e(e, "重置属性失败")
                false
            }
        }

    /**------------------------------未使用到的方法------------------------------------**/

    /**
     * 刷新所有属性值
     *
     * 刷新指定 Token 的所有 tokenId 的所有属性结果
     * 使用协程优化，支持并发处理多个属性
     *
     * @param token Token 实例
     * @return 操作结果
     */
    suspend fun refreshAllAttributes(token: Token): Boolean {
        val td: TokenDefinition = getAssetDefinition(token) ?: return false

        return withContext(ioDispatcher) {
            try {
                // 并发处理所有属性
                val jobs = td.attributes.values.map { attribute ->
                        async {
                            if (attribute.usesTokenId()) {
                                // 为每个 tokenId 更新属性
                                token.getTokenAssets().keys
                                    .map { tokenId ->
                                        async {
                                            updateAttributeResult(token, td, attribute, tokenId)
                                        }
                                    }.awaitAll()
                            } else {
                                updateAttributeResult(token, td, attribute, BigInteger.ZERO)
                            }
                        }
                    }

                jobs.awaitAll()
                true
            } catch (e: Exception) {
                Timber.e(e, "刷新所有属性失败")
                false
            }
        }
    }

    /**
     * 更新属性结果
     *
     * 从区块链获取属性值并存储到本地数据库
     * 使用协程优化网络请求和数据库操作
     *
     * @param token Token 实例
     * @param td TokenDefinition 实例
     * @param attr 属性定义
     * @param tokenId Token ID
     */
    private fun updateAttributeResult(
        token: Token,
        td: TokenDefinition,
        attr: Attribute?,
        tokenId: BigInteger,
    ) {
        if (attr?.function != null) {
            val useAddress = ContractAddress(attr.function)

            CoroutineUtils.launchSafely(
                scope = serviceScope,
                dispatcher = ioDispatcher,
                onError = { error ->
                    Timber.e(error, "更新属性结果失败: ${attr.name}")
                },
            ) {
                try {
                    // TODO: 待 tokenscriptUtility 支持协程后移除
                    val txResult =
                        tokenscriptUtility.fetchResultFromEthereum(
                            token,
                            useAddress,
                            attr,
                            tokenId,
                            td,
                            this@AssetDefinitionService,
                        )

                    storeAuxData(walletAddr, txResult)
                } catch (e: Exception) {
                    Timber.e(e, "从区块链获取属性值失败: tokenId=$tokenId, attr=${attr.name}")
                }
            }
        }
    }

    /*****------------------------------------------------------***/

    /**
     * 添加本地引用
     *
     * 向 TokenScript 工具类添加本地引用映射
     *
     * @param refs 引用映射表
     */
    fun addLocalRefs(refs: Map<String, String>) {
        tokenscriptUtility.addLocalRefs(refs)
    }

    /**
     * 从属性列表中获取指定类型
     *
     * @param key 属性名称
     * @param attrList 属性列表
     * @return 匹配的属性，如果未找到则返回 null
     */
    private fun getTypeFromList(
        key: String,
        attrList: List<Attribute>,
    ): Attribute? = attrList.find { it.name == key }

    /**
     * 从本地存储获取属性
     *
     * 不使用合约查询，直接从本地缓存获取 Token 属性
     *
     * @param token Token 实例
     * @param tokenId Token ID
     * @return TokenScript 结果对象
     */
    fun getTokenScriptResult(
        token: Token?,
        tokenId: BigInteger,
    ): TokenScriptResult {
        val result = TokenScriptResult()
        val definition: TokenDefinition? = getAssetDefinition(token)

        definition?.let { td ->
            td.attributes.keys.forEach { key ->
                getTokenscriptAttr(td, tokenId, key)?.let { result.setAttribute(key, it) }
            }
        }

        return result
    }

    /**
     * 获取 TokenScript 属性
     *
     * 根据属性类型从不同数据源获取属性值：
     * - 事件属性：返回不支持的编码
     * - 函数属性：从函数结果获取
     * - 位掩码属性：从 tokenId 计算
     *
     * @param td TokenDefinition 实例
     * @param tokenId Token ID
     * @param attribute 属性名称
     * @return 属性结果，如果失败则返回 null
     */
    private fun getTokenscriptAttr(
        td: TokenDefinition,
        tokenId: BigInteger,
        attribute: String,
    ): TokenScriptResult.Attribute? {
        val attrType: Attribute = td.attributes[attribute] ?: return null

        return try {
            when {
                attrType.event != null -> {
                    // 事件属性暂不支持
                    TokenScriptResult.Attribute(attrType, tokenId, "unsupported encoding")
                }

                attrType.function != null -> {
                    // 从函数获取属性值
                    val cAddr = ContractAddress(attrType.function)
                    val tResult = getFunctionResult(cAddr, attrType, tokenId)
                    tokenscriptUtility.parseFunctionResult(tResult, attrType)
                }

                else -> {
                    // 从位掩码计算属性值
                    val value = tokenId.and(attrType.bitmask).shiftRight(attrType.bitshift)
                    TokenScriptResult.Attribute(
                        attrType,
                        attrType.processValue(value),
                        attrType.getSyntaxVal(attrType.toString(value)).toString(),
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取 TokenScript 属性失败: attribute=$attribute, tokenId=$tokenId")
            TokenScriptResult.Attribute(attrType, tokenId, "unsupported encoding")
        }
    }

    /**
     * 获取指定属性
     *
     * 获取指定 Token 和 tokenId 的特定属性值
     *
     * @param token Token 实例
     * @param tokenId Token ID
     * @param attribute 属性名称
     * @return 属性结果，如果不存在则返回 null
     */
    fun getAttribute(
        token: Token?,
        tokenId: BigInteger,
        attribute: String,
    ): TokenScriptResult.Attribute? {
        val definition: TokenDefinition? = getAssetDefinition(token)
        return if (definition?.attributes?.containsKey(attribute) == true) {
            getTokenscriptAttr(definition, tokenId, attribute)
        } else {
            null
        }
    }

    /**
     * 检查读取外部存储权限
     *
     * @return 是否有读取权限
     */
    private fun checkReadPermission(): Boolean = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * 获取定义
     *
     * 根据 TokenScript 键值获取对应的 TokenDefinition
     * 首先检查缓存，然后从数据库查询
     *
     * @param tsKey TokenScript 键值（格式：address-chainId）
     * @return TokenDefinition 实例，如果未找到则返回 null
     */
    fun getDefinition(tsKey: String): TokenDefinition? {
        val elements = tsKey.split("-").filter { it.isNotEmpty() }
        if (elements.size < 2) {
            return null
        }

        val address = elements[0]
        val chainId = elements[1].toLongOrNull() ?: return null

        // 检查缓存
        checkCachedDefinition(chainId, address)?.let { return it }

        // 从数据库获取 TokenDefinition
        return try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                val tsData =
                    realm
                        .where(RealmTokenScriptData::class.java)
                        .equalTo("instanceKey", tsKey)
                        .findFirst()

                tsData?.let { data ->
                    cachedDefinition =
                        if (data.fileHash == BUNDLED_SCRIPT) {
                            data.filePath?.let {
                                // 处理捆绑脚本
                                getBundledDefinition(it)
                            }
                        } else {
                            data.filePath?.let {
                                // 处理外部脚本文件
                                val tf = TokenScriptFile(context, it)
                                parseFile(tf.getInputStreamSafe())
                            }
                        }
                    cachedDefinition
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取定义失败: tsKey=$tsKey")
            null
        }
    }

    /**
     * 检查缓存的定义
     *
     * 检查缓存中是否存在匹配的 TokenDefinition
     * 只匹配持有 Token 的合约信息
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return 匹配的 TokenDefinition，如果未找到则返回 null
     */
    private fun checkCachedDefinition(
        chainId: Long,
        address: String?,
    ): TokenDefinition? {
        cachedDefinition?.let { definition ->

            val holdingContracts = definition.contracts[definition.holdingToken]

            holdingContracts?.addresses?.get(chainId)?.let { addresses ->

                val targetAddress = address?.lowercase(Locale.getDefault())
                return if (addresses.any { it.equals(targetAddress, ignoreCase = true) }) {
                    definition
                } else {
                    null
                }
            }
        }
        return null
    }

    /**
     * 获取 TokenScript 文件
     *
     * 从数据库获取指定链和地址对应的 TokenScript 文件
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return TokenScriptFile 实例
     */
    fun getTokenScriptFile(
        chainId: Long,
        address: String,
    ): TokenScriptFile =
        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", getTSDataKey(chainId, address))
                    .findFirst()
                    ?.filePath
                    ?.let { TokenScriptFile(context, it) }
                    ?: TokenScriptFile(context)

//                if (tsData?.filePath != null) {
//                    TokenScriptFile(context, tsData.filePath!!)
//                } else {
//                    TokenScriptFile(context)
//                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
            TokenScriptFile(context)
        }

    /**
     * 获取调试路径
     *
     * 获取指定文件名在外部文件目录中的完整路径
     *
     * @param fileName 文件名
     * @return 完整的文件路径
     */
    fun getDebugPath(fileName: String): String = "${context.getExternalFilesDir("")}${File.separator}$fileName"

    /**
     * 获取TokenScript文件
     *
     * @param token 代币对象，可以为null
     * @return TokenScriptFile对象
     */
    fun getTokenScriptFile(token: Token?): TokenScriptFile =
        if (token == null) {
            // 如果token为null，返回默认的TokenScriptFile
            TokenScriptFile(context)
        } else if (token is Attestation) {
            // 如果token是Attestation类型（证明/认证类型的代币），调用专门的方法处理
            // Attestation是一种特殊的代币类型，代表数字证明或认证凭证
            // 例如：身份证明、学历证书、活动门票等数字化凭证
            getAttestationTSFile(token)
        } else {
            // 对于普通代币，使用token的tsKey加上.tsml扩展名来定位TokenScript文件
            locateTokenScriptFile(token.getTSKey() + TS_EXTENSION)
        }

    fun getAssetDefinitionDeepScan(attn: Attestation): TokenDefinition? {
        try {
            var tsf = locateTokenScriptFile(attn.getTSKey() + TS_EXTENSION) // try easy find
            if (tsf.exists()) {
                return parseFile(tsf.getInputStreamSafe())
            }

            // load filenames that are sufficient length to be attestation definitions
            // then include the files in the app external directory - these are placed here when there's no file permission
            val fileList: MutableList<File> = ArrayList()
            var files = context.getExternalFilesDir("")!!.listFiles()
            if (files != null) {
                fileList.addAll(Arrays.asList(*files)) // now add files in the app's external directory; /Android/data/[app-name]/files. These override internal
            }

            // finally the files downloaded from the server
            files = context.filesDir.listFiles()
            if (files != null) {
                fileList.addAll(Arrays.asList(*files)) // first add files in app internal area - these are downloaded from the server
            }

            // now check each file that is an attestation
            for (f in fileList) {
                if (f.isFile && f.canRead() && allowableExtension(f)) {
                    tsf = TokenScriptFile(context, f.absolutePath)
                    val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())
                    if (td.attestation != null && td.matchCollection(attn.getAttestationCollectionId(td))) {
                        return td
                    }
                }
            }
        } catch (e: Exception) {
            // NOP
        }

        return null
    }

    /**
     * 查找Attestation类型代币的TokenScript文件
     * 即使需要通过Schema匹配也要找到对应的TokenScript
     *
     * @param attn Attestation代币对象
     * @return 对应的TokenScriptFile对象
     */
    private fun getAttestationTSFile(attn: Attestation): TokenScriptFile {
        // 首先尝试直接从文件恢复 - 注意这对新导入的attestation不起作用，因为我们没有collection ID
        var tsfReturn = locateTokenScriptFile(attn.getTSKey() + TS_EXTENSION)
        if (tsfReturn.exists()) {
            // 如果文件存在，直接返回
            return tsfReturn
        }

        // 使用Realm数据库实例进行查找
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            // 否则，尝试将此attestation与有效的脚本匹配
            // 通过schemaUID在数据库中查找匹配的TokenScript数据
            val realmData: RealmResults<RealmTokenScriptData> =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("schemaUID", attn.getSchemaUID()) // 根据schema UID进行匹配
                    .findAll()

            // 遍历所有候选的TokenScript数据
            for (tsCandidate in realmData) {
                // 打开这个定义文件
                try {
                    // 根据文件路径定位TokenScript文件
                    if (tsCandidate.filePath != null) {
                        val tsf = locateTokenScriptFile(tsCandidate.filePath!!)
                        if (!tsf.exists()) {
                            // 如果文件不存在，跳过这个候选项
                            continue
                        }

                        // 解析TokenScript文件获取TokenDefinition
                        val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())
                        // 检查TokenDefinition是否与attestation的collection ID匹配
                        if (td.matchCollection(attn.getAttestationCollectionId(td))) {
                            // 找到匹配的文件，设置返回值并跳出循环
                            tsfReturn = tsf
                            break
                        }
                    }
                } catch (e: Exception) {
                    // 记录异常但继续处理下一个候选项
                    Timber.w(e)
                }
            }
        }
        // 返回找到的TokenScript文件（如果没找到则返回初始的空文件）
        return tsfReturn
    }

    private fun locateTokenScriptFile(fileName: String): TokenScriptFile {
        val tsf = TokenScriptFile(context, getDebugPath(fileName))
        if (tsf.exists()) {
            return tsf
        }

        val f = File(context.filesDir, fileName)
        if (f.exists()) {
            return TokenScriptFile(context, f.absolutePath)
        }

        return TokenScriptFile(context, fileName)
    }

    /**
     * 根据合约地址获取资产定义
     *
     * 该方法用于获取指定链和合约地址的 TokenScript 定义。如果本地缓存中没有找到定义，
     * 会尝试从服务器异步加载脚本文件。
     *
     * @param chainId 区块链网络ID
     * @param address 合约地址，如果为null则返回null
     * @return 返回对应的 TokenDefinition 对象，如果未找到则返回null
     */
    fun getAssetDefinition(
        chainId: Long,
        address: String?,
    ): TokenDefinition? {
        // 检查地址参数是否为空，如果为空则直接返回null
        var address = address ?: return null

        // 如果地址等于当前钱包地址（忽略大小写），则将地址设置为"ethereum"
        // 这是为了处理以太坊主网的特殊情况
        if (address.equals(tokenWalletAddress, ignoreCase = true)) {
            address = "ethereum"
        }

        // 检查资产定义是否已经被读取和缓存
        // 通过链ID和地址生成数据键来查找已缓存的TokenDefinition
        val assetDef: TokenDefinition? = getDefinition(getTSDataKey(chainId, address))

        // 如果没有找到资产定义且地址不是"ethereum"，则尝试从服务器加载
        if (assetDef == null && address != "ethereum") {
            // 尝试从Web服务器异步加载TokenScript文件
            // 将地址转换为小写以确保一致性
            loadScriptFromServer(chainId, address.lowercase(Locale.getDefault())) // 这个操作是异步完成的，完成后会更新显示
        }

        // 返回找到的资产定义，如果没有找到则返回null（使用默认值）
        return assetDef
    }

    /**
     * 入口方法 - 根据Token对象获取资产定义
     *
     * 该方法是获取TokenScript定义的主要入口点，支持缓存机制和文件解析
     *
     * @param token Token对象，包含链ID和合约地址信息
     * @return 返回对应的TokenDefinition对象，如果获取失败则返回null
     */
    fun getAssetDefinition(token: Token?): TokenDefinition? {
        // 检查token参数是否为空，如果为空则直接返回null
        if (token == null) {
            return null
        }

        // 检查是否存在已缓存的定义，如果存在则直接返回缓存的结果
        // 使用token的链ID和地址作为缓存键进行查找
        if (checkCachedDefinition(token.tokenInfo.chainId, token.getAddress()) != null) {
            return cachedDefinition
        }

        // 尝试获取并解析TokenScript文件
        try {
            // 获取对应token的TokenScript文件对象
            val tsf = getTokenScriptFile(token)
            // 解析TokenScript文件的输入流，生成TokenDefinition对象
            cachedDefinition = parseFile(tsf.getInputStreamSafe())
            // 返回解析后的TokenDefinition并更新缓存
            return cachedDefinition
        } catch (e: Exception) {
            // 捕获解析过程中的任何异常，静默处理（NOP - No Operation）
            // 这里不做任何操作，让方法继续执行到最后的return null
        }

        // 如果所有尝试都失败，返回null
        return null
    }

    /**
     * 异步获取资产定义
     *
     * 使用协程优化异步操作，提供更好的错误处理和性能
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return 包含 TokenDefinition 的协程
     */
    suspend fun getAssetDefinitionAsync(
        chainId: Long,
        address: String?,
    ): TokenDefinition {
        if (address == null) {
            return TokenDefinition()
        }

        val convertedAddr = if (address.equals(tokenWalletAddress, ignoreCase = true)) "ethereum" else address.lowercase(Locale.getDefault())

        return getAssetDefinitionAsync(
            getDefinition(getTSDataKey(chainId, address)),
            chainId,
            convertedAddr,
        )
    }

    /**
     * 异步获取资产定义（私有方法）
     *
     * @param assetDef 现有的资产定义
     * @param chainId 链ID
     * @param contractName 合约名称
     * @return 资产定义协程
     */
    private suspend fun getAssetDefinitionAsync(
        assetDef: TokenDefinition?,
        chainId: Long,
        contractName: String?,
    ): TokenDefinition =
        when {
            assetDef != null -> assetDef
            contractName != "ethereum" -> {
                // 此阶段脚本不会替换现有脚本，可以安全地写入数据库
                try {
                    val newFile = fetchXMLFromServer(chainId, contractName?.lowercase(Locale.getDefault()) ?: "")
                    handleNewTSFile(newFile ?: File(""))
                } catch (e: Exception) {
                    Timber.e(e, "获取资产定义失败: chainId=$chainId, contract=$contractName")
                    TokenDefinition()
                }
            }
            else -> TokenDefinition()
        }

    /**
     * 异步获取 Token 的资产定义
     *
     * @param token Token 实例
     * @return 包含 TokenDefinition 的协程
     */
    suspend fun getAssetDefinitionAsync(token: Token): TokenDefinition {
        var contractName = token.tokenInfo.address
        if (contractName.equals(tokenWalletAddress, ignoreCase = true)) {
            contractName = "ethereum"
        }

        // 等待资产定义加载完成
        waitForAssets()

        return getAssetDefinitionAsync(
            getDefinition(token.getTSKey()),
            token.tokenInfo.chainId,
            contractName,
        )
    }

    private fun waitForAssets() {
        try {
            assetLoadingLock.acquire()
        } catch (e: InterruptedException) {
            Timber.e(e)
        } finally {
            assetLoadingLock.release()
        }
    }

    fun getTokenName(
        chainId: Long,
        address: String,
        count: Int,
    ): String? {
        var Eaddress = address
        var tokenName: String? = null
        if (address.equals(tokenWalletAddress, ignoreCase = true)) Eaddress = "ethereum"
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData =
                realm
                    .where(
                        RealmTokenScriptData::class.java,
                    ).equalTo("instanceKey", getTSDataKey(chainId, address))
                    .findFirst()
            if (tsData != null) {
                tokenName = tsData.getName(count)
            }
        }
        return tokenName
    }

    /**
     * 从服务获取Token
     * @param chainId 链ID
     * @param address 合约地址
     * @return Token对象，如果未找到则返回null
     */
    fun getTokenFromService(
        chainId: Long,
        address: String,
    ): Token? = tokensService.getToken(chainId, address)

    /**
     * Get the issuer label given the contract address
     * Note: this is optimised so as we don't need to keep loading in definitions as the user scrolls
     *
     * @param token
     * @return
     */
    fun getIssuerName(token: Token): String {
        var issuer = token.getNetworkName()

        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                val tsData =
                    realm
                        .where(RealmTokenScriptData::class.java)
                        .equalTo("instanceKey", token.getTSKey())
                        .findFirst()
                if (tsData != null) {
                    tsData.fileHash?.let {
                        val sig = getCertificateFromRealm(it)
                        if (sig?.keyName != null) issuer = sig.keyName ?: issuer
                    }
                }
            }
        } catch (e: Exception) {
            // no action
        }

        return issuer
    }

    /**
     * 从服务器加载脚本
     *
     * 使用协程优化网络请求，提供更好的错误处理和重试机制
     *
     * @param chainId 链ID
     * @param correctedAddress 合约地址
     */
    private fun loadScriptFromServer(
        chainId: Long,
        correctedAddress: String,
    ) {
        // 从缓存映射中获取该合约地址的上次检查时间
        val lastCheckTime = assetChecked[correctedAddress]
        // 获取当前系统时间戳（毫秒）
        val currentTime = System.currentTimeMillis()
        // 定义缓存有效期为1小时（1000毫秒 * 60秒 * 60分钟）
        val oneHourInMillis = 1000L * 60L * 60L

        // 检查缓存有效性：如果从未检查过(null)或者距离上次检查超过1小时，则需要重新从服务器加载
        if (lastCheckTime == null || (currentTime > (lastCheckTime + oneHourInMillis))) {
            // 使用协程工具类安全启动异步任务
            CoroutineUtils.launchSafely(
                scope = serviceScope, // 使用服务级别的协程作用域
                dispatcher = ioDispatcher, // 在IO线程池中执行网络操作
                onError = { error -> onError(error) }, // 统一的错误处理回调
            ) {
                try {
                    // 从服务器获取TokenScript XML文件
                    val newFile = fetchXMLFromServer(chainId, correctedAddress)
                    // 处理新下载的文件，解析为TokenDefinition对象（如果文件为null则创建空文件）
                    val tokenDefinition = handleNewTSFile(newFile ?: File(""))

                    // 切换到主线程执行UI相关操作
                    withContext(mainDispatcher) {
                        // 通知加载完成，可能触发UI更新
                        loadComplete(tokenDefinition)
                    }
                } catch (e: Exception) {
                    // 记录详细的错误日志，包含链ID和合约地址信息
                    Timber.e(e, "从服务器加载脚本失败: chainId=$chainId, address=$correctedAddress")
                    // 调用统一的错误处理方法
                    onError(e)
                }
            }
        }
        // 如果在缓存有效期内，则直接跳过，不进行任何操作
    }

    private fun loadComplete(td: TokenDefinition) {
        Timber.d("TS LOAD: %s", td.getTokenName(1))
    }

    private fun onError(throwable: Throwable) {
        Timber.e(throwable)
    }

    @Throws(Exception::class)
    private fun parseFile(xmlInputStream: InputStream): TokenDefinition {
        val locale = context.resources.configuration.locales[0]

        return TokenDefinition(xmlInputStream, locale, this)
    }

    /**
     * 安全获取TokenScriptFile的输入流
     * @return 非空的InputStream
     * @throws IOException 当输入流为空时
     */
    @Throws(IOException::class)
    private fun TokenScriptFile.getInputStreamSafe(): InputStream =
        this.inputStream
            ?: throw IOException("TokenScriptFile输入流为空")

    /**
     * 处理新的 TokenScript 文件
     *
     * 处理流程：
     * 1. 检查有效性和原始令牌
     * 2. 检查现有文件，判断是否为调试文件或服务器脚本
     * 3. 更新签名数据
     * 4. 更新数据库
     *
     * @param newFile 新的 TokenScript 文件
     * @return TokenDefinition 对象
     */
    private suspend fun handleNewTSFile(newFile: File): TokenDefinition {
        return withContext(ioDispatcher) {
            try {
                // 如果文件不存在，返回未更改脚本信号
                if (!newFile.exists()) {
                    return@withContext signalUnchangedScript(newFile.name)
                }

                val tsf = TokenScriptFile(context, newFile.absolutePath)
                val td = parseFile(tsf.getInputStreamSafe())
                val originContracts = getOriginContracts(td)
                val schemaUID = td.attestationSchemaUID

                // 处理文件和数据库更新
                val isDebugOverride = tsf.isDebug
                updateScriptEntriesInRealm(originContracts, isDebugOverride, tsf.calcMD5(), schemaUID)
                cachedDefinition = td

                // 缓存签名
                cacheSignature(newFile, td)

                // 完成文件加载
                withContext(mainDispatcher) {
                    fileLoadComplete(originContracts, tsf, td)
                }

                td
            } catch (e: Exception) {
                Timber.e(e, "处理新 TokenScript 文件失败: ${newFile.name}")
                TokenDefinition()
            }
        }
    }

    /**
     * 发送未更改脚本信号
     *
     * @param name 脚本名称
     * @return 占位符 TokenDefinition
     */
    private suspend fun signalUnchangedScript(name: String): TokenDefinition =
        withContext(ioDispatcher) {
            val placeHolder = TokenDefinition()
            if (name == TokenDefinition.UNCHANGED_SCRIPT) {
                placeHolder.nameSpace = TokenDefinition.UNCHANGED_SCRIPT
            }
            placeHolder
        }

    private fun updateScriptEntriesInRealm(
        origins: List<ContractLocator>,
        isDebug: Boolean,
        newFileHash: String,
        schemaUID: String,
    ) {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm.executeTransaction { r: Realm ->
                for (cl in origins) {
                    val entryKey = getTSDataKey(cl.chainId, cl.address)
                    val realmData =
                        r
                            .where(RealmTokenScriptData::class.java)
                            .equalTo("instanceKey", entryKey)
                            .findFirst()
                    // delete the existing entry if this script is debug, or if the old script is in the server area
                    val filepath: String? = realmData?.filePath
                    if (realmData != null && (isDebug || (filepath != null && isInSecureZone(filepath)))) {
                        val realmCert =
                            r
                                .where(RealmCertificateData::class.java)
                                .equalTo("instanceKey", realmData.fileHash)
                                .findFirst()
                        if (realmCert != null && realmData.fileHash != newFileHash) {
                            realmCert.deleteFromRealm() // don't delete cert if new cert will overwrite it
                        }
                        deleteEventDataForScript(realmData)
                        if (realmData.fileHash != newFileHash) {
                            realmData.deleteFromRealm()
                        }
                    }
                }
            }
        }
    }

    /**
     * 从合约获取TokenScript文件
     *
     * 检查合约中的TokenScript URI，下载并存储有效的脚本文件。
     * 支持多个URI的数组，按顺序检查并返回第一个有效的脚本。
     *
     * @param token 代币对象
     * @param updateFlag 更新标志的LiveData
     * @return 下载的TokenScript文件
     */
    private suspend fun fetchTokenScriptFromContract(
        token: Token?,
        updateFlag: MutableLiveData<Boolean>?,
    ): File = withContext(ioDispatcher) {
        if (token == null) {
            return@withContext File("")
        }

        // 支持URI数组，依次检查每个URI并返回第一个有效条目
        val uriList = token.getScriptURI()
        for (uri in uriList) {
            // 对于未更改的IPFS提前返回
            if (matchesExistingScript(token, uri)) {
                break // 返回未更改的脚本/未找到
            }
            // 依次下载每个脚本，返回第一个有效脚本
            if (!TextUtils.isEmpty(uri) && updateFlag != null) {
                updateFlag.postValue(true)
            }
            val scriptCandidate = downloadScript(uri, 0)
            if (!TextUtils.isEmpty(scriptCandidate.first)) {
                val scriptData = Pair<String, Pair<String?, Boolean>>(uri, scriptCandidate)
                return@withContext storeEntry(token, scriptData)
            }
        }
        // 如果没有找到有效脚本，返回空文件
        File("")
    }

    // Write the TokenScript to Android Storage

    /** @noinspection ResultOfMethodCallIgnored
     */
    @Throws(IOException::class)
    private fun storeEntry(token: Token, scriptData: Pair<String, Pair<String?, Boolean>>, ): File {
        if (TextUtils.isEmpty(scriptData.second.first) || scriptData.second.first == TokenDefinition.UNCHANGED_SCRIPT) {
            return File(TokenDefinition.UNCHANGED_SCRIPT) // blank file with UNCHANGED name
        }

        var tempFileKey = token.getTSKey()

        // ensure url is correct
        token.tokenInfo.address?.let { updateScriptURLIfRequired(token.tokenInfo.chainId, it, scriptData.first ?: "") }

        if (!checkFileDiff(tempFileKey, scriptData.second)) {
            return File(TokenDefinition.UNCHANGED_SCRIPT)
        }

        val tempStoreFile = storeFile(tempFileKey, scriptData.second)

        var tsf = TokenScriptFile(context, tempStoreFile.absolutePath)

        val td: TokenDefinition? = getTokenDefinition(tsf)

        val preHash: ByteArray? = td?.attestationCollectionPreHash

        if (preHash != null) {
            td.attestation?.let {
                if (token is Attestation && !it.compareIssuerKey(token.getIssuer())) {
                    // refuse to download
                    tempStoreFile.delete()
                    return File("")
                }
            }

            // store this TokenScript with the correct CollectionId
            tempFileKey =
                Numeric.toHexString(Hash.keccak256(preHash)) + "-" + token.tokenInfo.chainId + TS_EXTENSION
            val updatedFile = File(context.filesDir, tempFileKey)
            tempStoreFile.renameTo(updatedFile)
            tsf = TokenScriptFile(context, updatedFile.absolutePath)
        }

        val fileHash = tsf.calcMD5()
        val tokenKey = tempFileKey
        val storeFile: File = tsf

        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                realm.executeTransaction { r: Realm ->
                    // String entryKey = getTSDataKey(token.tokenInfo.chainId, token.tokenInfo.address);
                    var entry =
                        r
                            .where(
                                RealmTokenScriptData::class.java,
                            ).equalTo("instanceKey", tokenKey)
                            .findFirst()

                    if (entry == null) {
                        entry = r.createObject(RealmTokenScriptData::class.java, tokenKey)
                    }

                    entry?.fileHash = fileHash
                    entry?.ipfsPath = scriptData.first // store scriptUri path
                    entry?.filePath = storeFile.absolutePath
                    entry?.schemaUID = td?.attestationSchemaUID
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }

        // check signature using the endpoint associated with scriptUri
        // otherwise we use the endpoint that takes the full encoded file
        return storeFile
    }

    private fun updateScriptURLIfRequired(
        chainId: Long,
        address: String,
        scriptURL: String,
    ) {
        val fileUrl = getScriptUrl(chainId, address)
        if (!Strings.isEmpty(fileUrl) && (Strings.isEmpty(scriptURL) || fileUrl.equals(scriptURL, ignoreCase = true,))) {
            return
        }

        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                realm.executeTransaction { r: Realm ->
                    val entryKey = getTSDataKey(chainId, address)
                    var entry =
                        r
                            .where(
                                RealmTokenScriptData::class.java,
                            ).equalTo("instanceKey", entryKey)
                            .findFirst()

                    if (entry == null) {
                        entry =
                            r.createObject(
                                RealmTokenScriptData::class.java,
                                entryKey,
                            )
                    }

                    entry!!.ipfsPath = scriptURL
                    r.insertOrUpdate(entry)
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    private fun matchesExistingScript(
        token: Token,
        uri: String,
    ): Boolean {
        // TODO: calculate and use the IPFS CID to validate existing script against IPFS locator
        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                val entryKey =
                    token.getTSKey() // getTSDataKey(token.tokenInfo.chainId, token.tokenInfo.address);
                val tsf = getTokenScriptFile(token)

                val entry =
                    realm
                        .where(
                            RealmTokenScriptData::class.java,
                        ).equalTo("instanceKey", entryKey)
                        .findFirst()
                if (entry != null && Utils.isIPFS(entry.ipfsPath) &&
                    !TextUtils.isEmpty(entry.fileHash) && !TextUtils.isEmpty(entry.filePath) &&
                    !TextUtils.isEmpty(
                        entry.ipfsPath,
                    ) && entry.ipfsPath == uri &&
                    tsf.exists()
                ) {
                    return true
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }

        return false
    }

    /**
     * 如果需要则尝试从服务器获取文件
     *
     * @param contractScript 本地合约脚本文件
     * @param address 合约地址
     * @param chainId 链 ID
     * @return 文件或 null
     */
    private suspend fun tryServerIfRequired(
        contractScript: File,
        address: String,
        chainId: Long,
    ): File? =
        if (contractScript.exists() || contractScript.name == TokenDefinition.UNCHANGED_SCRIPT) {
            contractScript
        } else {
            fetchXMLFromServer(chainId, address)
        }

    /**
     * 从服务器获取 XML 文件
     *
     * @param chainId 链 ID
     * @param address 合约地址
     * @return 下载的文件或默认文件
     */
    private suspend fun fetchXMLFromServer(
        chainId: Long,
        address: String,
    ): File? {
        return withContext(ioDispatcher) {
            try {
                val defaultReturn = File("")
                if (address.isEmpty()) return@withContext defaultReturn

                var result = getDownloadedXMLFile(address)

                // 检查文件是否存在
                var fileTime: Long = 0
                if (result != null && result.exists()) {
                    val td: TokenDefinition? = getTokenDefinition(result)

                    if (td != null && td.isSchemaLessThanMinimum) {
                        removeFile(result.absolutePath)
                        assetChecked[address] = 0L
                    } else {
                        fileTime = result.lastModified()
                    }
                } else {
                    result = defaultReturn
                }

                // 检查是否需要重新下载（1小时缓存）
                if (assetChecked[address] != null &&
                    (System.currentTimeMillis() > (assetChecked[address]!! + 1000L * 60L * 60L))
                ) {
                    return@withContext result
                }

                // 使用更新的服务器
                val serverUrl =
                    TokenDefinition.TOKENSCRIPT_STORE_SERVER
                        .replace(
                            TokenDefinition.TOKENSCRIPT_ADDRESS,
                            address,
                        ).replace(TokenDefinition.TOKENSCRIPT_CHAIN, chainId.toString())

                var downloadResponse = downloadScript(serverUrl, fileTime)
                val offchainScriptUri = getOffchainScriptUri(downloadResponse)

                if (!TextUtils.isEmpty(offchainScriptUri)) {
                    downloadResponse = downloadScript(offchainScriptUri, fileTime)
                    if (!TextUtils.isEmpty(downloadResponse.first)) {
                        storeFile(address, downloadResponse)
                    }
                }

                assetChecked[address] = System.currentTimeMillis()
                result
            } catch (e: Exception) {
                Timber.e(e, "从服务器获取 XML 失败: $address")
                File("")
            }
        }
    }

    private fun getOffchainScriptUri(downloadResponse: Pair<String?, Boolean>): String {
        var offchainScriptResponse = ""
        try {
            val response = JSONObject(downloadResponse.first)
            val scriptUri = response.getJSONObject("scriptURI")
            val offchainLinks = scriptUri.getJSONArray("offchain")
            if (offchainLinks.length() > 0) {
                offchainScriptResponse = offchainLinks.getString(0)
            }
        } catch (e: Exception) {
            offchainScriptResponse = ""
        }

        return offchainScriptResponse
    }

    private fun downloadScript(
        Uri: String,
        currentFileTime: Long,
    ): Pair<String?, Boolean> {
        if (Uri == TokenDefinition.UNCHANGED_SCRIPT) {
            return Pair<String?, Boolean>(TokenDefinition.UNCHANGED_SCRIPT, false)
        }

        val isIPFS = Utils.isIPFS(Uri)

        try {
            val response: QueryResponse? = ipfsService.performIO(Uri, getHeaders(currentFileTime))
            when (response?.code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {}
                HttpURLConnection.HTTP_OK -> return Pair(response.body, isIPFS)
                else -> {}
            }
        } catch (e: Exception) {
            if (!TextUtils.isEmpty(Uri)) // throws on empty, which is expected
                {
                    Timber.w(e)
                }
        }

        return Pair("", false)
    }

    @Throws(PackageManager.NameNotFoundException::class)
    private fun getHeaders(currentFileTime: Long): Array<String?> {
        val format = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val dateFormat = format.format(Date(currentFileTime))

        val manager: PackageManager = context.packageManager
        val info: PackageInfo =
            manager.getPackageInfo(
                context.packageName,
                0,
            )
        val appVersion = info.versionName
        val OSVersion = Build.VERSION.RELEASE.toString()

        return arrayOf(
            "Accept",
            "text/xml; charset=UTF-8",
            "X-Client-Name",
            "AlphaWallet",
            "X-Client-Version",
            appVersion,
            "X-Platform-Name",
            "Android",
            "X-Platform-Version",
            OSVersion,
            "If-Modified-Since",
            dateFormat,
        )
    }

    private fun finishLoading() {
        assetLoadingLock.release()
        // remove event listener update for now
        /*if (Utils.isAddressValid(tokensService.getCurrentAddress()))
        {
            updateEventBlockTimes();
            startEventListener();
        }*/
    }

    private fun removeFile(filename: String) {
        try {
            val fileToDelete = File(filename)
            fileToDelete.delete()
        } catch (e: Exception) {
            // ignore error
        }
    }

    /**
     * 处理捆绑脚本 并解析文件
     */
    private fun getBundledDefinition(asset: String): TokenDefinition? {
        var td: TokenDefinition? = null
        try {
            context.resources.assets.open(asset).use { input ->
                td = parseFile(input)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return td
    }

    private fun updateRealmForBundledScript(
        chainId: Long,
        address: String,
        asset: String,
        td: TokenDefinition,
    ) {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm.executeTransaction { r: Realm ->
                val entryKey = getTSDataKey(chainId, address)
                var entry =
                    r
                        .where(
                            RealmTokenScriptData::class.java,
                        ).equalTo("instanceKey", entryKey)
                        .findFirst()

                if (entry == null) {
                    entry =
                        r.createObject(
                            RealmTokenScriptData::class.java,
                            entryKey,
                        )
                }
                entry!!.filePath = asset
                entry.setViewList(td.views)
                entry.setNames(td.tokenNameList)
                entry.setHasEvents(td.hasEvents())
                entry.setViewList(td.views)
                entry.schemaUID = td.attestationSchemaUID
                entry.fileHash = BUNDLED_SCRIPT
            }
        }
    }

    private fun getTokenDefinition(file: File?): TokenDefinition? {
        try {
            FileInputStream(file).use { input ->
                return parseFile(input)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return null
    }

    /**
     * 获取原始合约定位器列表
     *
     * 从 TokenDefinition 中提取持有代币的合约信息，并转换为 ContractLocator 列表。
     * 如果找不到对应的合约信息，则返回空列表。
     *
     * @param tokenDef 代币定义对象，包含合约信息和持有代币标识
     * @return 合约定位器列表，如果没有找到合约信息则返回空列表
     */
    private fun getOriginContracts(tokenDef: TokenDefinition): List<ContractLocator> {
        // 安全获取持有代币的合约信息，处理可能的空值情况
        val holdingContracts: ContractInfo? = tokenDef.contracts[tokenDef.holdingToken]

        return if (holdingContracts != null) {
            // 找到合约信息时，添加到事件列表并返回合约定位器
            addToEventList(tokenDef)
            ContractLocator.fromContractInfo(holdingContracts)
        } else {
            // 未找到合约信息时返回空列表
            ArrayList()
        }
    }

    private fun addToEventList(tokenDef: TokenDefinition) {
        for (attrName in tokenDef.attributes.keys) {
            val attr: Attribute? = tokenDef.attributes[attrName]
            if (attr != null) {
                if (attr.event != null && attr.event?.contract != null) {
                    checkAddToEventList(attr.event!!) // note: event definition contains link back to the contract it refers to
                }
            }
        }

        if (tokenDef.activityCards.isNotEmpty()) {
            for (activityName in tokenDef.activityCards.keys) {
                val ev: EventDefinition = tokenDef.getActivityEvent(activityName)
                checkAddToEventList(ev)
            }
        }
    }

    private fun checkAddToEventList(ev: EventDefinition) {
        val eventKey: String = ev.eventKey
        eventList[eventKey] = ev
    }

    /**
     * 停止事件监听器
     *
     * 取消所有正在运行的事件检查协程
     */
    fun stopEventListener() {
        eventListenerJob?.cancel()
        eventListenerJob = null
        checkEventJob?.cancel()
        checkEventJob = null
    }

    /**
     * 启动事件监听器
     *
     * 使用协程优化事件监听，提供更好的性能和错误处理
     * 定期检查事件日志以获取需要事件的脚本
     */
    fun startEventListener() {
        if (assetLoadingLock.availablePermits() == 0) return

        // 停止现有的事件监听器
        stopEventListener()

        // 使用协程启动事件监听
        eventListenerJob =
            CoroutineUtils.launchSafely(
                scope = serviceScope,
                dispatcher = ioDispatcher,
                onError = { error ->
                    Timber.e(error, "事件监听器启动失败")
                },
            ) {
                while (isActive) {
                    try {
                        checkEventsAsync()
                        delay(CHECK_TX_LOGS_INTERVAL * 1000) // 转换为毫秒
                    } catch (e: Exception) {
                        Timber.e(e, "事件检查过程中发生错误")
                        delay(CHECK_TX_LOGS_INTERVAL * 1000) // 出错时也要延迟
                    }
                }
            }
    }

    /**
     * 异步检查事件
     *
     * 检查对应令牌的事件
     */
    private suspend fun checkEventsAsync() {
        withContext(ioDispatcher) {
            for (ev in eventList.values) {
                try {
                    getEventAsync(ev)
                } catch (e: Exception) {
                    Timber.e(e, "处理事件失败: ${ev.getEventKey()}")
                }
            }
        }
    }

    /**
     * 异步获取事件
     *
     * @param ev 事件定义
     */
    private suspend fun getEventAsync(ev: EventDefinition) {
        withContext(ioDispatcher) {
            try {
                val filter = getEventFilter(ev) ?: return@withContext
                if (BuildConfig.DEBUG) eventConnection.acquire() // 防止调试时的并发事件调用

                val walletAddress = tokenWalletAddress
                val web3j = TokenRepository.getWeb3jService(ev.getEventChainId())

                // 使用协程启动日志处理
                checkEventJob =
                    CoroutineUtils.launchSafely(
                        scope = serviceScope,
                        dispatcher = ioDispatcher,
                        onError = { error ->
                            if (BuildConfig.DEBUG) eventConnection.release()
                        },
                    ) {
                        if (walletAddress != null) {
                            handleLogsAsync(ev, filter, web3j, walletAddress)
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "获取事件失败")
                if (BuildConfig.DEBUG) eventConnection.release()
            }
        }
    }

    /**
     * 异步处理事件日志
     *
     * @param ev 事件定义
     * @param filter 事件过滤器
     * @param web3j Web3j 实例
     * @param walletAddress 钱包地址
     * @return 交易哈希
     */
    private suspend fun handleLogsAsync(
        ev: EventDefinition,
        filter: EthFilter,
        web3j: Web3j,
        walletAddress: String,
    ): String =
        withContext(ioDispatcher) {
            var txHash = ""
            try {
                val ethLogs: EthLog = web3j.ethGetLogs(filter).send()
                txHash = processLogs(ev, ethLogs.getLogs(), walletAddress)
            } catch (e: Exception) {
                Timber.e(e, "处理事件日志失败")
            } finally {
                if (BuildConfig.DEBUG) eventConnection.release()
            }
            txHash
        }

    @Throws(Exception::class)
    private fun getEventFilter(ev: EventDefinition): EthFilter? {
        val chainId: Long = ev.getEventChainId()
        val address: String = ev.getEventContractAddress()

        val originToken = tokensService.getToken(chainId, address) ?: return null

        // TODO: Handle non origin token events

        return EventUtils.generateLogFilter(ev, originToken, this)
    }

    private fun processLogs(
        ev: EventDefinition,
        logs: List<LogResult<*>>?,
        walletAddress: String,
    ): String {
        if (logs == null || logs.isEmpty()) return "" // early return

        val chainId: Long =
            ev.contract.addresses.keys
                .iterator()
                .next()
        val web3j: Web3j = TokenRepository.getWeb3jService(chainId)

        var firstTxHash = ""

        val index = logs.size - 1

        for (i in index downTo 0) {
            val ethLog: LogResult<*> = logs[i]
            val txHash = (ethLog.get() as Log).transactionHash
            if (TextUtils.isEmpty(firstTxHash)) firstTxHash = txHash
            val selectVal: String = EventUtils.getSelectVal(ev, ethLog)
            val blockNumber = (ethLog.get() as Log).blockNumber

            if (blockNumber.compareTo(ev.readBlock) > 0) {
                // Should store the latest event value
                storeLatestEventBlockTime(walletAddress, ev, blockNumber)
            }

            if (ev.parentAttribute != null) {
                storeEventValue(walletAddress, ev, ethLog, ev.parentAttribute, selectVal)
            } else {
                val txBlock: EthBlock =
                    EventUtils.getBlockDetails((ethLog.get() as Log).blockHash, web3j).blockingGet() // TODO: 待 EventUtils 支持协程后移除
                val blockTime: Long = txBlock.getBlock().getTimestamp().toLong()

                storeActivityValue(walletAddress, ev, ethLog, blockTime, ev.activityName)

                TransactionsService.addTransactionHashFetch(txHash, chainId, walletAddress)
            }
        }

        return firstTxHash
    }

    private fun storeLatestEventBlockTime(
        walletAddress: String,
        ev: EventDefinition,
        readBlock: BigInteger,
    ) {
        ev.readBlock = readBlock.add(BigInteger.ONE)
        try {
            realmManager.getRealmInstance(walletAddress).use { realm ->
                val chainId: Long = ev.getEventChainId()
                val eventAddress: String = ev.getEventContractAddress()
                val eventName: String =
                    if (ev.activityName != null) ev.activityName else ev.attributeName
                val databaseKey: String = TokensRealmSource.eventBlockKey(chainId, eventAddress, ev.type.name, ev.filter)
                realm.executeTransactionAsync { r: Realm ->
                    var realmToken =
                        r
                            .where(
                                RealmAuxData::class.java,
                            ).equalTo("instanceKey", databaseKey)
                            .findFirst()
                    if (realmToken == null) {
                        realmToken =
                            r.createObject(
                                RealmAuxData::class.java,
                                databaseKey,
                            )
                    }
                    realmToken!!.resultTime = System.currentTimeMillis()
                    realmToken.result = ev.readBlock.toString(16)
                    realmToken.functionId = eventName
                    realmToken.chainId = chainId
                    realmToken.tokenAddress = ""
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun storeActivityValue(
        walletAddress: String,
        ev: EventDefinition,
        log: LogResult<*>,
        blockTime: Long,
        activityName: String,
    ) {
        val tokenId: BigInteger = EventUtils.getTokenId(ev, log)
        // split out all the event data
        val valueList: String = EventUtils.getAllTopics(ev, log)

        val selectVal: String = EventUtils.getSelectVal(ev, log)

        val eventContractAddress: ContractAddress =
            ContractAddress(
                ev.getEventChainId(),
                ev.getEventContractAddress(),
            )

        // store this data
        val txHash = (log.get() as Log).transactionHash
        val key: String = TokensRealmSource.eventActivityKey(txHash, ev.type.name)
        storeAuxData(
            walletAddress,
            key,
            tokenId,
            valueList,
            activityName,
            eventContractAddress,
            blockTime,
        ) // store the event itself
    }

    private fun storeAuxData(
        walletAddress: String,
        databaseKey: String,
        tokenId: BigInteger,
        eventData: String,
        activityName: String,
        cAddr: ContractAddress,
        blockTime: Long,
    ) {
        try {
            realmManager.getRealmInstance(walletAddress).use { realm ->
                realm.executeTransactionAsync { r: Realm ->
                    var realmToken =
                        r
                            .where(
                                RealmAuxData::class.java,
                            ).equalTo("instanceKey", databaseKey)
                            .findFirst()
                    if (realmToken == null) {
                        realmToken =
                            r.createObject(
                                RealmAuxData::class.java,
                                databaseKey,
                            )
                    }
                    realmToken!!.resultTime = blockTime
                    realmToken.result = eventData
                    realmToken.functionId = activityName
                    realmToken.chainId = cAddr.chainId
                    realmToken.setTokenId(tokenId.toString(16))
                    realmToken.tokenAddress = cAddr.address
                    realmToken.resultReceivedTime = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun storeEventValue(
        walletAddress: String,
        ev: EventDefinition,
        log: LogResult<*>,
        attr: Attribute,
        selectVal: String,
    ) {
        // store result
        val tokenId: BigInteger = EventUtils.getTokenId(ev, log)

        val eventContractAddress: ContractAddress =
            ContractAddress(
                ev.getEventChainId(),
                ev.getEventContractAddress(),
            )
        val txResult = getFunctionResult(eventContractAddress, attr, tokenId)
        txResult.result = attr.getSyntaxVal(selectVal)

        val blockNumber = (log.get() as Log).blockNumber.toLong()

        // Update the entry for the attribute if required
        if (txResult.resultTime == 0L || blockNumber >= txResult.resultTime) {
            txResult.resultTime = blockNumber
            storeAuxData(walletAddress, txResult)
        }
    }

    private fun allowableExtension(file: File): Boolean {
        val index = file.name.lastIndexOf(".")
        if (index >= 0) {
            val extension = file.name.substring(index + 1)
            when (extension) {
                "xml", "tsml" -> return true
                else -> {}
            }
        }

        return false
    }

    private fun getFileName(file: File): String? {
        val name = file.name
        val index = name.lastIndexOf(".")
        return if (index > 0) {
            name.substring(0, index)
        } else {
            null
        }
    }

    private fun isAddress(file: File): Boolean {
        val name = getFileName(file)
        return if (name != null) {
            Utils.isAddressValid(name)
        } else {
            false
        }
    }

    /**
     * This is used to retrieve the file from the secure area in order to check the date.
     * Note: it only finds files previously downloaded from the server
     *
     * @param contractAddress
     * @return
     */
    private fun getDownloadedXMLFile(contractAddress: String): File? {
        // if in secure area will simply be address + XML
        val filename = contractAddress + TS_EXTENSION
        val file = File(context.filesDir, filename)
        if (file.exists() && file.canRead()) {
            return file
        }

        val files = context.filesDir.listFiles()
        for (f in files!!) {
            if (f.name.equals(filename, ignoreCase = true)) return f
        }

        return null
    }

    private val scriptsInSecureZone: List<String?>
        get() {
            val checkScripts: MutableList<String?> = ArrayList()
            val files = context.filesDir.listFiles()
            Observable
                .fromArray(*files)
                .filter { obj: File? -> obj!!.isFile }
                .filter { file: File -> this.allowableExtension(file) }
                .filter { obj: File? -> obj!!.canRead() }
                .filter { file: File -> this.isAddress(file) }
                .forEach { file: File -> checkScripts.add(getFileName(file)) }
                .isDisposed

            return checkScripts
        }

    private fun isInSecureZone(file: File): Boolean = file.path.contains(context.filesDir.path)

    private fun isInSecureZone(file: String): Boolean = file.contains(context.filesDir.path)

    /**
     * 缓存签名 - 如果发现未缓存的文件则添加缓存签名
     *
     * @param file TokenScript 文件
     * @param td TokenDefinition 对象
     * @return 处理后的文件
     */
    private suspend fun cacheSignature(
        file: File,
        td: TokenDefinition,
    ): File {
        return withContext(ioDispatcher) {
            if (file.name == TokenDefinition.UNCHANGED_SCRIPT) {
                return@withContext file
            }

            if (file.canRead()) {
                try {
                    val tsf = TokenScriptFile(context, file.absolutePath)
                    val hash = tsf.calcMD5()

                    // 从 realm 拉取数据
                    var sig = getCertificateFromRealm(hash)
                    if (sig?.keyName == null) {
                        // 获取签名并存储在 realm 中
                        sig = checkTokenScriptSignature(file, td, "")
                        tsf.determineSignatureType(sig)
                        storeCertificateData(hash, sig)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "缓存签名失败: ${file.name}")
                }
            }
            file
        }
    }

    /**
     * 检查TokenScript文件的签名
     *
     * @param file TokenScript文件
     * @param td TokenDefinition对象
     * @param scriptUri 脚本URI
     * @return XMLDsigDescriptor 签名描述对象
     */
    private suspend fun checkTokenScriptSignature(
        file: File,
        td: TokenDefinition,
        scriptUri: String,
    ): XMLDsigDescriptor {
        var scriptUriLocal: String = scriptUri
        val info: ContractInfo? = td.contracts[td.holdingToken]

        // 如果合约信息为空，返回无签名的描述对象
        if (info == null) {
            val descriptor = XMLDsigDescriptor()
            descriptor.type = SigReturnType.NO_SIGNATURE
            descriptor.result = "fail"
            descriptor.subject = "Invalid contract info"
            return descriptor
        }

        try {
            // 根据scriptUri是否为空选择不同的签名验证方式
            return if (TextUtils.isEmpty(scriptUriLocal)) {
                val tsf = TokenScriptFile(context, file.absolutePath)
                if (tsf.inputStream == null) {
                    throw IOException("无法读取TokenScript文件: ${file.absolutePath}")
                }

                scriptUriLocal = getScriptUrl(info.getfirstChainId(), info.firstAddress)
                alphaWalletService.checkTokenScriptSignature(
                    tsf.getInputStreamSafe(),
                    info.getfirstChainId(),
                    info.firstAddress,
                    scriptUriLocal,
                )
            } else {
                // 使用scriptUri进行签名验证
                alphaWalletService.checkTokenScriptSignature(
                    scriptUriLocal,
                    info.getfirstChainId(),
                    info.firstAddress,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "验证TokenScript签名时发生错误")
            // 创建错误签名描述对象
            val descriptor = XMLDsigDescriptor()
            descriptor.type = SigReturnType.SIGNATURE_INVALID
            descriptor.result = "fail"
            descriptor.subject = "Error: ${e.message}"
            return descriptor
        }
    }

    private fun getCertificateFromRealm(hash: String): XMLDsigDescriptor? {
        var sig: XMLDsigDescriptor? = null
        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                val realmCert =
                    realm
                        .where(
                            RealmCertificateData::class.java,
                        ).equalTo("instanceKey", hash)
                        .findFirst()
                if (realmCert != null) {
                    sig = realmCert.dsigObject
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return sig
    }

    private fun IPFSSigDescriptor(): XMLDsigDescriptor = XMLDsigDescriptor(EIP5169_ISSUER)

    /**
     * 检查文件差异
     * 比较新下载的脚本内容与本地文件的MD5哈希值，判断是否需要更新
     *
     * @param address 合约地址
     * @param result 下载结果，包含脚本内容和IPFS标志
     * @return true表示文件有差异需要更新，false表示无需更新
     */
    private fun checkFileDiff(
        address: String,
        result: Pair<String?, Boolean>,
    ): Boolean {
        // 安全获取脚本内容，避免智能转换问题
        val scriptContent = result.first
        if (scriptContent == null || scriptContent.length < 10) {
            return false
        }

        // 计算新脚本的MD5哈希值
        val newMD5Hash =
            TokenScriptFile.calcMD5(
                ByteArrayInputStream(
                    scriptContent.toByteArray(StandardCharsets.UTF_8),
                ),
            )
        val tsf = TokenScriptFile(context, defineDownloadTSFile(address).absolutePath)

        return if (tsf.exists()) {
            tsf.calcMD5() != newMD5Hash
        } else {
            true
        }
    }

    /**
     * Use internal directory to store contracts fetched from the server
     *
     * @param address
     * @param result
     * @return
     * @throws
     */

    /**
     * 存储从服务器下载的TokenScript文件到本地
     *
     * @param address 合约地址
     * @param result 包含文件内容和状态的Pair对象
     * @return 存储的文件对象
     * @throws IOException 文件操作异常
     */
    @Throws(IOException::class)
    private fun storeFile(
        address: String,
        result: Pair<String?, Boolean>,
    ): File {
        // 获取文件内容字符串，避免智能转换问题
        val fileContent = result.first

        // 检查文件内容是否为空或过短（小于10个字符）
        if (fileContent == null || fileContent.length < 10) {
            // 返回空文件对象表示无效内容
            return File("")
        }

        val file = defineDownloadTSFile(address)
        val fos = FileOutputStream(file)
        val os: OutputStream = BufferedOutputStream(fos)
        // 安全检查：确保result和fileContent都不为null
        if (result != null && fileContent != null) {
            os.write(fileContent.toByteArray())
        }

        fos.flush()
        os.close()
        fos.close()

        // handle signature for IPFS
        if (result.second) {
            val tsf = TokenScriptFile(context, file.absolutePath)
            val hash = tsf.calcMD5()
            storeCertificateData(hash, IPFSSigDescriptor())
        }

        return file
    }

    private fun defineDownloadTSFile(address: String): File {
        val fName = address + TS_EXTENSION
        // Store received files in the internal storage area - no need to ask for permissions
        return File(context.filesDir, fName)
    }

    fun hasDefinition(token: Token): Boolean {
        var hasDefinition = false
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData =
                realm
                    .where(
                        RealmTokenScriptData::class.java,
                    ).equalTo("instanceKey", token.getTSKey())
                    .findFirst()
            hasDefinition = tsData != null
        }
        return hasDefinition
    }

    fun hasTokenView(
        token: Token,
        type: String?,
    ): Boolean {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", token.getTSKey())
                    .findFirst()
            return (tsData != null && tsData.getViewList().isNotEmpty())
        }
    }

    fun getTokenViewLocalAttributes(token: Token?): List<Attribute> {
        val td: TokenDefinition? = getAssetDefinition(token)
        val results: MutableList<Attribute> = ArrayList()
        if (td != null) {
            val attrMap: Map<String, Attribute> = td.tokenViewLocalAttributes
            results.addAll(attrMap.values)
        }

        return results
    }

    fun getTokenFunctionMap(token: Token): Map<String, TSAction>? {
        if (token.getInterfaceSpec() == ContractType.ATTESTATION) {
            return getAttestationFunctionMap(token)
        }

        val td: TokenDefinition? = getAssetDefinition(token)
        return td?.getActions()
    }

    fun getLocalAttributes(
        td: TokenDefinition?,
        availableActions: Map<BigInteger, List<String>>,
    ): List<Attribute> {
        val attrs: MutableList<Attribute> = ArrayList()
        if (td != null) {
            for ((_, value) in availableActions) {
                attrs.addAll(getLocalAttributes(td, value))
            }
        }

        return attrs
    }

    private fun getLocalAttributes(
        td: TokenDefinition,
        actions: List<String>,
    ): List<Attribute> {
        val attrs: MutableList<Attribute> = ArrayList()
        for ((key, value) in td.getActions().entries) {
            val attributes = value.attributes
            if (!actions.contains(key) || attributes == null) {
                continue
            }
            attrs.addAll(attributes.values)
        }

        return attrs
    }

    /**
     * 构建所有可用 tokenId 到该 tokenId 可用功能列表的映射
     *
     * @param token 令牌对象
     * @param tokenIds tokenId 列表
     * @param type 合约类型
     * @param update 更新类型
     * @return tokenId 到允许功能列表的映射 - 注意如果有拒绝消息，我们允许显示该功能
     */
    private suspend fun fetchFunctionMapAsync(
        token: Token,
        tokenIds: List<BigInteger>,
        type: ContractType,
        update: UpdateType?,
    ): Map<BigInteger, List<String>> =
        withContext(ioDispatcher) {
            try {
                val modifiers: List<ActionModifier> = getAllowedTypes(type)
                val validActions: MutableMap<BigInteger, MutableList<String>> = HashMap()
                val td: TokenDefinition? = getAssetDefinition(token)

                if (td != null) {
                    val actions: Map<String, TSAction> = td.getActions()
                    // 首先收集所有需要的属性 - 这样如果多个动作对同一个 tokenId 使用相同属性，我们就不会重复获取值
                    val requiredAttrNames = getRequiredAttributeNames(actions, td)
                    val attrResults: Map<BigInteger, MutableMap<String, TokenScriptResult.Attribute?>> =
                        getRequiredAttributeResults(requiredAttrNames, tokenIds, td, token, update) // 所有必需属性值与所有 tokenId 的映射

                    // 处理每个 tokenId 的动作
                    for (tokenId in tokenIds) {
                        for (actionName in actions.keys) {
                            val action: TSAction? = actions[actionName]
                            if (action == null || !modifiers.contains(action.modifier)) {
                                continue // 如果这不是证明获取，则不包括证明
                            }

                            val exclude = action.exclude
                            val selection: TSSelection? =
                                if (exclude != null) td.getSelection(exclude) else null

                            if (selection == null) {
                                if (!validActions.containsKey(tokenId)) {
                                    validActions[tokenId] = ArrayList()
                                }
                                validActions[tokenId]!!.add(actionName)
                            } else {
                                // 获取此 tokenId 和选择所需的属性结果
                                val requiredAttributeNames: List<String> = selection.getRequiredAttrs()
                                val idAttrResults: MutableMap<String, TokenScriptResult.Attribute?> =
                                    getAttributeResultsForTokenIds(td, attrResults, requiredAttributeNames, tokenId)

                                // 添加内在属性，如 ownerAddress、tokenId、contractAddress
                                addIntrinsicAttributes(idAttrResults, token, tokenId)

                                // 现在评估选择
                                val exclude: Boolean = EvaluateSelection.evaluate(selection.head, idAttrResults)
                                val denialMessage = selection.denialMessage
                                if (!exclude || denialMessage != null) {
                                    if (!validActions.containsKey(tokenId)) {
                                        validActions[tokenId] = ArrayList()
                                    }
                                    validActions[tokenId]!!.add(actionName)
                                }
                            }
                        }
                    }
                }
                validActions
            } catch (e: Exception) {
                Timber.e(e, "获取功能映射失败: ${token.getName()}")
                emptyMap()
            }
        }

    /**
     * 获取函数映射（协程版本）
     *
     * 获取代币的函数映射，支持异步操作。
     * 这是协程版本的实现，替代了原来的RxJava版本。
     *
     * @param token 代币对象
     * @param tokenIds 代币ID列表
     * @param type 合约类型
     * @param update 更新类型
     * @return 函数映射结果
     */
    suspend fun fetchFunctionMap(
        token: Token,
        tokenIds: List<BigInteger>,
        type: ContractType,
        update: UpdateType?,
    ): Map<BigInteger, List<String>> = withContext(ioDispatcher) {
        fetchFunctionMapAsync(token, tokenIds, type, update)
    }

    private fun getAllowedTypes(type: ContractType): List<ActionModifier> {
        val modifiers: MutableList<ActionModifier> = ArrayList<ActionModifier>()
        when (type) {
            ContractType.ATTESTATION -> {
                modifiers.add(ActionModifier.ATTESTATION)
            }

            else -> {
                modifiers.add(ActionModifier.NONE)
                modifiers.add(ActionModifier.ACTIVITY)
            }
        }

        return modifiers
    }

    private fun addIntrinsicAttributes(
        attrs: MutableMap<String, TokenScriptResult.Attribute?>,
        token: Token,
        tokenId: BigInteger,
    ) {
        // add tokenId, ownerAddress & contractAddress
        attrs["tokenId"] =
            TokenScriptResult.Attribute("tokenId", "tokenId", tokenId, tokenId.toString(10))
        attrs["ownerAddress"] =
            TokenScriptResult.Attribute(
                "ownerAddress",
                "ownerAddress",
                BigInteger.ZERO,
                token.getWallet(),
            )
        attrs["contractAddress"] =
            TokenScriptResult.Attribute(
                "contractAddress",
                "contractAddress",
                BigInteger.ZERO,
                token.getAddress(),
            )
    }

    suspend fun checkFunctionDenied(
        token: Token,
        actionName: String?,
        tokenIds: MutableList<out BigInteger>?,
    ): String? =
        withContext(ioDispatcher) {
            var denialMessage: String? = null
            val td: TokenDefinition? = getAssetDefinition(token)
            if (td != null) {
                val tokenId =
                    if (tokenIds != null && !tokenIds.isEmpty()) tokenIds[0] else BigInteger.ZERO
                val action: TSAction? = td.tokenActions.get(actionName)
                val exclude = action?.exclude
                val selection: TSSelection? =
                    if (exclude != null) td.getSelection(exclude) else null
                if (selection != null) {
                    // gather list of attribute results
                    val requiredAttrs: List<String> = selection.getRequiredAttrs()
                    // resolve all these attrs
                    val attrs: MutableMap<String, TokenScriptResult.Attribute?> =
                        HashMap<String, TokenScriptResult.Attribute?>()
                    // get results
                    for (attrId in requiredAttrs) {
                        val attr: Attribute = td.attributes.get(attrId) ?: continue

                        val attrResult: TokenScriptResult.Attribute =
                            tokenscriptUtility.fetchAttrResult(token, attr, tokenId, td, this@AssetDefinitionService, ViewType.VIEW, UpdateType.ALWAYS_UPDATE)
                        attrs[attrId] = attrResult
                    }

                    addIntrinsicAttributes(attrs, token, tokenId)

                    val exclude: Boolean = EvaluateSelection.evaluate(selection.head, attrs)
                    if (exclude && !TextUtils.isEmpty(selection.denialMessage)) {
                        denialMessage = selection.denialMessage
                    }
                }
            }

            denialMessage
        }

    private fun getAttributeResultsForTokenIds(
        td: TokenDefinition,
        attrResults: Map<BigInteger, MutableMap<String, TokenScriptResult.Attribute?>>,
        requiredAttributeNames: List<String>,
        tokenId: BigInteger,
    ): MutableMap<String, TokenScriptResult.Attribute?> {
        val results: MutableMap<String, TokenScriptResult.Attribute?> =
            HashMap<String, TokenScriptResult.Attribute?>()

        for (attributeName in requiredAttributeNames) {
            val useTokenId: BigInteger = td.useZeroForTokenIdAgnostic(attributeName, tokenId)

            if (!attrResults.containsKey(useTokenId)) {
                continue
            }

            results[attributeName] = attrResults[useTokenId]!![attributeName]
        }

        return results
    }

    private suspend fun getRequiredAttributeResults(
        requiredAttrNames: List<String>,
        tokenIds: List<BigInteger>,
        td: TokenDefinition,
        token: Token,
        update: UpdateType?,
    ): Map<BigInteger, MutableMap<String, TokenScriptResult.Attribute?>> {
        val resultSet: MutableMap<BigInteger, MutableMap<String, TokenScriptResult.Attribute?>> =
            HashMap<BigInteger, MutableMap<String, TokenScriptResult.Attribute?>>()
        for (tokenId in tokenIds) {
            for (attrName in requiredAttrNames) {
                val attr: Attribute =
                    td.attributes.get(attrName) ?: continue
                val useTokenId: BigInteger = td.useZeroForTokenIdAgnostic(attrName, tokenId)
                val attrResult: TokenScriptResult.Attribute =
                    tokenscriptUtility.fetchAttrResult(token, attr, useTokenId, td, this, ViewType.VIEW, update)
                val tokenIdMap: MutableMap<String, TokenScriptResult.Attribute?> =
                    resultSet.computeIfAbsent(useTokenId) { k: BigInteger? -> HashMap<String, TokenScriptResult.Attribute?>() }
                tokenIdMap[attrName] = attrResult
            }
        }

        return resultSet
    }

    private fun getRequiredAttributeNames(
        actions: Map<String, TSAction>,
        td: TokenDefinition,
    ): List<String> {
        val requiredAttrs: MutableList<String> = ArrayList()
        for (actionName in actions.keys) {
            val action: TSAction? = actions[actionName]
            val exclude = action?.exclude
            val selection: TSSelection? =
                if (exclude != null) td.getSelection(exclude) else null
            if (selection != null) {
                val attrNames: List<String> = selection.getRequiredAttrs()
                for (attrName in attrNames) {
                    if (!requiredAttrs.contains(attrName)) requiredAttrs.add(attrName)
                }
            }
        }

        return requiredAttrs
    }

    override fun parseMessage(parseResult: ParseResult.ParseResultId?) {
        when (parseResult) {
            ParseResult.ParseResultId.PARSER_OUT_OF_DATE -> HomeActivity.setUpdatePrompt()
            ParseResult.ParseResultId.XML_OUT_OF_DATE -> {}
            ParseResult.ParseResultId.OK -> {}
            else -> {}
        }
    }

    /**
     * 通知新脚本已加载
     *
     * @param newTsFileName 新的 TokenScript 文件名
     */
    fun notifyNewScriptLoaded(newTsFileName: String) {
        val newTsFile = File(newTsFileName)
        CoroutineUtils.launchSafely(
            scope = serviceScope,
            dispatcher = ioDispatcher,
            onError = { throwable -> onScriptError(throwable, "新脚本加载") },
        ) {
            try {
                val td = handleNewTSFile(newTsFile)
                withContext(mainDispatcher) {
                    notifyNewScript(td, newTsFile)
                }
            } catch (e: Exception) {
                Timber.e(e, "处理新脚本失败: $newTsFileName")
                onScriptError(e)
            }
        }
    }

    /**
     * 统一的脚本错误处理
     *
     * @param throwable 异常对象
     * @param context 错误上下文描述
     */
    private fun onScriptError(
        throwable: Throwable,
        context: String = "Unknown",
    ) {
        Timber.e(throwable, "TokenScript 错误 [$context]: ${throwable.message}")
        // 根据错误类型进行不同处理
        when (throwable) {
            is SAXException -> {
                Timber.w("XML 解析错误: ${throwable.message}")
            }
            is FileNotFoundException -> {
                Timber.w("文件未找到: ${throwable.message}")
            }
            is SecurityException -> {
                Timber.w("权限错误: ${throwable.message}")
            }
            is OutOfMemoryError -> {
                Timber.e("内存不足，建议重启应用")
                // 清理缓存以释放内存
                cachedDefinition = null
                System.gc()
            }
            else -> {
                Timber.e("未知错误类型: ${throwable.javaClass.simpleName}")
            }
        }
    }

    private fun notifyNewScript(
        tokenDefinition: TokenDefinition,
        file: File,
    ) {
        if (!TextUtils.isEmpty(tokenDefinition.holdingToken)) {
            notificationService.DisplayNotification(
                "Definition Updated",
                file.name,
                NotificationCompat.PRIORITY_MAX,
            )
            val originContracts = getOriginContracts(tokenDefinition)
            for (cl in originContracts) {
                tokensService.addUnknownTokenToCheck(ContractAddress(cl.chainId, cl.address))
            }
        }
    }

    /**
     * 获取令牌的签名数据
     *
     * @param token 令牌对象
     * @return 签名描述符
     */
    suspend fun getSignatureDataAsync(token: Token): XMLDsigDescriptor {
        return withContext(ioDispatcher) {
            if (token == null) return@withContext XMLDsigDescriptor()
            val tsf = getTokenScriptFile(token)
            getSignatureDataAsync(tsf, token.tokenInfo.chainId, token.tokenInfo.address)
        }
    }

    /**
     * 获取指定链和合约地址的签名数据
     *
     * @param chainId 链 ID
     * @param contractAddress 合约地址
     * @return 签名描述符
     */
    suspend fun getSignatureDataAsync(
        chainId: Long,
        contractAddress: String,
    ): XMLDsigDescriptor? =
        withContext(ioDispatcher) {
            val tsf = getTokenScriptFile(chainId, contractAddress)
            getSignatureDataAsync(tsf, chainId, contractAddress)
        }

    /**
     * 获取 TokenScript 文件的签名数据（私有方法）
     *
     * @param tsf TokenScript 文件
     * @param chainId 链 ID
     * @param contractAddress 合约地址
     * @return 签名描述符
     */
    private suspend fun getSignatureDataAsync(
        tsf: TokenScriptFile?,
        chainId: Long,
        contractAddress: String?,
    ): XMLDsigDescriptor =
        withContext(ioDispatcher) {
            try {
                var sigDescriptor: XMLDsigDescriptor? =
                    XMLDsigDescriptor()
                sigDescriptor!!.result = "fail"
                sigDescriptor.type = SigReturnType.NO_TOKENSCRIPT

                if (tsf != null && tsf.exists()) {
                    val hash = tsf.calcMD5()
                    var sig = getCertificateFromRealm(hash)
                    if (sig == null || (
                            sig.result != null &&
                                sig.result.equals(
                                    "fail",
                                    ignoreCase = true,
                                )
                        ) || sig.type == SigReturnType.NO_TOKENSCRIPT
                    ) {
                        val scriptUrl = getScriptUrl(chainId, contractAddress)
                        sig =
                            alphaWalletService.checkTokenScriptSignature(
                                tsf.getInputStreamSafe(),
                                chainId,
                                contractAddress,
                                scriptUrl,
                            )
                        tsf.determineSignatureType(sig)
                        storeCertificateData(hash, sig)
                    }
                    sigDescriptor = sig
                }
                sigDescriptor
            } catch (e: Exception) {
                Timber.e(e, "获取签名数据失败: chainId=$chainId, address=$contractAddress")
                XMLDsigDescriptor().apply {
                    result = "fail"
                    type = SigReturnType.NO_TOKENSCRIPT
                }
            }
        }

    /**
     * 获取签名数据（协程版本）
     *
     * 获取代币的签名数据，支持异步操作。
     * 这是协程版本的实现，替代了原来的RxJava版本。
     *
     * @param token 代币对象
     * @return 签名描述对象
     */
    suspend fun getSignatureData(token: Token): XMLDsigDescriptor = withContext(ioDispatcher) {
        getSignatureDataAsync(token)
    }

    /**
     * 获取签名数据（协程版本）
     *
     * 根据链ID和合约地址获取签名数据，支持异步操作。
     * 这是协程版本的实现，替代了原来的RxJava版本。
     *
     * @param chainId 链ID
     * @param contractAddress 合约地址
     * @return 签名描述对象
     */
    suspend fun getSignatureData(
        chainId: Long,
        contractAddress: String,
    ): XMLDsigDescriptor? = withContext(ioDispatcher) {
        getSignatureDataAsync(chainId, contractAddress)
    }

    private fun getScriptUrl(
        chainId: Long,
        contractAddress: String?,
    ): String {
        val entryKey = getTSDataKey(chainId, contractAddress)
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val entry =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", entryKey)
                    .findFirst()
            if (entry != null) {
                entry.ipfsPath?.let {
                    return it
                }
            }
        }
        return ""
    }

    // Database functions
    private fun functionKey(
        cAddr: ContractAddress,
        tokenId: BigInteger,
        attrId: String?,
    ): String {
        // produce a unique key for this. token address, token Id, chainId
        return cAddr.address + "-" + tokenId.toString(Character.MAX_RADIX) + "-" + cAddr.chainId + "-" + attrId + "-func-key"
    }

    override fun getFunctionResult(
        contract: ContractAddress,
        attr: Attribute,
        tokenId: BigInteger,
    ): TransactionResult {
        val tResult = TransactionResult(contract.chainId, contract.address, tokenId, attr)

        val dataBaseKey = functionKey(contract, tokenId, attr.name)

        try {
            realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                val realmToken: RealmAuxData? =
                    realm
                        .where(RealmAuxData::class.java)
                        .equalTo("instanceKey", dataBaseKey)
                        .equalTo("chainId", contract.chainId)
                        .findFirst()

                if (realmToken != null) {
                    tResult.resultTime = realmToken.resultTime
                    tResult.result = realmToken.result
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return tResult
    }

    override fun storeAuxData(
        walletAddress: String,
        tResult: TransactionResult,
    ): TransactionResult {
        if (tokenWalletAddress == null || !Utils.isAddressValid(tokenWalletAddress)) return tResult
        if (tResult.result == null || tResult.resultTime < 0) return tResult
        try {
            realmManager.getRealmInstance(walletAddress).use { realm ->
                val cAddr: ContractAddress =
                    ContractAddress(tResult.contractChainId, tResult.contractAddress)
                val databaseKey = functionKey(cAddr, tResult.tokenId, tResult.attrId)
                realm.executeTransaction { r: Realm ->
                    val realmToken =
                        r
                            .where(RealmAuxData::class.java)
                            .equalTo("instanceKey", databaseKey)
                            .equalTo("chainId", tResult.contractChainId)
                            .findFirst()
                    if (realmToken == null) {
                        createAuxData(r, tResult, databaseKey)
                    } else if (tResult.result != null) {
                        realmToken.result = tResult.result
                        realmToken.resultTime = tResult.resultTime
                        realmToken.resultReceivedTime = System.currentTimeMillis()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return tResult
    }

    private fun updateEventBlockTimes() {
        try {
            realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                val realmEvents: RealmResults<RealmAuxData> =
                    realm
                        .where(
                            RealmAuxData::class.java,
                        ).endsWith("instanceKey", "-eventBlock")
                        .sort("resultTime", Sort.ASCENDING)
                        .findAll()
                for (eventData in realmEvents) {
                    updateEventList(eventData)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /**
     * For debugging & testing
     */
    @Keep
    private fun deleteAllEventData() {
        // delete all realm event/attribute result data
        try {
            realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                realm.executeTransactionAsync { r: Realm ->
                    val realmEvents: RealmResults<RealmAuxData> =
                        r
                            .where(
                                RealmAuxData::class.java,
                            ).findAll()
                    realmEvents.deleteAllFromRealm()
                }
            }
        } catch (e: Exception) {
            //
        }

        // Delete all tokenscript data
        try {
            realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
                realm.executeTransactionAsync { r: Realm ->
                    val rd: RealmResults<RealmTokenScriptData> =
                        r
                            .where(
                                RealmTokenScriptData::class.java,
                            ).findAll()
                    val realmCert: RealmResults<RealmCertificateData> =
                        r
                            .where(
                                RealmCertificateData::class.java,
                            ).findAll()

                    rd.deleteAllFromRealm()
                    realmCert.deleteAllFromRealm()
                }
            }
        } catch (e: Exception) {
            //
        }
    }

    private fun updateEventList(eventData: RealmAuxData) {
        eventData.instanceKey?.let { it ->
            val contractDetails =
                     it
                    .split("-".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            if (contractDetails.size != 5) return
            val eventAddress = contractDetails[0]
            val chainId = contractDetails[1].toLong()
            val eventId = eventData.functionId

            val eventKey: String = EventDefinition.getEventKey(chainId, eventAddress, eventId, null)
            val ev: EventDefinition? = eventList[eventKey]
            if (ev != null) {
                ev.readBlock =
                    BigInteger(
                        eventData.result,
                        16,
                    ).add(BigInteger.ONE) // add one so we don't pick up the same event again
            }
        }


    }

    private fun createAuxData(
        realm: Realm,
        tResult: TransactionResult,
        dataBaseKey: String,
    ) {
        try {
            val realmData =
                realm.createObject(
                    RealmAuxData::class.java,
                    dataBaseKey,
                )
            realmData.resultTime = tResult.resultTime
            realmData.result = tResult.result
            realmData.chainId = tResult.contractChainId
            realmData.functionId = tResult.method
            realmData.setTokenId(tResult.tokenId.toString(Character.MAX_RADIX))
            realmData.resultReceivedTime = System.currentTimeMillis()
        } catch (e: RealmPrimaryKeyConstraintException) {
            // in theory we should never see this
            Timber.e(e)
        }
    }

    // private Token

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun addOpenSeaAttributes(
        attrs: StringBuilder,
        token: Token,
        tokenId: BigInteger,
    ) {
        val tokenAsset = token.getAssetForToken(tokenId.toString()) ?: return

        // add all asset IDs
        if (tokenAsset.backgroundColor != null) {
            TokenScriptResult.addPair<String>(
                attrs,
                "background_colour",
                URLEncoder.encode(tokenAsset.backgroundColor, StandardCharsets.UTF_8),
            )
        }
        if (tokenAsset.thumbnail != null) {
            TokenScriptResult.addPair<String>(
                attrs,
                "image_preview_url",
                tokenAsset.thumbnail,
            )
        }
        if (tokenAsset.description != null) {
            TokenScriptResult.addPair<String>(
                attrs,
                "description",
                URLEncoder.encode(tokenAsset.description, StandardCharsets.UTF_8),
            )
        }
        if (tokenAsset.externalLink != null) {
            TokenScriptResult.addPair<String>(
                attrs,
                "external_link",
                tokenAsset.externalLink,
            )
        }
        // if (tokenAsset.getTraits() != null) TokenScriptResult.addPair(attrs, "traits", tokenAsset.getTraits());
        if (tokenAsset.name != null) {
            TokenScriptResult.addPair<String>(
                attrs,
                "metadata_name",
                tokenAsset.name,
            )
        }
    }

    fun getTokenAttrs(
        token: Token,
        tokenId: BigInteger,
        count: BigInteger,
    ): StringBuilder {
        val attrs = StringBuilder()

        val definition: TokenDefinition? = getAssetDefinition(token)
        var label = token.getTokenTitle()
        if (definition?.getTokenName(1) != null) {
            label = definition.getTokenName(1)
        }
        TokenScriptResult.addPair<String>(attrs, "name", token.tokenInfo.name)
        TokenScriptResult.addPair<String>(attrs, "label", label)
        TokenScriptResult.addPair<String>(attrs, "symbol", token.getSymbol())
        TokenScriptResult.addPair<String>(attrs, "_count", count.toString())
        TokenScriptResult.addPair<String>(
            attrs,
            "contractAddress",
            Keys.toChecksumAddress(token.tokenInfo.address),
        )
        TokenScriptResult.addPair<BigInteger>(
            attrs,
            "chainId",
            BigInteger.valueOf(token.tokenInfo.chainId),
        )
        TokenScriptResult.addPair<BigInteger>(attrs, "tokenId", tokenId)
        TokenScriptResult.addPair<String>(
            attrs,
            "ownerAddress",
            Keys.toChecksumAddress(token.getWallet()),
        )

        if (token.isNonFungible()) {
            // TODO
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addOpenSeaAttributes(attrs, token, tokenId)
            }
        }

        if (token.isEthereum()) {
            TokenScriptResult.addPair<String>(attrs, "balance", token.balance.toString())
        }

        if (token.getInterfaceSpec() == ContractType.ATTESTATION) {
            addAttestationAttributes(attrs, token as Attestation, definition)
        }

        return attrs
    }

    /**
     * Get all the magic values - eg native crypto balances for all chains
     *
     * @return
     */
    @Throws(Exception::class)
    fun getMagicValuesForInjection(chainId: Long): String {
        val walletBalance = "walletBalance"
        val prefix = "web3.eth"
        val sb = StringBuilder()
        sb.append("\n\n")
        tokenWalletAddress?.let {
            val nativeCurrency = tokensService.getToken(chainId, it)
            sb
                .append(prefix)
                .append(" = {\n")
                .append(walletBalance)
                .append(": ")
                .append(nativeCurrency?.balance.toString())
                .append("\n}\n")
        }

        val nativeCurrencies = tokensService.getAllAtAddress(tokenWalletAddress)
        for (currency in nativeCurrencies) {
            sb
                .append(prefix)
                .append("_")
                .append(currency.tokenInfo.chainId)
                .append(" = {\n")
                .append(walletBalance)
                .append(": ")
                .append(currency.balance.toString())
                .append("\n}\n")
        }

        sb.append("\n\n")

        return sb.toString()
    }

    fun clearResultMap() {
        tokenscriptUtility.clearParseMaps()
    }

    /**
     * 解析属性
     *
     * 将 Token 的属性解析为协程流
     *
     * @param token Token 对象
     * @param td Token 定义
     * @param tokenId Token ID
     * @param extraAttrs 额外属性列表
     * @param itemView 视图类型
     * @param update 更新类型
     * @return 属性结果流
     */
    fun resolveAttrs(
        token: Token,
        td: TokenDefinition?,
        tokenId: BigInteger,
        extraAttrs: List<Attribute>?,
        itemView: ViewType?,
        update: UpdateType?,
    ): Flow<TokenScriptResult.Attribute> =
        flow {
            val definition: TokenDefinition? = td ?: getAssetDefinition(token)
            val cAddr = ContractAddress(token.tokenInfo.chainId, token.tokenInfo.address)

            if (definition == null) {
                emit(
                    TokenScriptResult.Attribute(
                        "RAttrs",
                        "",
                        BigInteger.ZERO,
                        "",
                    ),
                )
                return@flow
            }

            definition.context = TokenscriptContext()
            definition.context?.cAddr = cAddr
            definition.context?.attrInterface = this@AssetDefinitionService

            val attrList: MutableList<Attribute> = ArrayList<Attribute>(definition.attributes.values)
            if (extraAttrs != null) attrList.addAll(extraAttrs)

            // 调用私有的 resolveAttrs 方法
            resolveAttrs(token, tokenId, definition, attrList, itemView, update).collect { result ->
                emit(result)
            }
        }.flowOn(Dispatchers.IO)

    fun getAttestationAttrs(
        token: Token,
        action: TSAction?,
        attnId: String?,
    ): List<TokenScriptResult.Attribute> {
        val attrs: MutableList<TokenScriptResult.Attribute> =
            ArrayList<TokenScriptResult.Attribute>()
        val att =
            if (action != null && action.modifier == ActionModifier.ATTESTATION) {
                attnId?.let {
                    tokensService.getAttestation(token.tokenInfo.chainId, token.getAddress(), it)
                } as Attestation
            } else {
                null
            }
        if (att != null) {
            if (att.isEAS()) {
                // can we rebuild the EasAttestation?
                val dfe: DefaultFunctionEncoder = DefaultFunctionEncoder()
                val easAttestation: EasAttestation = att.easAttestation ?: return attrs
                val inputParam = listOf<org.web3j.abi.datatypes.Type<*>>(easAttestation.getAttestationCore())
                val coreAttestationHex: String =
                    Numeric.prependHexPrefix(dfe.encodeParameters(inputParam).substring(0x40))
                attrs.add(
                    TokenScriptResult.Attribute(
                        "attestation",
                        "attestation",
                        BigInteger.ZERO,
                        coreAttestationHex,
                    ),
                )
                // also require signature
                val signatureBytes: String = Numeric.toHexString(easAttestation.getSignatureBytes())
                attrs.add(
                    TokenScriptResult.Attribute(
                        "attestationSig",
                        "attestationSig",
                        BigInteger.ZERO,
                        signatureBytes,
                    ),
                )
                // inject the raw attestation
                attrs.add(
                    TokenScriptResult.Attribute(
                        "attestationSig",
                        "rawAttestation",
                        BigInteger.ZERO,
                        att.getRawAttestation(),
                    ),
                )
            } else {
                attrs.add(
                    TokenScriptResult.Attribute(
                        "attestation",
                        "attestation",
                        BigInteger.ZERO,
                        Numeric.toHexString(att.getAttestation()),
                    ),
                )
            }
        }

        return attrs
    }

    private fun addAttestationAttributes(
        attrs: StringBuilder,
        att: Attestation,
        td: TokenDefinition?,
    ) {
        if (att.isEAS()) {
            val dfe: DefaultFunctionEncoder = DefaultFunctionEncoder()
            val easAttestation: EasAttestation = att.easAttestation ?: return
            val inputParam = listOf<org.web3j.abi.datatypes.Type<*>>(easAttestation.getAttestationCore())
            val coreAttestationHex: String =
                Numeric.prependHexPrefix(dfe.encodeParameters(inputParam).substring(0x40)) // 0x40?
            TokenScriptResult.addPair<String>(attrs, "attestation", coreAttestationHex)
            val signatureBytes: String = Numeric.toHexString(easAttestation.getSignatureBytes())
            TokenScriptResult.addPair<String>(attrs, "attestationSig", signatureBytes)
            TokenScriptResult.addPair<String>(attrs, "rawAttestation", att.getRawAttestation())

            // add all the required attributes
            attrs.append(att.addTokenScriptAttributes())
        } else {
            TokenScriptResult.addPair<String>(
                attrs,
                "attestation",
                Numeric.toHexString(att.getAttestation()),
            )
        }
    }

    fun getAttestationFunctionMap(att: Token?): Map<String, TSAction> {
        val td: TokenDefinition? = getAssetDefinition(att)
        val actions: MutableMap<String, TSAction> = HashMap<String, TSAction>()
        if (att != null && td != null) {
            // got attestation, fetch all functions related to attestation
            for ((key, value) in td.tokenActions.entries) {
                if (value.modifier == ActionModifier.ATTESTATION) {
                    actions[key] = value
                }
            }
        }

        return actions
    }

    /**
     * 更新钱包数据库中所有认证的集合ID指针
     *
     * 主要功能：
     * - 遍历当前钱包数据库中的所有认证记录
     * - 检查并更新需要修正的认证集合ID
     * - 确保认证数据与TokenScript定义保持一致
     *
     * 业务流程：
     * 1. 验证TokenDefinition是否包含认证信息
     * 2. 从TokenDefinition中提取认证的预哈希值
     * 3. 计算新的脚本集合ID（使用Keccak256哈希）
     * 4. 查询数据库中需要更新的认证记录
     * 5. 批量更新认证记录的集合ID
     *
     * @param td TokenDefinition对象，包含认证定义和相关配置
     * @return 返回更新的认证记录数量（字符串格式）
     *
     * 技术特点：
     * - 使用Realm数据库事务确保数据一致性
     * - 通过Keccak256哈希算法生成唯一的集合ID
     * - 自动资源管理，确保数据库连接正确关闭
     * - 异常处理确保操作安全性
     */
    fun updateAttestations(td: TokenDefinition): String {
        return try {
            // 1. 验证输入参数和认证信息，使用安全调用
            val attestation =
                td.attestation ?: run {
                    Timber.d("TokenDefinition 不包含认证信息")
                    return "0"
                }

            // 2. 计算脚本集合ID
            val preHash = attestation.getCollectionIdPreHash()
            val scriptCollectionId = Numeric.toHexString(Hash.keccak256(preHash))

            Timber.d("计算脚本集合ID: $scriptCollectionId")

            // 3. 获取当前钱包地址并验证
            val currentAddress = tokenWalletAddress
            if (currentAddress.isNullOrEmpty()) {
                Timber.w("当前钱包地址为空，无法更新认证")
                return "0"
            }

            val wallet = Wallet(currentAddress)

            // 4. 执行数据库更新操作
            val updateCount = performAttestationUpdate(wallet, td, scriptCollectionId)

            Timber.d("成功更新 $updateCount 条认证记录")
            updateCount.toString()
        } catch (e: Exception) {
            Timber.e(e, "更新认证集合ID时发生错误")
            "0"
        }
    }

    /**
     * 执行认证更新操作的核心方法
     *
     * @param wallet 钱包实例
     * @param td TokenDefinition对象
     * @param scriptCollectionId 目标脚本集合ID
     * @return 更新的记录数量
     */
    private fun performAttestationUpdate(
        wallet: Wallet,
        td: TokenDefinition,
        scriptCollectionId: String,
    ): Int {
        return realmManager.getRealmInstance(wallet).use { realm ->
            try {
                // 1. 查询需要更新的认证记录
                val recordsToUpdate = getRealmItemsForUpdate(realm, td, scriptCollectionId)

                if (recordsToUpdate.isEmpty()) {
                    Timber.d("没有需要更新的认证记录")
                    return@use 0
                }

                Timber.d("找到 ${recordsToUpdate.size} 条需要更新的认证记录")

                // 2. 执行批量更新事务
                realm.executeTransaction {
                    recordsToUpdate.forEach { attestationRecord ->
                        try {
                            attestationRecord.collectionId = scriptCollectionId
                            Timber.v("更新认证记录: ${attestationRecord.getAttestationID()}")
                        } catch (e: Exception) {
                            Timber.e(e, "更新单个认证记录时发生错误: ${attestationRecord.getAttestationID()}")
                        }
                    }
                }

                recordsToUpdate.size
            } catch (e: Exception) {
                Timber.e(e, "执行认证更新事务时发生错误")
                0
            }
        }
    }

    /**
     * 获取需要更新集合ID的Realm认证记录列表
     *
     * 主要功能：
     * - 查询数据库中集合ID与脚本集合ID不匹配的认证记录
     * - 验证认证记录是否确实需要更新
     * - 过滤出真正需要更新的认证记录
     *
     * 业务逻辑：
     * 1. 查询所有集合ID不等于目标脚本集合ID的认证记录
     * 2. 对每个记录，从TokensService获取对应的认证对象
     * 3. 验证认证对象计算出的集合ID是否与目标集合ID匹配
     * 4. 将匹配的记录添加到更新列表中
     *
     * @param realm Realm数据库实例
     * @param td TokenDefinition对象，用于验证认证集合ID
     * @param scriptCollectionId 目标脚本集合ID
     * @return 需要更新的RealmAttestation记录列表
     *
     * 注意事项：
     * - 双重验证机制确保数据准确性
     * - 避免不必要的数据库更新操作
     */
    private fun getRealmItemsForUpdate(
        realm: Realm,
        td: TokenDefinition,
        scriptCollectionId: String,
    ): List<RealmAttestation> {
        // 初始化需要更新的记录列表
        val forUpdate: MutableList<RealmAttestation> = ArrayList<RealmAttestation>()

        // 查询所有集合ID不匹配的认证记录
        val realmItems: RealmResults<RealmAttestation> =
            realm
                .where<RealmAttestation>(
                    RealmAttestation::class.java,
                ).notEqualTo("collectionId", scriptCollectionId)
                .findAll()

        // 遍历查询结果，验证每个记录是否真正需要更新
        for (rAtt in realmItems) {
            // 从TokensService获取对应的认证对象
            val attn =
                    tokensService.getAttestation(
                        rAtt.getChains().get(0), // 获取链ID
                        rAtt.getTokenAddress(), // 获取代币地址
                        rAtt.getAttestationID(), // 获取认证ID
                    )

            // 验证认证对象存在且其集合ID与目标集合ID匹配
            if (attn?.getAttestationCollectionId(td) == scriptCollectionId) {
                forUpdate.add(rAtt) // 添加到更新列表
            }
        }

        return forUpdate
    }

    /**
     * 解析属性列表
     *
     * 将属性列表转换为协程流，异步处理每个属性
     *
     * @param token Token 对象
     * @param tokenId Token ID
     * @param td Token 定义
     * @param attrList 属性列表
     * @param itemView 视图类型
     * @param update 更新类型
     * @return 属性结果流
     */
    private suspend fun resolveAttrs(
        token: Token,
        tokenId: BigInteger,
        td: TokenDefinition,
        attrList: List<Attribute>,
        itemView: ViewType?,
        update: UpdateType?,
    ): Flow<TokenScriptResult.Attribute> =
        withContext(ioDispatcher) {
            flow {
                tokenscriptUtility.buildAttrMap(attrList)

                // 并发处理所有属性
                val results =
                    attrList.map { attr ->
                        async {
                            tokenscriptUtility.fetchAttrResult(token, attr, tokenId, td, this@AssetDefinitionService, itemView, update)
                        }
                    }

                // 收集所有结果并发送到流中
                results.forEach { deferred ->
                    try {
                        val result = deferred.await()
                        emit(result)
                    } catch (e: Exception) {
                        Timber.e(e, "处理属性时发生错误")
                        // 可以选择发送错误结果或跳过
                    }
                }
            }
        }

    /**
     * 解析多个 TokenId 的属性
     *
     * 处理多个 TokenId 的属性解析
     *
     * @param token Token 对象
     * @param tokenIds Token ID 列表
     * @param extraAttrs 额外属性列表
     * @param update 更新类型
     * @return 属性结果流
     */
    fun resolveAttrs(
        token: Token,
        tokenIds: List<BigInteger>,
        extraAttrs: List<Attribute>?,
        update: UpdateType?,
    ): Flow<TokenScriptResult.Attribute> =
        flow {
            val definition: TokenDefinition =
                getAssetDefinition(token)
                    ?: run {
                        emit(TokenScriptResult.Attribute("", "", BigInteger.ZERO, ""))
                        return@flow
                    }

            // 预填充 tokenIds
            for (attrType in definition.attributes.values) {
                resolveTokenIds(attrType, tokenIds)
            }

            // TODO: 为多个 tokenIds 存储交易获取时间
            resolveAttrs(
                token,
                definition,
                tokenIds[0],
                extraAttrs,
                ViewType.VIEW,
                UpdateType.UPDATE_IF_REQUIRED,
            ).collect { result ->
                emit(result)
            }
        }.flowOn(Dispatchers.IO)

    private fun resolveTokenIds(
        attrType: Attribute,
        tokenIds: List<BigInteger>,
    ) {
        if (attrType.function == null) return

        val params: List<MethodArg> = attrType.function?.parameters ?: error("Missing chain")
        for (arg in params) {
            val index = arg.tokenIndex
            if (arg.isTokenId && index >= 0 && index < tokenIds.size) {
                arg.element.value = tokenIds[index].toString()
            }
        }
    }

    fun generateTransactionPayload(
        token: Token?,
        tokenId: BigInteger,
        def: FunctionDefinition?,
    ): String? {
        val td: TokenDefinition = getAssetDefinition(token) ?: return ""
        val function: org.web3j.abi.datatypes.Function =
            tokenscriptUtility.generateTransactionFunction(
                token,
                tokenId,
                td,
                def,
                this,
            )
        return if (function.inputParameters == null) {
            null
        } else {
            FunctionEncoder.encode(function)
        }
    }

    /**
     * Clear the currently cached definition. This forces the service to reload the definition so it's clean for the next usage.
     */
    fun clearCache() {
        cachedDefinition = null
    }

    fun getHoldingContract(importFileName: String): ContractLocator? {
        var cr: ContractLocator? = null

        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData =
                realm
                    .where(
                        RealmTokenScriptData::class.java,
                    ).contains("filePath", importFileName)
                    .findFirst()
            if (tsData != null) {
                cr = ContractLocator(tsData.getOriginTokenAddress(), tsData.getChainId())
            }
        }
        return cr
    }

    fun convertInputValue(
        attr: Attribute?,
        valueFromInput: String?,
    ): String = tokenscriptUtility.convertInputValue(attr, valueFromInput)

    fun resolveReference(
        token: Token,
        action: TSAction?,
        arg: TokenscriptElement,
        tokenId: BigInteger?,
    ): String? {
        val td: TokenDefinition? = getAssetDefinition(token)
        return tokenscriptUtility.resolveReference(token, arg, tokenId, td, this)
    }

    fun setErrorCallback(callback: FragmentMessenger?) {
        homeMessenger = callback
    }

    /**
     * 获取所有 TokenDefinition 列表
     *
     * 使用文件搜索方法而非预解析方法，这样可以捕获错误的 TokenScript 并报告错误
     *
     * @param refresh 是否刷新资产脚本
     * @return TokenScript 详情列表
     */
    suspend fun getAllTokenDefinitionsAsync(refresh: Boolean): List<TokenLocator> =
        withContext(ioDispatcher) {
            try {
                if (refresh) {
                    loadAssetScripts()
                }
                waitForAssets()

                val tokenLocators = mutableListOf<TokenLocator>()
                val fileList = buildFileList()
                Collections.reverse(fileList) // 管理器期望优先级顺序相反 - 最低优先级在前

                fileList
                    .asSequence()
                    .filter { file -> file.isFile }
                    .filter { file -> allowableExtension(file) }
                    .filter { file -> file.canRead() }
                    .forEach { file ->
                        try {
                            FileInputStream(file).use { input ->
                                val tokenDef: TokenDefinition = parseFile(input)
                                val origins: ContractInfo? = tokenDef.contracts[tokenDef.holdingToken]
                                if (origins != null && origins.addresses.isNotEmpty()) {
                                    val tsf = TokenScriptFile(context, file.absolutePath)
                                    tokenLocators.add(TokenLocator(tokenDef.getTokenName(1).toString(), origins, tsf))
                                }
                            }
                        } catch (e: SAXException) {
                            // 不是合法的 XML TokenScript 文件，忽略
                            Timber.w("忽略非法 XML 文件: ${file.name}")
                        } catch (e: Exception) {
                            // 捕获特定的 TokenScript 解析错误以报告 TokenScript 错误
                            val tsf = TokenScriptFile(context, file.absolutePath)
                            val contractInfo = ContractInfo("Contract Type", HashMap<Long, List<String>>())
                            val stackTrace = StringWriter()
                            e.printStackTrace(PrintWriter(stackTrace))

                            tokenLocators.add(
                                TokenLocator(
                                    file.name,
                                    contractInfo,
                                    tsf,
                                    true,
                                    stackTrace.toString(),
                                ),
                            )
                            Timber.e(e, "解析 TokenScript 文件失败: ${file.name}")
                        }
                    }
                tokenLocators
            } catch (e: Exception) {
                Timber.e(e, "获取所有 TokenDefinition 失败")
                emptyList()
            }
        }

    /**
     * 从服务器检查脚本
     *
     * 从 scriptURI 导入脚本
     *
     * @param token 令牌对象
     * @param updateFlag 更新标志
     * @return TokenDefinition 或 null
     */
    @Deprecated("Use checkServerForScriptAsync", ReplaceWith("checkServerForScriptAsync(token, updateFlag)"))
    fun checkServerForScript(
        token: Token?,
        updateFlag: MutableLiveData<Boolean>?,
    ): Single<TokenDefinition> =
        singleFrom { checkServerForScriptAsync(token, updateFlag) ?: TokenDefinition() }

    suspend fun checkServerForScriptAsync(
        token: Token?,
        updateFlag: MutableLiveData<Boolean>?,
    ): TokenDefinition? {
        return withContext(ioDispatcher) {
            try {
                if (token == null) return@withContext TokenDefinition()

                val tf = getTokenScriptFile(token)

                // 检查本地文件
                if ((tf != null && tf.exists()) && !isInSecureZone(tf)) {
                    try {
                        val td =
                            if (checkCachedDefinition(token.tokenInfo.chainId, token.getAddress()) != null) {
                                cachedDefinition
                            } else {
                                val parsed = parseFile(tf.getInputStreamSafe())
                                cachedDefinition = parsed
                                parsed
                            }
                        return@withContext td
                    } catch (ignored: Exception) {
                        // 如果调试脚本不工作，尝试服务器
                        Timber.w("本地脚本解析失败，尝试服务器: ${ignored.message}")
                    }
                }

                // 尝试合约 URI，然后服务器
                val contractFile = fetchTokenScriptFromContract(token, updateFlag)
                val serverFile =
                    tryServerIfRequired(
                        contractFile,
                        token.getAddress().lowercase(Locale.getDefault()),
                        token.tokenInfo.chainId,
                    )

                handleNewTSFile(serverFile ?: File(""))
            } catch (e: Exception) {
                Timber.e(e, "从服务器检查脚本失败: ${token?.getName()}")
                TokenDefinition()
            }
        }
    }

    fun storeTokenViewHeight(
        chainId: Long,
        address: String,
        listViewHeight: Int,
    ) {
        realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
            realm.executeTransactionAsync { r: Realm ->
                val tsf =
                    getTokenScriptFile(chainId, address)
                if (tsf == null || !tsf.exists()) return@executeTransactionAsync
                val hash = tsf.calcMD5()
                val databaseKey = tokenSizeDBKey(chainId, address)

                var realmToken =
                    r
                        .where(
                            RealmAuxData::class.java,
                        ).equalTo("instanceKey", databaseKey)
                        .equalTo("chainId", chainId)
                        .findFirst()

                if (realmToken == null) {
                    realmToken =
                        r.createObject(
                            RealmAuxData::class.java,
                            databaseKey,
                        )
                }
                realmToken!!.chainId = chainId
                realmToken.result = hash
                realmToken.resultTime = listViewHeight.toLong()
            }
        }
    }

    /**
     * 获取视图高度
     *
     * @param chainId 链 ID
     * @param address 合约地址
     * @return 视图高度
     */
    suspend fun fetchViewHeightAsync(
        chainId: Long,
        address: String,
    ): Int {
        return withContext(ioDispatcher) {
            try {
                realmManager.getRealmInstance(tokenWalletAddress).use { realm ->
                    // 确定哈希
                    val tsf = getTokenScriptFile(chainId, address)
                    if (tsf == null || !tsf.exists()) return@withContext 0
                    val hash = tsf.calcMD5()
                    val databaseKey = tokenSizeDBKey(chainId, address)

                    val realmToken =
                        realm
                            .where(RealmAuxData::class.java)
                            .equalTo("instanceKey", databaseKey)
                            .equalTo("chainId", chainId)
                            .findFirst()

                    if (realmToken == null) {
                        return@withContext 0
                    }
                    if (hash == realmToken.result) {
                        // 可以使用这个高度
                        return@withContext realmToken.resultTime.toInt()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "获取视图高度失败: chainId=$chainId, address=$address")
            }
            0
        }
    }

    private fun tokenSizeDBKey(
        chainId: Long,
        address: String,
    ): String = "szkey-" + chainId + "-" + address.lowercase(Locale.getDefault())

    val eventRealm: Realm
        get() = realmManager.getRealmInstance(tokenWalletAddress)

    // For testing only
    private fun deleteAWRealm() {
        realmManager.getRealmInstance(TokensRealmSource.IMAGES_DB).use { realm ->
            realm.executeTransactionAsync(
                Realm.Transaction { r: Realm ->
                    val instance: RealmResults<RealmAuxData>? =
                        r
                            .where(RealmAuxData::class.java)
                            .findAll()
                    if (instance != null) {
                        instance.deleteAllFromRealm()
                    }
                },
            )
        }
    }

    fun generateTransactionFunction(
        token: Token,
        tokenId: BigInteger,
        td: TokenDefinition,
        fd: FunctionDefinition,
    ): org.web3j.abi.datatypes.Function = tokenscriptUtility.generateTransactionFunction(token, tokenId, td, fd, this)

    fun callSmartContract(
        chainId: Long,
        contractAddress: String,
        function: org.web3j.abi.datatypes.Function,
    ): String? = tokenscriptUtility.callSmartContract(chainId, contractAddress, function)

    private fun <T : Any> singleFrom(block: suspend () -> T): Single<T> =
        Single.create { emitter ->
            val job = CoroutineUtils.launchSafely(serviceScope) {
                try {
                    emitter.onSuccess(block())
                } catch (throwable: Throwable) {
                    emitter.tryOnError(throwable)
                }
            }
            emitter.setCancellable { job.cancel() }
        }
}
