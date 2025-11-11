package com.alphawallet.app.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActionSheetInterface
import com.alphawallet.app.service.SignatureLookupService
import com.alphawallet.app.web3.entity.Web3Transaction
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.web3j.utils.Numeric

/**
 * Created by JB on 15/01/2021.
 */

class TransactionDetailWidget(context: Context?, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private val textTransactionSummary: TextView
    private val textFullDetails: TextView
    private val textFunctionName: TextView
    private val layoutDetails: LinearLayout
    private val layoutHolder: LinearLayout
    private val layoutHeader: LinearLayout
    private val progressBar: ProgressBar
    private var sheetInterface: ActionSheetInterface? = null

    private var disposable: Disposable? = null

    init {
        inflate(context, R.layout.transaction_detail_widget, this)
        textTransactionSummary = findViewById<TextView>(R.id.text_transaction_summary)
        textFullDetails = findViewById<TextView>(R.id.text_full_details)
        textFunctionName = findViewById<TextView>(R.id.text_function_name)
        layoutDetails = findViewById<LinearLayout>(R.id.layout_detail)
        layoutHolder = findViewById<LinearLayout>(R.id.layout_holder)
        layoutHeader = findViewById<LinearLayout>(R.id.layout_header)
        progressBar = findViewById<ProgressBar>(R.id.progress)
    }

    fun setupTransaction(
        w3tx: Web3Transaction, chainId: Long, symbol: String?,
        asIf: ActionSheetInterface
    ) {
        progressBar.setVisibility(GONE)
        textTransactionSummary.setVisibility(VISIBLE)
        textFullDetails.setText(w3tx.getFormattedTransaction(getContext(), chainId, symbol))
        sheetInterface = asIf

        if (!TextUtils.isEmpty(w3tx.description)) {
            textTransactionSummary.setText(w3tx.description)
        } else {
            val displayText = (Numeric.prependHexPrefix(w3tx.payload)).substring(0, 10)
            textTransactionSummary.setText(displayText)
            textFunctionName.setText(displayText)
        }

        if (w3tx.isConstructor) {
            val constructor = getContext().getString(R.string.constructor)
            textTransactionSummary.setText(constructor)
            textFunctionName.setText(constructor)
        } else {
            val svc = SignatureLookupService()
            disposable = svc.getFunctionName(w3tx.payload!!)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                    Consumer { functionName: String? -> this.onResult(functionName) },
                    Consumer { error: Throwable? -> })
        }

        layoutHolder.setOnClickListener(OnClickListener { v: View? ->
            if (layoutDetails.getVisibility() == GONE) {
                layoutDetails.setVisibility(VISIBLE)
                layoutHeader.setVisibility(GONE)
                sheetInterface!!.lockDragging(true)
            } else {
                layoutDetails.setVisibility(GONE)
                layoutHeader.setVisibility(VISIBLE)
                sheetInterface!!.lockDragging(false)
            }
        })
    }

    fun onDestroy() {
        if (disposable != null && !disposable!!.isDisposed()) {
            disposable!!.dispose()
        }
    }

    private fun onResult(functionName: String?) {
        if (!TextUtils.isEmpty(functionName)) {
            textTransactionSummary.setText(functionName)
            textFunctionName.setText(functionName)
        }
    }
}
