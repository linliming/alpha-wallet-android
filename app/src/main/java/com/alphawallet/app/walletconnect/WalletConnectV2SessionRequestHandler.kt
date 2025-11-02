package com.alphawallet.app.walletconnect

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.alphawallet.app.R
import com.alphawallet.app.entity.walletconnect.SignType
import com.alphawallet.app.entity.walletconnect.WalletConnectV2SessionItem
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.ui.HomeActivity
import com.alphawallet.app.ui.WalletConnectV2Activity
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.walletconnect.entity.BaseRequest
import com.alphawallet.app.walletconnect.entity.EthSignRequest
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.ActionSheet
import com.alphawallet.app.widget.ActionSheetSignDialog
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable
import com.walletconnect.web3.wallet.client.Wallet
import timber.log.Timber

// 定义常量以避免魔法字符串
private const val DEVELOPER_OVERRIDE = "developer_override"

class WalletConnectV2SessionRequestHandler(
    val sessionRequest: Wallet.Model.SessionRequest,
    private val settledSession: Wallet.Model.Session,
    private val activity: Activity,
    private val client: AWWalletConnectClient
) {
    // 使用 lazy 初始化，确保 sessionItem 只在需要时创建一次
    private val sessionItem: WalletConnectV2SessionItem by lazy {
        WalletConnectV2SessionItem(settledSession)
    }
    private var actionSheet: ActionSheet? = null

    fun handle(method: String, aCallback: ActionSheetCallback) {
        activity.runOnUiThread {
            showDialog(method, aCallback)
        }
    }

    private fun showDialog(method: String, aCallback: ActionSheetCallback) {
        when (method) {
            "eth_signTransaction", "eth_sendTransaction" -> {
                val signType = if (method == "eth_signTransaction") SignType.SIGN_TX else SignType.SEND_TX
                val fragmentManager = (activity as? AppCompatActivity)?.supportFragmentManager
                if (fragmentManager != null) {
                    TransactionDialogBuilder(activity, sessionRequest, settledSession, client, signType)
                        .show(fragmentManager, "wc_call")
                } else {
                    Timber.e("Activity is not an AppCompatActivity, cannot show transaction dialog.")
                }
                return
            }
        }

        val signRequest = EthSignRequest.getSignRequest(sessionRequest)
        if (signRequest == null) {
            Timber.e("Method %s not supported.", method)
            // 可选择拒绝请求
            // aCallback.dismissed(...)
            return
        }

        val signable = signRequest.getSignable(
            sessionRequest.request.id,
            settledSession.metaData?.url ?: ""
        )

        when {
            // isDangerous 是一个代码异味，通常表示需要用户特别注意。这里用 developer_override 控制
            signable.isDangerous -> showNotSigning(aCallback, signRequest, signable)
            // 验证链 ID 是否在会话支持的列表中
            !validateChainId(signable) -> checkProceed(aCallback, signRequest, signable)
            else -> showActionSheet(aCallback, signRequest, signable)
        }
    }

    private fun validateChainId(signable: Signable): Boolean {
        return when (signable.messageType) {
            SignMessageType.SIGN_MESSAGE,
            SignMessageType.SIGN_PERSONAL_MESSAGE,
            SignMessageType.SIGN_TYPED_DATA -> true // 这些类型不检查链 ID

            SignMessageType.SIGN_TYPED_DATA_V3,
            SignMessageType.SIGN_TYPED_DATA_V4 ->
                // 如果 signable 未指定 chainId (-1)，则视为不限制。
                // 否则，检查 chainId 是否在会话声明支持的链列表中。
                signable.chainId == -1L || getChainListFromSession().contains(signable.chainId)

            SignMessageType.ATTESTATION -> {
                // TODO: 检查 attestation 签名链
                true
            }

            SignMessageType.SIGN_ERROR -> false
        }
    }

    // 从 sessionItem 中解析并缓存链 ID 列表
    private val chainListFromSession: List<Long> by lazy {
        sessionItem.chains.mapNotNull { chain ->
            chain.split(":").getOrNull(1)?.toLongOrNull()
        }
    }

    private fun getChainListFromSession(): List<Long> {
        return chainListFromSession
    }


    private fun showActionSheet(aCallback: ActionSheetCallback, signRequest: BaseRequest, signable: Signable) {
        (activity as? HomeActivity)?.clearWalletConnectRequest()

        actionSheet?.takeIf { it.isShowing }?.forceDismiss()

        actionSheet = ActionSheetSignDialog(activity, aCallback, signable).apply {
            setSigningWallet(signRequest.walletAddress)
            settledSession.metaData?.icons?.firstOrNull()?.let { setIcon(it) }
            show()
        }
    }

    private fun checkProceed(aCallback: ActionSheetCallback, signRequest: BaseRequest, signable: Signable) {
        val networkName = if (EthereumNetworkBase.isChainSupported(signable.chainId)) {
            EthereumNetworkBase.getShortChainName(signable.chainId)
        } else {
            signable.chainId.toString()
        }
        val message = activity.getString(R.string.session_not_authorised, networkName)

        AWalletAlertDialog(activity, AWalletAlertDialog.ERROR).apply {
            setMessage(message)
            setButton(R.string.override) {
                this.dismiss()
                showActionSheet(aCallback, signRequest, signable)
            }
            setSecondaryButton(R.string.action_cancel) {
                this.dismiss()
                cancelRequest(aCallback, signable)
            }
            setCancelable(false)
            show()
        }
    }

    private fun showErrorDialog(aCallback: ActionSheetCallback, signable: Signable, session: WalletConnectV2SessionItem) {
        val networkName = EthereumNetworkBase.getShortChainName(signable.chainId)
        val message = if (EthereumNetworkBase.isChainSupported(signable.chainId)) {
            activity.getString(R.string.error_eip712_wc2_disabled_network, networkName)
        } else {
            activity.getString(R.string.error_eip712_unsupported_network, signable.chainId.toString())
        }

        AWalletAlertDialog(activity, AWalletAlertDialog.ERROR).apply {
            setMessage(message)
            setButton(R.string.action_view_session) {
                openSessionDetail(session)
                cancelRequest(aCallback, signable)
                this.dismiss()
            }
            setSecondaryButton(R.string.action_cancel) {
                cancelRequest(aCallback, signable)
                this.dismiss()
            }
            setCancelable(false)
            show()
        }
    }

    private fun showNotSigning(aCallback: ActionSheetCallback, signRequest: BaseRequest, signable: Signable) {
        val pref = PreferenceManager.getDefaultSharedPreferences(activity)
        val hasDeveloperOverride = pref.getBoolean(DEVELOPER_OVERRIDE, false)

        AWalletAlertDialog(activity, AWalletAlertDialog.ERROR).apply {
            setMessage(activity.getString(R.string.override_warning_text))
            setButton(R.string.action_cancel) {
                cancelRequest(aCallback, signable)
                this.dismiss()
            }
            if (hasDeveloperOverride) {
                setSecondaryButton(R.string.override) {
                    showActionSheet(aCallback, signRequest, signable)
                    this.dismiss()
                }
            }
            setCancelable(false)
            show()
        }
    }

    private fun openSessionDetail(session: WalletConnectV2SessionItem) {
        val intent = Intent(activity, WalletConnectV2Activity::class.java).apply {
            putExtra("session", session)
        }
        activity.startActivity(intent)
    }

    private fun cancelRequest(aCallback: ActionSheetCallback, signable: Signable) {
        aCallback.dismissed("", signable.callbackId, false)
    }
}
