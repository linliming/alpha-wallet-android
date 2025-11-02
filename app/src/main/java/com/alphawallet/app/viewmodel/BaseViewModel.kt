package com.alphawallet.app.viewmodel

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alphawallet.app.C
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.ServiceException
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.AnalyticsServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Kotlin 版本的 BaseViewModel
 * 将 RxJava 替换为协程，提供更好的异步操作支持
 */
abstract class BaseViewModel : ViewModel() {
    // 静态 LiveData 对象
    companion object {
        protected val queueCompletion = MutableLiveData<Int>()
        protected val pushToastMutable = MutableLiveData<String>()
        protected val successDialogMutable = MutableLiveData<Int>()
        protected val errorDialogMutable = MutableLiveData<Int>()
        protected val refreshTokens = MutableLiveData<Boolean>()
    }

    // 实例 LiveData 对象
    protected val error = MutableLiveData<ErrorEnvelope?>()
    protected val progress = MutableLiveData<Boolean>()

    // 协程相关
    private var currentJob: Job? = null
    private var analyticsService: AnalyticsServiceType<AnalyticsProperties>? = null

    // StateFlow 替代 LiveData (可选)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorState = MutableStateFlow<ErrorEnvelope?>(null)
    val errorState: StateFlow<ErrorEnvelope?> = _errorState.asStateFlow()

    /**
     * 安全启动协程
     */
    protected fun launchSafely(
        onStart: () -> Unit = { _isLoading.value = true },
        onComplete: () -> Unit = { _isLoading.value = false },
        onError: (Throwable) -> Unit = { handleError(it) },
        block: suspend () -> Unit,
    ): Job =
        viewModelScope
            .launch {
                try {
                    onStart()
                    block()
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    onComplete()
                }
            }.also { currentJob = it }

    /**
     * 在 IO 线程执行协程
     */
    protected fun launchIO(
        onStart: () -> Unit = { _isLoading.value = true },
        onComplete: () -> Unit = { _isLoading.value = false },
        onError: (Throwable) -> Unit = { handleError(it) },
        block: suspend () -> Unit,
    ): Job =
        viewModelScope
            .launch(Dispatchers.IO) {
                try {
                    onStart()
                    block()
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    onComplete()
                }
            }.also { currentJob = it }

    /**
     * 网络调用包装器
     */
    protected suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> =
        try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * 取消当前协程
     */
    protected fun cancelCurrentJob() {
        currentJob?.cancel()
        currentJob = null
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        cancelCurrentJob()
    }

    // 静态方法
    fun onQueueUpdate(complete: Int) {
        queueCompletion.postValue(complete)
    }

    fun onPushToast(message: String) {
        pushToastMutable.postValue(message)
    }

    // LiveData 访问方法
    fun error(): LiveData<ErrorEnvelope?> = error

    fun progress(): LiveData<Boolean> = progress

    fun queueProgress(): LiveData<Int> = queueCompletion

    fun pushToast(): LiveData<String> = pushToastMutable

    fun refreshTokens(): LiveData<Boolean> = refreshTokens

    /**
     * 错误处理
     */
    protected fun handleError(throwable: Throwable) {
        Timber.e(throwable)
        val errorEnvelope =
            when (throwable) {
                is ServiceException -> throwable.error
                else -> {
                    val message =
                        if (TextUtils.isEmpty(throwable.message)) {
                            "Unknown Error"
                        } else {
                            throwable.message ?: "Unknown Error"
                        }
                    ErrorEnvelope(C.ErrorCode.UNKNOWN, message, throwable)
                }
            }

        error.postValue(errorEnvelope)
        _errorState.value = errorEnvelope
    }

    /**
     * 清除错误状态
     */
    fun clearError() {
        error.value = null
        _errorState.value = null
    }

    /**
     * 显示发送代币界面
     */
    open fun showSendToken(
        context: Context,
        address: String,
        symbol: String,
        decimals: Int,
        token: Token,
    ) {
        // 默认实现为空
    }

    /**
     * 显示代币列表
     */
    open fun showTokenList(
        activity: Activity,
        token: Token,
    ) {
        // 默认实现为空
    }

    /**
     * 显示 ERC20 代币详情
     */
    open fun showErc20TokenDetail(
        context: Activity,
        address: String,
        symbol: String,
        decimals: Int,
        token: Token,
    ) {
        // 默认实现为空
    }

    /**
     * 设置分析服务
     */
    protected fun setAnalyticsService(analyticsService: AnalyticsServiceType<AnalyticsProperties>?) {
        this.analyticsService = analyticsService
    }

    /**
     * 用户识别
     */
    fun identify(uuid: String) {
        analyticsService?.identify(uuid)
    }

    /**
     * 跟踪导航事件
     */
    fun track(event: Analytics.Navigation) {
        trackEvent(event.value)
    }

    /**
     * 跟踪带属性的导航事件
     */
    fun track(
        event: Analytics.Navigation,
        props: AnalyticsProperties,
    ) {
        trackEventWithProps(event.value, props)
    }

    /**
     * 跟踪操作事件
     */
    fun track(event: Analytics.Action) {
        trackEvent(event.value)
    }

    /**
     * 跟踪带属性的操作事件
     */
    fun track(
        event: Analytics.Action,
        props: AnalyticsProperties,
    ) {
        trackEventWithProps(event.value, props)
    }

    /**
     * 跟踪错误事件
     */
    fun trackError(
        source: Analytics.Error,
        message: String,
    ) {
        val props =
            AnalyticsProperties().apply {
                put(Analytics.PROPS_ERROR_MESSAGE, message)
            }
        trackEventWithProps(source.value, props)
    }

    /**
     * 跟踪事件
     */
    private fun trackEvent(event: String) {
        analyticsService?.track(event)
    }

    /**
     * 跟踪带属性的事件
     */
    private fun trackEventWithProps(
        event: String,
        props: AnalyticsProperties,
    ) {
        analyticsService?.track(event, props)
    }

    /**
     * 扩展函数：在 IO 线程执行并返回结果
     */
    protected suspend fun <T> withIO(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            block()
        }

    /**
     * 扩展函数：在主线程执行
     */
    protected suspend fun withMain(block: suspend () -> Unit) {
        withContext(Dispatchers.Main) {
            block()
        }
    }

    /**
     * 扩展函数：延迟执行
     */
    protected suspend fun delay(millis: Long) {
        kotlinx.coroutines.delay(millis)
    }
}
