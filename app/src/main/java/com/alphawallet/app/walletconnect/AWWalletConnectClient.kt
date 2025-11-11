package com.alphawallet.app.walletconnect

// WalletConnect SDK 导入
import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LongSparseArray
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.alphawallet.app.App
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.walletconnect.WalletConnectSessionItem
import com.alphawallet.app.entity.walletconnect.WalletConnectV2SessionItem
import com.alphawallet.app.interact.WalletConnectInteract
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.KeyProvider
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.service.GasService
import com.alphawallet.app.ui.WalletConnectNotificationActivity
import com.alphawallet.app.ui.WalletConnectSessionActivity
import com.alphawallet.app.ui.WalletConnectV2Activity
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.walletconnect.util.WCMethodChecker
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.ActionSheetSignDialog
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType.SIGNATURE_GENERATED
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.cacao.signature.SignatureType
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.android.relay.NetworkClientTimeout
import com.walletconnect.web3.wallet.client.Wallet.Model
import com.walletconnect.web3.wallet.client.Wallet.Params
import com.walletconnect.web3.wallet.client.Web3Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.web3j.utils.Numeric
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * WalletConnect V2 客户端主控制器 - 现代化版本
 *
 * 负责管理 WalletConnect V2 协议的全生命周期操作，包括连接建立、会话管理、
 * 签名请求处理和通知服务。本类采用现代化的 Kotlin 协程和 API 设计，
 * 提供更稳定和易维护的 WalletConnect 实现。
 *
 * 主要功能：
 * - WalletConnect 连接的配对与会话建立
 * - 会话提案的批准与拒绝（支持协程）
 * - 签名请求的处理与响应
 * - 会话生命周期管理（断开连接、过期处理）
 * - 系统通知显示与管理
 * - 多链网络支持与验证
 *
 * 技术特点：
 * - 基于 Kotlin 协程的异步操作
 * - 使用现代化的 WalletConnect SDK
 * - 使用 Hilt 依赖注入
 * - 支持 Android 通知系统
 * - 完整的错误处理与日志记录
 *
 * 现代化说明：
 * - 采用 Kotlin 协程替代回调
 * - 保持相同的公共 API 接口
 * - 改进的错误处理和性能
 * - 更好的类型安全性
 *
 * @property context 应用上下文
 * @property walletConnectInteract WalletConnect 会话交互器
 * @property preferenceRepository 偏好设置仓库
 * @property gasService Gas 计算服务
 *
 * @since 2024
 * @author AlphaWallet Team
 */
