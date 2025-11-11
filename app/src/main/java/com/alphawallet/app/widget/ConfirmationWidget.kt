package com.alphawallet.app.widget

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.MainThread
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.repository.TransactionsRealmCache
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.ui.widget.entity.ProgressCompleteCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import io.realm.Realm
import io.realm.RealmResults

/**
 * 负责展示交易提交后的确认动画与状态文本。
 */
class ConfirmationWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {

    private val progress: ProgressKnobkerry
    private val hashText: TextView
    private val progressLayout: RelativeLayout
    private var realmTransactionUpdates: RealmResults<RealmTransaction>? = null
    private val supervisorJob = SupervisorJob()
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + supervisorJob)

    init {
        inflate(context, R.layout.item_confirmation, this)
        progress = findViewById(R.id.progress_knob)
        progressLayout = findViewById(R.id.layout_confirmation)
        hashText = findViewById(R.id.hash_text)
    }

    /**
     * 启动带预期时间的进度动画，并监听交易完成。
     */
    fun startAnimate(expectedTransactionTime: Long, transactionRealm: Realm, txHash: String?) {
        progress.visibility = View.VISIBLE
        progressLayout.visibility = View.VISIBLE
        progress.startAnimation(expectedTransactionTime)
        createCompletionListener(transactionRealm, txHash)
        if (!txHash.isNullOrEmpty()) {
            hashText.visibility = View.VISIBLE
            hashText.alpha = 1.0f
            hashText.text = txHash
            hashText.animate().setStartDelay(2000).alpha(0.0f).setDuration(1500)
        }
    }

    /**
     * 启动循环进度动画，例如等待签名结果。
     */
    fun startProgressCycle(cycleTime: Int) {
        uiScope.launch {
            progress.visibility = View.VISIBLE
            progressLayout.visibility = View.VISIBLE
            progress.startAnimation(cycleTime.toLong())
        }
    }

    /**
     * 展示完成提示，并在动画结束时回调。
     */
    fun completeProgressMessage(message: String?, callback: ProgressCompleteCallback) {
        val animatorListener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) = Unit
            override fun onAnimationEnd(animation: Animator) = callback.progressComplete()
            override fun onAnimationCancel(animation: Animator) = Unit
            override fun onAnimationRepeat(animation: Animator) = Unit
        }

        uiScope.launch {
            if (!message.isNullOrEmpty()) {
                completeProgressSuccess(true)
                hashText.visibility = View.VISIBLE
                hashText.alpha = 1.0f
                if (message.length > 1) hashText.text = message
                hashText.animate().alpha(0.0f).setDuration(1500).setListener(animatorListener)
            } else {
                completeProgressSuccess(false)
                hashText.visibility = View.GONE
                hashText.animate().alpha(0.0f).setDuration(1500).setListener(animatorListener)
            }
        }
    }

    /**
     * 监听交易写入数据库状态，完成后更新动画。
     */
    private fun createCompletionListener(realm: Realm, txHash: String?) {
        realmTransactionUpdates?.removeAllChangeListeners()
        if (txHash.isNullOrEmpty()) return

        val results = realm.where(RealmTransaction::class.java)
            .equalTo("hash", txHash)
            .findAllAsync()
            .also { realmTransactionUpdates = it }

        results.addChangeListener { realmTransactions ->
            if (!realmTransactions.isEmpty()) {
                val rTx = realmTransactions.first()
                if (rTx != null && !rTx.isPending) {
                    val tx: Transaction = TransactionsRealmCache.convert(rTx)
                    uiScope.launch {
                        completeProgressSuccess(!tx.hasError())
                    }
                }
            }
        }
    }

    /**
     * 根据成功与否展示最终状态。
     */
    private fun completeProgressSuccess(success: Boolean) {
        realmTransactionUpdates?.removeAllChangeListeners()
        progress.visibility = View.VISIBLE
        progressLayout.visibility = View.VISIBLE
        progress.setComplete(success)
    }

    /**
     * 开始等待动画，用于进入准备状态。
     */
    fun showAnimate() {
        progress.visibility = View.VISIBLE
        progressLayout.visibility = View.VISIBLE
        progress.waitCycle()
    }

    /**
     * 隐藏整体布局。
     */
    fun hide() {
        progressLayout.visibility = View.GONE
    }

    /**
     * 释放 Realm 监听与协程任务，防止内存泄漏。
     */
    fun onDestroy() {
        realmTransactionUpdates?.removeAllChangeListeners()
        supervisorJob.cancelChildren()
    }

    override fun onDetachedFromWindow() {
        onDestroy()
        super.onDetachedFromWindow()
    }
}
