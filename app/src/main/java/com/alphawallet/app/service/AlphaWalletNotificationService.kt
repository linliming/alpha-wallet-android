package com.alphawallet.app.service

import android.net.Uri
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.C
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.util.JsonUtils
import com.google.firebase.messaging.FirebaseMessaging
import com.walletconnect.foundation.util.jwt.signJwt
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlphaWallet通知服务
 *
 * 负责管理钱包地址的通知订阅和取消订阅功能，包括：
 * - Firebase主题订阅管理
 * - 后端API通知订阅
 * - 网络请求处理
 * - 错误处理和日志记录
 *
 * 技术特点：
 * - 使用协程处理异步操作
 * - 使用Hilt进行依赖注入
 * - 统一的错误处理机制
 * - 资源自动管理
 */
@Singleton
class AlphaWalletNotificationService
    @Inject
    constructor(
        private val walletRepository: WalletRepositoryType,
    ) {
        // 网络配置常量
        companion object {
            private const val BASE_API_URL = BuildConfig.NOTIFICATION_API_BASE_URL
            const val SUBSCRIPTIONS_API_PATH = "$BASE_API_URL/subscriptions"
            private const val USER_AGENT = "Chrome/74.0.3729.169"
            private const val CONTENT_TYPE = "application/json"
        }

        // HTTP客户端配置
        private val httpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(C.CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
                .writeTimeout(C.WRITE_TIMEOUT.toLong(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        // 协程作用域管理
        private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /**
         * 构建POST请求
         * @param api API地址
         * @param requestBody 请求体
         * @return 构建好的请求对象
         */
        private fun buildPostRequest(
            api: String,
            requestBody: RequestBody,
        ): Request =
            Request
                .Builder()
                .url(api)
                .header("User-Agent", USER_AGENT)
                .addHeader("Content-Type", CONTENT_TYPE)
                .post(requestBody)
                .build()

        /**
         * 构建DELETE请求
         * @param api API地址
         * @return 构建好的请求对象
         */
        private fun buildDeleteRequest(api: String): Request =
            Request
                .Builder()
                .url(api)
                .header("User-Agent", USER_AGENT)
                .addHeader("Content-Type", CONTENT_TYPE)
                .delete()
                .build()

        /**
         * 执行HTTP请求
         * @param request 请求对象
         * @return 响应结果字符串
         */
        private suspend fun executeRequest(request: Request): String =
            withContext(Dispatchers.IO) {
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.string() ?: JsonUtils.EMPTY_RESULT
                        } else {
                            response.body?.string() ?: "Request failed with code: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "网络请求执行失败")
                    e.message ?: "Unknown error"
                }
            }

        /**
         * 构建订阅请求体
         * @param address 钱包地址
         * @param chainId 链ID字符串
         * @return 请求体，构建失败时返回null
         */
        private fun buildSubscribeRequest(
            address: String,
            chainId: String,
        ): RequestBody? =
            try {
                val json =
                    JSONObject().apply {
                        put("wallet", address)
                        put("chainId", chainId.toLong())
                    }
                json.toString().toRequestBody(CONTENT_TYPE.toMediaType())
            } catch (e: Exception) {
                Timber.e(e, "构建订阅请求体失败，address: $address, chainId: $chainId")
                null
            }

        /**
         * 订阅指定链的通知
         * @param chainId 链ID
         * @return 订阅结果
         */
        suspend fun subscribe(chainId: Long): String =
            try {
                val wallet = walletRepository.getDefaultWallet()
                val address = wallet.address
                if (address.isNullOrEmpty()) {
                    "订阅失败: 钱包地址为空"
                } else {
                    doSubscribe(address, chainId)
                }
            } catch (e: Exception) {
                Timber.e(e, "订阅通知失败，chainId: $chainId")
                "订阅失败: ${e.message}"
            }

        /**
         * 执行订阅操作
         * @param address 钱包地址
         * @param chainId 链ID
         * @return 订阅结果
         */
        private suspend fun doSubscribe(
            address: String,
            chainId: Long,
        ): String {
            // 订阅Firebase主题
            subscribeToFirebaseTopic(address, chainId)

            // 构建API请求
            val url =
                Uri
                    .Builder()
                    .encodedPath(SUBSCRIPTIONS_API_PATH)
                    .build()
                    .toString()

            val requestBody =
                buildSubscribeRequest(address, chainId.toString())
                    ?: return "构建请求体失败"

            return executeRequest(buildPostRequest(url, requestBody))
        }

        /**
         * 取消订阅指定链的通知
         * @param chainId 链ID
         * @return 取消订阅结果
         */
        suspend fun unsubscribe(chainId: Long): String =
            try {
                val wallet = walletRepository.getDefaultWallet()
                val address = wallet.address
                if (address.isNullOrEmpty()) {
                    "取消订阅失败: 钱包地址为空"
                } else {
                    doUnsubscribe(address, chainId)
                }
            } catch (e: Exception) {
                Timber.e(e, "取消订阅失败，chainId: $chainId")
                "取消订阅失败: ${e.message}"
            }

        /**
         * 异步取消订阅主题（临时方法）
         * TODO: [Notifications] 实现完整的取消订阅功能后删除此方法
         * @param chainId 链ID
         */
        fun unsubscribeToTopic(chainId: Long) {
            serviceScope.launch {
                try {
                    val wallet = walletRepository.getDefaultWallet()
                    val address = wallet.address
                    if (!address.isNullOrEmpty()) {
                        unsubscribeToFirebaseTopic(address, chainId)
                    } else {
                        Timber.w("异步取消订阅主题失败: 钱包地址为空，chainId: $chainId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "异步取消订阅主题失败，chainId: $chainId")
                }
            }
        }

        /**
         * 执行取消订阅操作
         * @param address 钱包地址
         * @param chainId 链ID
         * @return 取消订阅结果
         */
        private suspend fun doUnsubscribe(
            address: String,
            chainId: Long,
        ): String {
            // 取消订阅Firebase主题
            unsubscribeToFirebaseTopic(address, chainId)

            // 构建API请求
            val url =
                Uri
                    .Builder()
                    .encodedPath(SUBSCRIPTIONS_API_PATH)
                    .appendEncodedPath(address)
                    .appendEncodedPath(chainId.toString())
                    .build()
                    .toString()

            return executeRequest(buildDeleteRequest(url))
        }

        /**
         * 订阅Firebase主题
         * @param address 钱包地址
         * @param chainId 链ID
         */
        private suspend fun subscribeToFirebaseTopic(
            address: String,
            chainId: Long,
        ) {
            val topic = "$address-$chainId"

            try {
                // 使用协程包装Firebase异步操作
                suspendCancellableCoroutine<Unit> { continuation ->
                    FirebaseMessaging
                        .getInstance()
                        .subscribeToTopic(topic)
                        .addOnCompleteListener { task ->
                            val msg =
                                if (task.isSuccessful) {
                                    "成功订阅主题: $topic"
                                } else {
                                    "订阅主题失败: $topic"
                                }
                            Timber.d(msg)

                            if (continuation.isActive) {
                                continuation.resume(Unit) {}
                            }
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Firebase主题订阅异常，topic: $topic")
            }
        }

        /**
         * 取消订阅Firebase主题
         * @param address 钱包地址
         * @param chainId 链ID
         */
        private suspend fun unsubscribeToFirebaseTopic(
            address: String,
            chainId: Long,
        ) {
            val topic = "$address-$chainId"

            try {
                // 使用协程包装Firebase异步操作
                suspendCancellableCoroutine<Unit> { continuation ->
                    FirebaseMessaging
                        .getInstance()
                        .unsubscribeFromTopic(topic)
                        .addOnCompleteListener { task ->
                            val msg =
                                if (task.isSuccessful) {
                                    "成功取消订阅主题: $topic"
                                } else {
                                    "取消订阅主题失败: $topic"
                                }
                            Timber.d(msg)

                            if (continuation.isActive) {
                                continuation.resume(Unit) {}
                            }
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Firebase主题取消订阅异常，topic: $topic")
            }
        }

        /**
         * 清理资源
         * 取消所有正在进行的协程操作
         */
        fun cleanup() {
            serviceScope.cancel()
        }
    }