class AWWalletConnectClient @Inject constructor(
    private val context: Context,
    private val walletConnectInteract: WalletConnectInteract,
    private val preferenceRepository: PreferenceRepositoryType,
    private val gasService: GasService
) : Web3Wallet.WalletDelegate {

    companion object {
        private val TAG = AWWalletConnectClient::class.java.name
        private const val ISS_DID_PREFIX = "did:pkh:"
        private const val WC_NOTIFICATION_ID = 25964950
        private const val CHANNEL_ID = "WalletConnectV2Service"
        private const val OPERATION_TIMEOUT_MS = 30000L // 30秒超时
        private const val RETRY_DELAY_MS = 1000L // 重试延迟1秒
        private const val NOTIFICATION_DELAY_MS = 500L // 通知延迟500ms

        // 静态会话提案引用，供其他组件访问
        @JvmStatic
        var sessionProposal: Model.SessionProposal? = null
    }

    // ==================== 成员变量 ====================
    
    // LiveData 会话状态管理
    private val sessionItemMutableLiveData = MutableLiveData(emptyList<WalletConnectSessionItem>())
    
    // 依赖注入的服务和仓库
    private val keyProvider: KeyProvider = KeyProviderFactory.get()
    
    // 会话请求处理器映射
    private val requestHandlers = LongSparseArray<WalletConnectV2SessionRequestHandler>()
    
    // UI 回调和状态
    private var actionSheetCallback: ActionSheetCallback? = null
    private var hasConnection = false
    private var application: Application? = null
//    private var isInit = false
    
    // WalletConnect 初始化状态
    private var isInit = false

    // ==================== 公共 API - 协程版本 ====================

    /**
     * 检查是否存在活跃的 WalletConnect 会话
     * @return 是否有活跃会话
     */
    fun hasWalletConnectSessions(): Boolean {
        return walletConnectInteract.getSessionsCount() > 0
    }

    /**
     * 配对 WalletConnect 连接（协程版本）
     * @param url WalletConnect URI
     * @return 配对结果，成功时返回 null，失败时返回错误信息
     */
    suspend fun pair(url: String): String = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val pairParams = Core.Params.Pair(url)
                    CoreClient.Pairing.pair(
                        pairParams,
                        { 
                            continuation.resume(null) // 配对成功
                        },
                        { error -> 
                            Timber.e(error.throwable, "配对失败")
                            continuation.resume(error.throwable.message) // 配对失败，返回错误信息
                        }
                    )
                }
            } ?: "配对操作超时"
        } catch (e: CancellationException) {
            Timber.d("配对操作被取消")
            "配对操作被取消"
        } catch (e: Exception) {
            Timber.e(e, "配对操作异常")
            e.message ?: "配对操作异常"
        }
    }

    /**
     * 批准会话提案（协程版本）
     * @param sessionProposal 会话提案
     * @param selectedAccounts 选中的账户列表
     * @return 批准结果，成功时返回 true
     */
    suspend fun approveSessionProposal(
        sessionProposal: Model.SessionProposal,
        selectedAccounts: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val proposerPublicKey = sessionProposal.proposerPublicKey
                    val namespaces = buildNamespaces(sessionProposal, selectedAccounts)
                    
                    if (namespaces.isEmpty()) {
                        continuation.resume(false)
                        return@suspendCancellableCoroutine
                    }
                    
                    val approveParams = Params.SessionApprove(
                        proposerPublicKey, 
                        namespaces, 
                        sessionProposal.relayProtocol
                    )
                    
                    Web3Wallet.approveSession(
                        approveParams,
                        { 
                            // 延迟更新通知状态
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateNotification(sessionProposal)
                                continuation.resume(true)
                            }, NOTIFICATION_DELAY_MS)
                        },
                        { error -> 
                            onSessionApproveError(error)
                            continuation.resume(false)
                        }
                    )
                }
            } ?: false // 超时返回失败
        } catch (e: CancellationException) {
            Timber.d("批准会话提案被取消")
            false
        } catch (e: Exception) {
            Timber.e(e, "批准会话提案异常")
            false
        }
    }

    /**
     * 拒绝会话提案（协程版本）
     * @param sessionProposal 会话提案
     * @return 拒绝结果，成功时返回 true
     */
    suspend fun rejectSessionProposal(sessionProposal: Model.SessionProposal): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        val rejectParams = Params.SessionReject(
                            sessionProposal.proposerPublicKey,
                            context.getString(R.string.message_reject_request)
                        )
                        
                        Web3Wallet.rejectSession(
                            rejectParams,
                            { 
                                continuation.resume(true)
                            },
                            { error -> 
                                Timber.e(error.throwable, "拒绝会话失败")
                                continuation.resume(false)
                            }
                        )
                    }
                } ?: false
            } catch (e: CancellationException) {
                Timber.d("拒绝会话提案被取消")
                false
            } catch (e: Exception) {
                Timber.e(e, "拒绝会话提案异常")
                false
            }
        }

    /**
     * 断开 WalletConnect 会话（协程版本）
     * @param sessionId 会话ID
     * @return 断开结果，成功时返回 true
     */
    suspend fun disconnectSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val disconnectParams = Params.SessionDisconnect(sessionId)
                    Web3Wallet.disconnectSession(
                        disconnectParams,
                        { continuation.resume(true) },
                        { error -> 
                            onDisconnectError(error)
                            continuation.resume(false)
                        }
                    )
                }
            } ?: false
        } catch (e: CancellationException) {
            Timber.d("断开会话被取消")
            false
        } catch (e: Exception) {
            Timber.e(e, "断开会话异常")
            false
        }
    }

    /**
     * 更新会话通知状态（非挂起函数，兼容 Java 调用）
     * @param sessionProposal 会话提案（可选）
     */
    fun updateNotification(sessionProposal: Model.SessionProposal?) {
        try {
            val sessions = walletConnectInteract.getSessions().toMutableList()

            // 如果有新的会话提案且当前无会话，添加临时会话项
            if (sessionProposal != null && sessions.isEmpty()) {
                sessions.add(WalletConnectV2SessionItem.from(sessionProposal))
            }

            updateService(sessions)
            // 使用postValue避免线程限制
            sessionItemMutableLiveData.postValue(sessions)
        } catch (e: Exception) {
            Timber.e(e, "更新通知状态异常")
        }
    }

    // ==================== 兼容性 API - 保留回调接口 ====================

    /**
     * 配对操作（兼容 Java 回调版本）
     * @param url WalletConnect URI
     * @param callback 配对结果回调
     */
