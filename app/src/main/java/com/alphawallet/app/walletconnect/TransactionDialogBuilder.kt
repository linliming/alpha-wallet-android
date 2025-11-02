package com.alphawallet.app.walletconnect

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.walletconnect.SignType
import com.alphawallet.app.service.GasService
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.viewmodel.WalletConnectViewModel
import com.alphawallet.app.walletconnect.entity.WCEthereumTransaction
import com.alphawallet.app.walletconnect.util.WalletConnectHelper
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.ActionSheetDialog
import com.alphawallet.hardware.SignatureFromKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.walletconnect.web3.wallet.client.Wallet
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.web3j.utils.Numeric
import java.math.BigDecimal

class TransactionDialogBuilder(
    private val activity: Activity,
    private val sessionRequest: Wallet.Model.SessionRequest,
    private val settledSession: Wallet.Model.Session,
    private val awWalletConnectClient: AWWalletConnectClient,
    private val signType: SignType
) : DialogFragment() {

    private lateinit var viewModel: WalletConnectViewModel
    private lateinit var actionSheetDialog: ActionSheetDialog
    private var gasEstimateDisposable: Disposable? = null
    private var isApproved: Boolean = false

    // 使用 Kotlin 风格的 ActivityResultLauncher 初始化
    private val activityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            actionSheetDialog.setCurrentGasIndex(result)
        }

    init {
        // 在构造器中直接调用初始化方法，这是 Kotlin 的常见做法
        initViewModel()
    }

    private fun initViewModel() {
        // activity 本身是 ViewModelStoreOwner，无需强制转换
        viewModel = ViewModelProvider(this)[WalletConnectViewModel::class.java]
        viewModel.blankLiveData()
        viewModel.transactionFinalised().observe(this, this::txWritten)
        viewModel.transactionSigned().observe(this, this::txSigned)
        viewModel.transactionError().observe(this, this::txError)

        // 使用 ?. 安全调用和 let 块来处理可空的 chainId
        sessionRequest.chainId?.let { chainIdStr ->
            viewModel.startGasCycle(WalletConnectHelper.getChainId(chainIdStr))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.blankLiveData()

        val listType = object : TypeToken<ArrayList<WCEthereumTransaction>>() {}.type
        val transactionList: List<WCEthereumTransaction> =
            Gson().fromJson(sessionRequest.request.params, listType)
        val wcTx = transactionList[0]

        // 使用 let 块和 ?. 安全调用来处理可空的 chainId
        val chainId = sessionRequest.chainId?.let {
            WalletConnectHelper.getChainId(it)
        } ?: return super.onCreateDialog(savedInstanceState) // 如果 chainId 为空，则无法继续

        val w3Tx = Web3Transaction(wcTx, wcTx.hashCode().toLong(), signType)
        val fromWallet = viewModel.findWallet(wcTx.from)
        val token = viewModel.tokensService.getTokenOrBase(chainId, w3Tx.recipient.toString())

        actionSheetDialog = ActionSheetDialog(
            activity,
            w3Tx,
            token,
            "",
            w3Tx.recipient.toString(),
            viewModel.tokensService,
            object : ActionSheetCallback {
                override fun getAuthorisation(callback: SignAuthenticationCallback) {
                    viewModel.getAuthenticationForSignature(fromWallet, activity, callback)
                }

                override fun signTransaction(tx: Web3Transaction) {
                    viewModel.requestSignatureOnly(tx, fromWallet, chainId)
                }

                override fun getWalletType(): WalletType = fromWallet.type

                override fun getGasService(): GasService = viewModel.gasService

                override fun sendTransaction(tx: Web3Transaction) {
                    viewModel.requestSignature(tx, fromWallet, chainId)
                }

                override fun completeSendTransaction(tx: Web3Transaction, signature: SignatureFromKey) {
                    token?.tokenInfo?.chainId?.let { viewModel.sendTransaction(fromWallet, it, tx, signature) }
                }

                override fun completeSignTransaction(tx: Web3Transaction, signature: SignatureFromKey) {
                    token?.tokenInfo?.chainId?.let { viewModel.signTransaction(it, tx, signature) }
                }

                override fun dismissed(txHash: String, callbackId: Long, actionCompleted: Boolean) {
                    if (!actionCompleted) {
                        awWalletConnectClient.reject(sessionRequest)
                    }
                }

                override fun notifyConfirm(mode: String) {
                    // No-op
                }

                override fun denyWalletConnect() {
                    awWalletConnectClient.reject(sessionRequest)
                }

                override fun gasSelectLauncher(): ActivityResultLauncher<Intent> = activityResultLauncher
            })

        actionSheetDialog.apply {
            setSigningWallet(fromWallet.address)
            if (signType == SignType.SIGN_TX) {
                setSignOnly()
            }
            settledSession.metaData?.url?.let { setURL(it) } // 安全设置 URL
            setCanceledOnTouchOutside(false)
            waitForEstimate()
        }

        isApproved = false

        val payload = if (w3Tx.payload != null) Numeric.hexStringToByteArray(w3Tx.payload) else null

        gasEstimateDisposable = viewModel.calculateGasEstimate(
            fromWallet,
            payload,
            chainId,
            w3Tx.recipient.toString(),
            BigDecimal(w3Tx.value),
            w3Tx.gasLimit
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { actionSheetDialog.setGasEstimate(it) },
                { it.printStackTrace() } // 简单的错误处理
            )

        return actionSheetDialog
    }

    private fun txWritten(txData: TransactionReturn?) {
        // 使用 let 安全处理可空的 txData
        txData?.let {
            it.hash?.let { it1 -> approve(it1) }
            actionSheetDialog.transactionWritten(it.hash)
        }
    }

    private fun txSigned(txData: TransactionReturn?) {
        txData?.let {
            it.hash?.let { it1 -> approve(it1) }
            actionSheetDialog.transactionWritten(it.displayData)
        }
    }

    private fun txError(txError: TransactionReturn?) {
        txError?.throwable?.let { reject(it) }
    }

    private fun reject(error: Throwable) {
        // activity 可能为 null，所以使用 ?.
        activity.let {
            Toast.makeText(it, error.message, Toast.LENGTH_SHORT).show()
        }
        awWalletConnectClient.reject(sessionRequest)
        actionSheetDialog.dismiss()
    }

    private fun approve(hashData: String) {
        isApproved = true
        // 延迟1秒发送批准，以确保UI有时间更新
        Handler(Looper.getMainLooper()).postDelayed({
            awWalletConnectClient.approve(sessionRequest, hashData)
        }, 1000)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        handleDismissal()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        handleDismissal()
    }

    private fun handleDismissal() {
        if (!isApproved) {
            awWalletConnectClient.reject(sessionRequest)
        }
        gasEstimateDisposable?.dispose() // 清理 RxJava 订阅
        viewModel.onDestroy()
    }
}
