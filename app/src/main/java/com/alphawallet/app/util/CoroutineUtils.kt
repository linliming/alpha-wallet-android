package com.alphawallet.app.util

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 协程工具类
 * 提供 RxJava 到协程的转换功能
 *
 * @author AlphaWallet Team
 * @since 2024
 */
object CoroutineUtils {
    /**
     * 将 RxJava Single 转换为协程
     *
     * @param T 返回类型
     * @return 协程结果
     */
    suspend fun <T> Single<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            val disposable =
                subscribe(
                    { result ->
                        continuation.resume(result)
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    },
                )

            continuation.invokeOnCancellation {
                disposable.dispose()
            }
        }

    /**
     * 将 RxJava Completable 转换为协程
     *
     * @return 协程结果
     */
    suspend fun Completable.await(): Unit =
        suspendCancellableCoroutine { continuation ->
            val disposable =
                subscribe(
                    {
                        continuation.resume(Unit)
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    },
                )

            continuation.invokeOnCancellation {
                disposable.dispose()
            }
        }

    /**
     * 安全的协程执行包装器
     *
     * @param T 返回类型
     * @param operation 要执行的操作
     * @return 操作结果或异常
     */
    suspend fun <T> safeExecute(operation: suspend () -> T): Result<T> =
        try {
            Result.success(operation())
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * 带重试的协程执行
     *
     * @param T 返回类型
     * @param maxRetries 最大重试次数
     * @param delayMs 重试延迟（毫秒）
     * @param operation 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        operation: suspend () -> T,
    ): T {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1))
                }
            }
        }

        throw lastException ?: Exception("Operation failed after $maxRetries attempts")
    }

    /**
     * 安全的协程启动方法
     * 在指定的协程作用域中安全地启动协程，自动处理异常
     *
     * @param scope 协程作用域
     * @param dispatcher 协程调度器，默认为 IO
     * @param onError 错误处理回调（可选）
     * @param block 要执行的协程代码块
     * @return 启动的协程 Job
     */
    fun launchSafely(
        scope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = scope.launch(dispatcher + SupervisorJob()) {
        try {
            block()
        } catch (e: Throwable) {
            Timber.e(e, "Error in launchSafely")
            onError?.invoke(e)
        }
    }

    /**
     * 安全的协程启动方法（简化版本）
     * 使用默认的 IO 调度器
     *
     * @param scope 协程作用域
     * @param block 要执行的协程代码块
     * @return 启动的协程 Job
     */
    fun launchSafely(
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) = launchSafely(scope, Dispatchers.IO, null, block)

    /**
     * 全局安全的协程启动方法
     * 使用全局协程作用域，适用于没有特定作用域的场景
     *
     * @param block 要执行的协程代码块
     * @return 启动的协程 Job
     */
    fun launchSafely(block: suspend CoroutineScope.() -> Unit) =
        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO + SupervisorJob()) {
            try {
                block()
            } catch (e: Throwable) {
                Timber.e(e, "Error in global launchSafely")
            }
        }

    /**
     * 安全的协程启动方法（带错误处理）
     *
     * @param scope 协程作用域
     * @param onError 错误处理回调
     * @param block 要执行的协程代码块
     * @return 启动的协程 Job
     */
    fun launchSafely(
        scope: CoroutineScope,
        onError: (Throwable) -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ) = launchSafely(scope, Dispatchers.IO, onError, block)
}