//    fun pair(url: String, callback: Consumer<String>) {
//        val pairParams = Core.Params.Pair(url)
//        CoreClient.pairing.pair(
//            pairParams,
//            { callback.accept("") }, // 成功时返回空字符串
//            { error ->
//                Timber.e(error.throwable, "配对失败")
//                callback.accept(error.throwable.message ?: "配对失败")
//            }
//        )
//    }
//
    /**
     * 批准会话提案（回调版本）
     */
//    fun approve(sessionProposal: Model.SessionProposal, callback: WalletConnectV2Callback) {
//        Web3Wallet.approveSession(
//            Params.SessionApprove(sessionProposal.proposerPublicKey, buildSupportedNamespaces()),
//            { callback.onSessionProposalApproved() },
//            { error ->
//                Timber.e(error.throwable, "批准会话失败")
//                callback.onSessionProposalRejected()
//            }
//        )
//    }

    /**
     * 拒绝会话提案（回调版本）
     */
//    fun reject(sessionProposal: Model.SessionProposal, callback: WalletConnectV2Callback) {
//        Web3Wallet.rejectSession(
//            Params.SessionReject(sessionProposal.proposerPublicKey, "User rejected session"),
//            { callback.onSessionProposalRejected() },
//            { error ->
//                Timber.e(error.throwable, "拒绝会话失败")
//                callback.onSessionProposalRejected()
//            }
//        )
//    }

    /**
     * 断开会话连接（回调版本）
     */
