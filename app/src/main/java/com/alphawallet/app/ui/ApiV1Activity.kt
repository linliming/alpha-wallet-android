package com.alphawallet.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.api.v1.entity.ApiV1
import com.alphawallet.app.api.v1.entity.request.ApiV1Request
import com.alphawallet.app.api.v1.entity.request.ConnectRequest
import com.alphawallet.app.api.v1.entity.request.SignPersonalMessageRequest
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.ApiV1ViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.ApiV1Dialog
import com.alphawallet.app.widget.ConfirmationWidget
import com.alphawallet.app.widget.SignDataWidget
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.Signable
import dagger.hilt.android.AndroidEntryPoint
import org.web3j.utils.Numeric

/**
 * API V1 请求入口页面，负责解析 deeplink 并引导用户确认连接或签名。
 */
@AndroidEntryPoint
class ApiV1Activity : BaseActivity() {

    private lateinit var viewModel: ApiV1ViewModel
    private var apiV1Dialog: ApiV1Dialog? = null
    private lateinit var request: ApiV1Request
    private var alertDialog: AWalletAlertDialog? = null
    private var confirmationWidget: ConfirmationWidget? = null

    /**
     * 初始化界面与 ViewModel，完成请求解析。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_v1)
        initViewModel()
        val requestUrl = intent.getStringExtra(C.Key.API_V1_REQUEST_URL) ?: run {
            finish()
            return
        }
        request = ApiV1Request(requestUrl)
    }

    /**
     * 进入前台时准备处理请求。
     */
    override fun onResume() {
        super.onResume()
        viewModel.prepare()
    }

    /**
     * 构建 ViewModel 并订阅状态。
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[ApiV1ViewModel::class.java]
        viewModel.defaultWallet().observe(this, this::onDefaultWallet)
        viewModel.signature().observe(this, this::onSignature)
        viewModel.error().observe(this, this::onError)
    }

    /**
     * 默认钱包触发后，根据请求类型弹出对应的对话框。
     */
    private fun onDefaultWallet(wallet: Wallet) {
        when (request.method?.callType) {

            ApiV1.CallType.CONNECT -> {
                val connectRequest = ConnectRequest(request.getRequestUrl())
                apiV1Dialog = setupConnectDialog(connectRequest, wallet.address.toString()).also { it.show() }
            }

            ApiV1.CallType.SIGN_PERSONAL_MESSAGE -> {
                val signRequest = SignPersonalMessageRequest(request.getRequestUrl())
                if (viewModel.addressMatches(wallet.address, signRequest.address)) {
                    apiV1Dialog = setupSignPersonalMessageDialog(signRequest, wallet.address.toString()).also { it.show() }
                } else {
                    cancelSignPersonalMessage()
                }
            }

            else -> {
                cancelSignPersonalMessage()
            }
        }
    }

    /**
     * 构建连接授权对话框。
     */
    private fun setupConnectDialog(req: ConnectRequest, address: String): ApiV1Dialog {
        return ApiV1Dialog(this, req).apply {
            addWidget(
                R.string.label_api_v1_note,
                getString(R.string.message_api_v1_note, Utils.formatAddress(address))
            )
            setPrimaryButtonListener {
                hideFunctionBar()
                connect(address)
            }
            setSecondaryButtonListener {
                hideFunctionBar()
                connect(null)
            }
        }
    }

    /**
     * 构建签名确认对话框，同时展示数据与确认控件。
     */
    private fun setupSignPersonalMessageDialog(req: SignPersonalMessageRequest, address: String): ApiV1Dialog {
        return ApiV1Dialog(this, req).apply {
            addWidget(
                R.string.label_api_v1_note,
                getString(R.string.message_api_v1_note, Utils.formatAddress(address))
            )
            val signDataWidget = SignDataWidget(this@ApiV1Activity, null).apply {
                setupSignData(req.signable)
            }
            addWidget(signDataWidget)
            confirmationWidget = ConfirmationWidget(this@ApiV1Activity, null).also { addWidget(it) }
            setPrimaryButtonListener {
                hideFunctionBar()
                signPersonalMessage(req.signable)
            }
            setSecondaryButtonListener {
                cancelSignPersonalMessage()
                dismiss()
            }
        }
    }

    /**
     * 发起签名流程，完成后由 ViewModel 回调。
     */
    private fun signPersonalMessage(signable: Signable) {
        val callback = object : SignAuthenticationCallback {
            override fun gotAuthorisation(gotAuth: Boolean) {
                confirmationWidget?.startProgressCycle(4)
                viewModel.signMessage(signable)
            }

            override fun cancelAuthentication() = Unit
            override fun gotSignature(signature: SignatureFromKey?) = Unit
        }
        viewModel.getAuthentication(this, callback)
    }

    /**
     * 取消签名操作并重定向。
     */
    private fun cancelSignPersonalMessage() {
        redirect(viewModel.buildSignPersonalMessageResponse(request.redirectUrl, null))
    }

    /**
     * 构造连接回调并重定向到外部浏览器。
     */
    private fun connect(address: String?) {
        redirect(viewModel.buildConnectResponse(request.redirectUrl, address))
    }

    /**
     * 打开浏览器进行跳转。
     */
    private fun redirect(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        finish()
    }

    /**
     * 成功获取签名后更新 UI 并跳转返回。
     */
    private fun onSignature(signature: ByteArray) {
        val uri = viewModel.buildSignPersonalMessageResponse(request.redirectUrl, Numeric.toHexString(signature))
        confirmationWidget?.completeProgressMessage(".") { redirect(uri) }
    }

    /**
     * 错误回调时弹出提示框。
     */
    private fun onError(error: ErrorEnvelope?) {
        alertDialog = AWalletAlertDialog(this).apply {
            setTitle(R.string.title_dialog_error)
            setMessage(error?.message)
            setIcon(AWalletAlertDialog.ERROR)
            setButtonText(R.string.dialog_ok)
            setButtonListener {
                dismiss()
                cancelSignPersonalMessage()
            }
            show()
        }
    }

    /**
     * 进入后台时关闭所有对话框，避免窗口泄漏。
     */
    override fun onPause() {
        super.onPause()
        apiV1Dialog?.takeIf { it.isShowing }?.dismiss()
        alertDialog?.takeIf { it.isShowing }?.dismiss()
    }

    companion object
}
