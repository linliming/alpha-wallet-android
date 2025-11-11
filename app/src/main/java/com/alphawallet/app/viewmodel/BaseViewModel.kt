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
 * Base view model that centralises coroutine helpers, error propagation, and analytics tracking.
 * Subclasses should leverage the provided launch utilities and LiveData/StateFlow streams
 * to expose UI state while keeping asynchronous work lifecycle-aware.
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * Shared static LiveData channels used for cross-view-model UI events.
     */
    companion object {
        protected val queueCompletion = MutableLiveData<Int>()
        protected val pushToastMutable = MutableLiveData<String?>()
        protected val successDialogMutable = MutableLiveData<Int>()
        protected val errorDialogMutable = MutableLiveData<Int>()
        protected val refreshTokens = MutableLiveData<Boolean>()

        /**
         * Emits a toast message to observers; pass null to clear observers.
         */
        fun onPushToast(message: String?) {
            pushToastMutable.postValue(message)
        }
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
     * Launches a coroutine tied to the view model scope with standardised lifecycle hooks.
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
     * Executes the supplied suspending API call and wraps the outcome in a [Result].
     */
    protected suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> =
        try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Cancels the currently active job launched by [launchSafely] or [launchIO].
     */
    protected fun cancelCurrentJob() {
        currentJob?.cancel()
        currentJob = null
    }

    /**
     * Cancels outstanding work when the view model is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        cancelCurrentJob()
    }

    /**
     * Emits progress values for queued work.
     */
    fun onQueueUpdate(complete: Int) {
        queueCompletion.postValue(complete)
    }

    /**
     * Exposes the latest error envelope for UI consumption.
     */
    fun error(): LiveData<ErrorEnvelope?> = error

    /**
     * Exposes progress state as LiveData.
     */
    fun progress(): LiveData<Boolean> = progress

    /**
     * Exposes queue completion events.
     */
    fun queueProgress(): LiveData<Int> = queueCompletion

    /**
     * Exposes toast events as LiveData.
     */
    fun pushToast(): LiveData<String?> = pushToastMutable

    /**
     * Exposes refresh events for token lists.
     */
    fun refreshTokens(): LiveData<Boolean> = refreshTokens

    /**
     * Standardises error handling by logging, mapping, and publishing envelopes.
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
     * Clears the current error state from both LiveData and StateFlow.
     */
    fun clearError() {
        error.value = null
        _errorState.value = null
    }

    /**
     * Hook for launching the send-token experience; override where relevant.
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
     * Hook for presenting a token list; override in subclasses as required.
     */
    open fun showTokenList(
        activity: Activity,
        token: Token,
    ) {
        // 默认实现为空
    }

    /**
     * Hook for presenting ERC20 token details; override when needed.
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
     * Assigns an analytics service instance used by the tracking helpers.
     */
    protected fun setAnalyticsService(analyticsService: AnalyticsServiceType<AnalyticsProperties>?) {
        this.analyticsService = analyticsService
    }

    /**
     * Associates the current user with analytics tracking.
     */
    fun identify(uuid: String) {
        analyticsService?.identify(uuid)
    }

    /**
     * Tracks a navigation event.
     */
    fun track(event: Analytics.Navigation) {
        trackEvent(event.value)
    }

    /**
     * Tracks a navigation event with custom properties.
     */
    fun track(
        event: Analytics.Navigation,
        props: AnalyticsProperties,
    ) {
        trackEventWithProps(event.value, props)
    }

    /**
     * Tracks an action event.
     */
    fun track(event: Analytics.Action) {
        trackEvent(event.value)
    }

    /**
     * Tracks an action event with custom properties.
     */
    fun track(
        event: Analytics.Action,
        props: AnalyticsProperties,
    ) {
        trackEventWithProps(event.value, props)
    }

    /**
     * Tracks an error event with the provided message.
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
     * Internal helper for event tracking without properties.
     */
    private fun trackEvent(event: String) {
        analyticsService?.track(event)
    }

    /**
     * Internal helper for event tracking with properties.
     */
    private fun trackEventWithProps(
        event: String,
        props: AnalyticsProperties,
    ) {
        analyticsService?.track(event, props)
    }

    /**
     * Runs the supplied block on the IO dispatcher and returns its result.
     */
    protected suspend fun <T> withIO(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            block()
        }

    /**
     * Runs the supplied block on the main dispatcher.
     */
    protected suspend fun withMain(block: suspend () -> Unit) {
        withContext(Dispatchers.Main) {
            block()
        }
    }

    /**
     * Suspends execution for the requested duration.
     */
    protected suspend fun delay(millis: Long) {
        kotlinx.coroutines.delay(millis)
    }
}