//    fun disconnect(sessionId: String, callback: WalletConnectV2Callback) {
//        Web3Wallet.disconnectSession(
//            Params.SessionDisconnect(sessionId),
//            { callback.onSessionDisconnected() },
//            { error ->
//                Timber.e(error.throwable, "断开连接失败")
//                callback.onSessionDisconnected()
//            }
//        )
//    }

    // ==================== 会话请求处理 ====================

    /**
     * 批准会话请求
     * @param sessionRequest 会话请求
     * @param result 结果数据
     */
    fun approve(sessionRequest: Model.SessionRequest, result: String) {
        val jsonRpcResponse = Model.JsonRpcResponse.JsonRpcResult(sessionRequest.request.id, result)
        val response = Params.SessionRequestResponse(sessionRequest.topic, jsonRpcResponse)
        Web3Wallet.respondSessionRequest(response, { }, this::onSessionRequestApproveError)
     }

     /**
      * 拒绝会话请求
      * @param sessionRequest 会话请求
      */
     fun reject(sessionRequest: Model.SessionRequest) {
         reject(sessionRequest, context.getString(R.string.message_reject_request))
     }

     /**
      * 拒绝会话请求（带失败信息）
      * @param sessionRequest 会话请求
      * @param failMessage 失败消息
      */
     fun reject(sessionRequest: Model.SessionRequest, failMessage: String) {
         val jsonRpcResponse = Model.JsonRpcResponse.JsonRpcError(sessionRequest.request.id, 0, failMessage)
         val response = Params.SessionRequestResponse(sessionRequest.topic, jsonRpcResponse)
         Web3Wallet.respondSessionRequest(response, { }, this::onSessionRequestRejectError)
     }

    // ==================== 获取器方法 ====================

    /**
     * 获取会话状态 LiveData
     * @return 会话项列表的 LiveData
     */
    fun sessionItemMutableLiveData(): MutableLiveData<List<WalletConnectSessionItem>> {
        return sessionItemMutableLiveData
    }

    /**
     * 获取中继服务器地址
     * @return 中继服务器 URL
     */
    fun getRelayServer(): String {
        return String.format(
            "%s/?projectId=%s", 
            C.WALLET_CONNECT_REACT_APP_RELAY_URL, 
            keyProvider.getWalletConnectProjectId()
        )
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 验证链ID是否受支持
     * @param chains 链ID列表
     * @return 是否全部受支持
     */
    private fun validChainId(chains: List<String>): Boolean {
        return chains.all { chainId ->
            try {
                chainId.split(":")[1].toLong()
                true
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, 
                        String.format(context.getString(R.string.chain_not_support), chainId), 
                        Toast.LENGTH_SHORT
                    ).show()
                }
                false
            }
        }
    }

    /**
     * 根据话题获取会话
     * @param topic 会话话题
     * @return 会话对象或 null
     */
    private fun getSession(topic: String): Model.Session? {
        return try {
            Web3Wallet.getListOfActiveSessions().find { it.topic == topic }
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).e(e)
            null
        }
    }

    /**
     * 构建支持的命名空间映射（用于兼容回调版本）
     * @return 支持的命名空间映射
     */
    private fun buildSupportedNamespaces(): Map<String, Model.Namespace.Session> {
        return mapOf(
            "eip155" to Model.Namespace.Session(
                getSupportedChains(),
                emptyList(), // 空账户列表，将在批准时填充
                getSupportedMethods(),
                getSupportedEvents()
            )
        )
    }

    /**
     * 构建命名空间映射
     * @param sessionProposal 会话提案
     * @param selectedAccounts 选中账户
     * @return 命名空间映射
     */
    private fun buildNamespaces(
        sessionProposal: Model.SessionProposal, 
        selectedAccounts: List<String>
    ): Map<String, Model.Namespace.Session> {
        val supportedNamespaces = mapOf(
            "eip155" to Model.Namespace.Session(
                getSupportedChains(),
                toCAIP10(getSupportedChains(), selectedAccounts),
                getSupportedMethods(),
                getSupportedEvents()
            )
        )
        
        return try {
            Web3Wallet.generateApprovedNamespaces(sessionProposal, supportedNamespaces)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "生成批准的命名空间失败")
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            emptyMap()
        }
    }

    /**
     * 获取支持的方法列表
     */
    private fun getSupportedMethods(): List<String> {
        return listOf(
            "eth_sendTransaction", 
            "eth_signTransaction", 
            "eth_signTypedData", 
            "eth_signTypedData_v3", 
            "eth_signTypedData_v4", 
            "personal_sign", 
            "eth_sign", 
            "wallet_switchEthereumChain"
        )
    }

    /**
     * 获取支持的事件列表
     */
    private fun getSupportedEvents(): List<String> {
        return listOf("chainChanged", "accountsChanged")
    }

    /**
     * 获取支持的链列表
     */
    private fun getSupportedChains(): List<String> {
        return EthereumNetworkBase.getAllNetworks().map { chainId -> "eip155:$chainId" }
    }

    /**
     * 转换为 CAIP-10 格式
     * @param chains 链列表
     * @param selectedAccounts 选中账户
     * @return CAIP-10 格式的账户列表
     */
    private fun toCAIP10(chains: List<String>, selectedAccounts: List<String>): List<String> {
        return chains.flatMap { chain ->
            selectedAccounts.map { account -> formatCAIP10(chain, account) }
        }
    }

    /**
     * 格式化为 CAIP-10 标准
     * @param chain 链标识
     * @param account 账户地址
     * @return CAIP-10 格式字符串
     */
    private fun formatCAIP10(chain: String, account: String): String {
        return "$chain:$account"
    }

    /**
     * 更新服务状态
     * @param items 会话项列表
     */
    private fun updateService(items: List<WalletConnectSessionItem>) {
        try {
            if (items.isEmpty()) {
                removeNotification()
            } else {
                displayNotification()
            }
        } catch (e: Exception) {
            Timber.e(e, "无法更新服务状态")
        }
    }

    // ==================== 通知管理 ====================

    /**
     * 显示 WalletConnect 通知
     */
    fun displayNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        
        val notification = createNotification()
        val notificationManager = NotificationManagerCompat.from(context)
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
            == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(WC_NOTIFICATION_ID, notification)
        } else {
            // 请求通知权限
            val intent = Intent(C.REQUEST_NOTIFICATION_ACCESS)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }

    /**
     * 创建通知对象
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(context, WalletConnectNotificationActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 
            WC_NOTIFICATION_ID, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notify_wallet_connect_title))
            .setContentText(context.getString(R.string.notify_wallet_connect_content))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * 创建通知渠道（Android O+）
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = context.getString(R.string.notify_wallet_connect_title)
        val description = context.getString(R.string.notify_wallet_connect_content)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setDescription(description)
        }
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 移除通知
     */
    private fun removeNotification() {
        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
            == PackageManager.PERMISSION_GRANTED) {
            notificationManager.cancel(WC_NOTIFICATION_ID)
        }
    }

    // ==================== 初始化与生命周期 ====================

    /**
     * 初始化 WalletConnect 客户端
     * @param application 应用程序实例
     */
    fun init(application: Application) {
        if (keyProvider.getWalletConnectProjectId().isEmpty()) {
            Timber.w("无 WalletConnect 项目ID，跳过初始化")
            return
        }

        this.application = application
        try {
            val appMetaData = getAppMetaData(application)
            val relayServer = getRelayServer()
            
            val coreClient = CoreClient
            
            // 初始化 Core Client
            coreClient.initialize(
                appMetaData,
                relayServer,
                ConnectionType.AUTOMATIC,
                application,
                null,
                null,
                NetworkClientTimeout(30, TimeUnit.SECONDS),
                false
            ) { error ->
                Timber.w(error.throwable, "Core Client 初始化警告")
            }

            // 初始化 Web3Wallet
            Web3Wallet.initialize(
                Params.Init(coreClient),
                {
                    isInit = true
                    Timber.tag(TAG).i("WalletConnect V2 协议已初始化")
                },
                { error ->
                    Timber.tag(TAG).e(error.throwable, "WalletConnect V2 协议初始化失败")
                }
            )

            // 设置代理
            Web3Wallet.setWalletDelegate(this)
            // 确保在有活跃会话时显示通知
            updateNotification(null)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "初始化 WalletConnect 客户端失败")
        }
    }

    /**
     * 获取应用元数据
     * @param application 应用程序实例
     * @return 应用元数据
     */
    private fun getAppMetaData(application: Application): Core.Model.AppMetaData {
        val name = application.getString(R.string.app_name)
        val url = C.ALPHAWALLET_WEBSITE
        val icons = listOf(C.ALPHA_WALLET_LOGO_URL)
        val description = "The ultimate Web3 Wallet to power your tokens."
        val redirect = "kotlin-responder-wc:/request"
        
        return Core.Model.AppMetaData(
            name, description, url, icons, redirect, null, false, null
        )
    }

    /**
     * 关闭 WalletConnect 客户端
     */
    fun shutdown() {
        Timber.tag(TAG).i("shutdown")
    }

    // ==================== 设置器方法 ====================

    /**
     * 设置操作表回调
     * @param actionSheetCallback 回调接口
     */
    fun setCallback(actionSheetCallback: ActionSheetCallback) {
        this.actionSheetCallback = actionSheetCallback
    }

    // ==================== 签名处理 ====================

    /**
     * 签名完成处理
     * @param signatureFromKey 签名结果
     * @param signable 可签名对象
     */
    fun signComplete(signatureFromKey: SignatureFromKey, signable: Signable) {
        if (hasConnection) {
            onSign(signatureFromKey, getHandler(signable.callbackId))
        } else {
            // 延迟重试
            Handler(Looper.getMainLooper()).postDelayed({
                signComplete(signatureFromKey, signable)
            }, RETRY_DELAY_MS)
        }
    }

    /**
     * 签名失败处理
     * @param error 错误信息
     * @param signable 可签名对象
     */
    fun signFail(error: String, signable: Signable) {
        val requestHandler = getHandler(signable.callbackId)
        Timber.i("签名失败: %s", error)
        if (requestHandler != null) {
            reject(requestHandler.sessionRequest, error)
        }
    }

    /**
     * 对话框被关闭处理
     * @param callbackId 回调ID
     */
    fun dismissed(callbackId: Long) {
        val requestHandler = getHandler(callbackId)
        if (requestHandler != null) {
            reject(requestHandler.sessionRequest, application?.getString(R.string.message_reject_request) ?: "请求被拒绝")
        }
    }

    /**
     * 获取并移除请求处理器
     * @param callbackId 回调ID
     * @return 请求处理器或 null
     */
    private fun getHandler(callbackId: Long): WalletConnectV2SessionRequestHandler? {
        val handler = requestHandlers.get(callbackId)
        requestHandlers.remove(callbackId)
        return handler
    }

    /**
     * 处理签名结果
     * @param signatureFromKey 签名结果
     * @param requestHandler 请求处理器
     */
    private fun onSign(signatureFromKey: SignatureFromKey, requestHandler: WalletConnectV2SessionRequestHandler?) {
        if (requestHandler == null) return
        
        if (signatureFromKey.sigType == SIGNATURE_GENERATED) {
            val result = Numeric.toHexString(signatureFromKey.signature)
            approve(requestHandler.sessionRequest, result)
        } else {
            Timber.i("签名失败: %s", signatureFromKey.failMessage)
            reject(requestHandler.sessionRequest, signatureFromKey.failMessage)
        }
    }

    // ==================== 错误处理回调 ====================

    private fun onSessionApproveError(error: Model.Error): Unit {
        Timber.tag(TAG).e(error.throwable, "批准会话失败")
        Toast.makeText(context, error.throwable.localizedMessage, Toast.LENGTH_LONG).show()
    }

    private fun onSessionRejectError(error: Model.Error): Unit {
        Timber.tag(TAG).e(error.throwable, "拒绝会话失败")
    }

    private fun onDisconnectError(error: Model.Error): Unit {
        Timber.tag(TAG).e(error.throwable, "断开会话失败")
    }

    private fun onSessionRequestApproveError(error: Model.Error): Unit {
        Timber.tag(TAG).e(error.throwable, "批准会话请求失败")
    }

    private fun onSessionRequestRejectError(error: Model.Error): Unit {
        Timber.tag(TAG).e(error.throwable, "拒绝会话请求失败")
    }

    // ==================== Web3Wallet.WalletDelegate 实现 ====================

    override fun onError(error: Model.Error) {
        Timber.tag(TAG).e(error.throwable, "WalletConnect 错误")
    }

    override fun onSessionSettleResponse(settledSessionResponse: Model.SettledSessionResponse) {
        Timber.tag(TAG).i("onSessionSettleResponse: %s", settledSessionResponse.toString())
    }

    override fun onSessionUpdateResponse(sessionUpdateResponse: Model.SessionUpdateResponse) {
        Timber.tag(TAG).i("onSessionUpdateResponse")
    }

    override fun onConnectionStateChange(connectionState: Model.ConnectionState) {
        Timber.tag(TAG).i("onConnectionStateChange: ${connectionState.isAvailable}")
        hasConnection = connectionState.isAvailable
    }

    override fun onSessionDelete(deletedSession: Model.SessionDelete) {
        Timber.tag(TAG).i("onSessionDelete: ${deletedSession}")
        updateNotification(null)
    }

    override fun onAuthRequest(authRequest: Model.AuthRequest, verifyContext: Model.VerifyContext) {
        Timber.tag(TAG).i("onAuthRequest: ${authRequest.id}")
        showApprovalDialog(authRequest)
    }

    override fun onSessionProposal(sessionProposal: Model.SessionProposal, verifyContext: Model.VerifyContext) {
        Timber.tag(TAG).i("onSessionProposal: ${sessionProposal.proposerPublicKey}")
        val sessionItem = WalletConnectV2SessionItem.from(sessionProposal)
        if (!validChainId(sessionItem.chains)) {
            Timber.w("不支持的链ID: ${sessionItem.chains}")
            return
        }
        
        AWWalletConnectClient.sessionProposal = sessionProposal
        val intent = Intent(context, WalletConnectV2Activity::class.java).apply {
            putExtra("session", sessionItem)
            flags = FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun onSessionRequest(sessionRequest: Model.SessionRequest, verifyContext: Model.VerifyContext) {
        val method = sessionRequest.request.method
        Timber.tag(TAG).i("onSessionRequest: $method")
        
        // 特殊处理链切换请求
        if (method == "wallet_switchEthereumChain") {
            val response = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", sessionRequest.request.id)
                put("result", "null")
            }
            approve(sessionRequest, response.toString())
            return
        }

        // 检查方法支持
        val checkMethod = if (method.startsWith("eth_signTypedData")) {
            "eth_signTypedData"
        } else {
            method
        }

        if (!WCMethodChecker.includes(checkMethod)) {
            Timber.w("不支持的方法: $method")
            reject(sessionRequest)
            return
        }

        val settledSession = getSession(sessionRequest.topic) ?: run {
            Timber.w("未找到会话: ${sessionRequest.topic}")
            return
        }
        
        val topActivity = App.instance?.topActivity

        if (topActivity != null) {
            val handler = WalletConnectV2SessionRequestHandler(
                sessionRequest, settledSession, topActivity, this
            )
            actionSheetCallback?.let { handler.handle(method, it) }
            requestHandlers.append(sessionRequest.request.id, handler)
        } else {
            Timber.w("无法获取顶层活动")
            reject(sessionRequest, "无法获取顶层活动")
        }
    }

    override fun onProposalExpired(expiredProposal: Model.ExpiredProposal) {
        Timber.tag(TAG).i("onProposalExpired: ${expiredProposal}")
        // TODO: 移除仍在显示的弹窗
    }

    override fun onRequestExpired(expiredRequest: Model.ExpiredRequest) {
        Timber.tag(TAG).i("onRequestExpired: ${expiredRequest.id}")
        // TODO: 移除仍在显示的弹窗
    }

    override fun onSessionExtend(session: Model.Session) {
        Timber.tag(TAG).i("onSessionExtend: ${session.topic}")
        // 会话延期处理
    }

    // ==================== 私有UI处理方法 ====================

    /**
     * 显示认证批准对话框
     * @param authRequest 认证请求
     */
    private fun showApprovalDialog(authRequest: Model.AuthRequest) {
        val activeWallet = preferenceRepository.currentWalletAddress ?: run {
            Timber.w("无活跃钱包地址")
            return
        }
        
        val issuer = ISS_DID_PREFIX + formatCAIP10(authRequest.payloadParams.chainId, activeWallet)
        val message = Web3Wallet.formatMessage(Params.FormatMessage(authRequest.payloadParams, issuer))
        val origin = authRequest.payloadParams.domain

        Handler(Looper.getMainLooper()).post {
            if (message != null) {
                doShowApprovalDialog(activeWallet, message, authRequest.id, origin, issuer)
            }
        }
    }

    /**
     * 执行显示批准对话框
     * @param walletAddress 钱包地址
     * @param message 消息内容
     * @param requestId 请求ID
     * @param origin 来源（可选）
     * @param issuer 签发者
     */
    private fun doShowApprovalDialog(walletAddress: String, message: String, requestId: Long, origin: String?, issuer: String) {
        val ethereumMessage = EthereumMessage(message, origin ?: "", 0, SignMessageType.SIGN_MESSAGE)
        val topActivity = App.instance?.topActivity
        
        if (topActivity != null) {
            val actionSheet = ActionSheetSignDialog(topActivity, newActionSheetCallback(requestId, issuer), ethereumMessage)
            actionSheet.setSigningWallet(walletAddress)
            actionSheet.show()
        } else {
            Timber.w("无法获取顶层活动")
        }
    }

    /**
     * 创建新的操作表回调
     * @param requestId 请求ID
     * @param issuer 签发者
     * @return 操作表回调
     */
    private fun newActionSheetCallback(requestId: Long, issuer: String): ActionSheetCallback {
        return object : ActionSheetCallback {
            override fun getAuthorisation(callback: SignAuthenticationCallback) {}

            override fun sendTransaction(tx: Web3Transaction) {}

            override fun completeSendTransaction(tx: Web3Transaction, signature: SignatureFromKey) {}

            override fun getGasService(): GasService = gasService

            override fun dismissed(txHash: String, callbackId: Long, actionCompleted: Boolean) {
                if (actionCompleted) {
                    closeWalletConnectActivity()
                } else {
                    Web3Wallet.respondAuthRequest(
                        Params.AuthRequestResponse.Error(requestId, 0, "用户拒绝请求"),
                        { closeWalletConnectActivity(); Unit },
                        { closeWalletConnectActivity(); Unit }
                    )
                }
            }

            override fun notifyConfirm(mode: String?) {}

            override fun gasSelectLauncher(): ActivityResultLauncher<Intent>? = null

            override fun getWalletType(): WalletType? = null

            override fun signingComplete(signature: SignatureFromKey, message: Signable) {
                Web3Wallet.respondAuthRequest(
                    Params.AuthRequestResponse.Result(
                        requestId,
                        Model.Cacao.Signature(
                            SignatureType.EIP191.header,
                            Numeric.toHexString(signature.signature),
                            null
                        ),
                        issuer
                    ),
                    { 
                        Timber.i("以太坊登录成功")
                        Unit 
                    },
                    { error -> 
                        Timber.w("以太坊登录失败")
                        Timber.w(error.throwable)
                        Unit 
                    }
                )
            }
        }
    }

    /**
     * 关闭 WalletConnect 活动
     */
    private fun closeWalletConnectActivity() {
        Handler(Looper.getMainLooper()).post {
            App.instance?.topActivity?.onBackPressed()
        }
    }

    /**
     * 获取会话意图
     * @param appContext 应用上下文
     * @return 会话意图
     */
    fun getSessionIntent(appContext: Context): Intent {
        val sessions = walletConnectInteract.getSessions()
        return if (sessions.size == 1) {
            WalletConnectSessionActivity.newIntent(appContext, sessions[0])
        } else {
            Intent(appContext, WalletConnectSessionActivity::class.java)
        }
    }

    // ==================== 回调接口定义 ====================

    /**
     * WalletConnect V2 回调接口
     * 用于处理会话操作的异步回调
     */
//    interface WalletConnectV2Callback {
//        /**
//         * 会话提案已批准
//         */
//        fun onSessionProposalApproved()
//
//        /**
//         * 会话提案已拒绝
//         */
//        fun onSessionProposalRejected()
//
//        /**
//         * 会话已断开连接
//         */
//        fun onSessionDisconnected()
//    }

}
