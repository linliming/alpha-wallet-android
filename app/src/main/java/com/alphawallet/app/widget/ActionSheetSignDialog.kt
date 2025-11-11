package com.alphawallet.app.widget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.util.Pair
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.alphawallet.app.R
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.SignDialogViewModel
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable
import com.bumptech.glide.Glide

/**
 * ActionSheetSignDialog 是一个底部弹窗对话框，用于处理各种类型的签名请求，
 * 例如普通消息签名、EIP-712 签名、个人签名以及 EIP-4361 (Sign-In with Ethereum)。
 *
 * 它负责：
 * - 显示签名请求的详细信息，包括发起方和签名数据。
 * - 根据钱包类型（软件钱包或硬件钱包）调整 UI 和交互逻辑。
 * - 处理用户授权流程，包括密码或生物识别认证。
 * - 与 ViewModel (SignDialogViewModel) 交互，执行签名操作并观察结果。
 * - 通过 ActionSheetCallback 将签名结果或用户操作（如取消）通知给调用方。
 */
class ActionSheetSignDialog(
    private val activity: Activity,
    private val actionSheetCallback: ActionSheetCallback,
    private val signable: Signable
) : ActionSheet(activity), StandardFunctionInterface, SignAuthenticationCallback {

    // 使用 lazy 延迟初始化 ViewModel，直到第一次访问时
    private val viewModel: SignDialogViewModel by lazy {
        ViewModelProvider(activity as ViewModelStoreOwner).get(SignDialogViewModel::class.java)
    }

    private val toolbar: BottomSheetToolbarView
    private val confirmationWidget: ConfirmationWidget
    private val requesterDetail: AddressDetailView
    private val addressDetail: AddressDetailView
    private val signWidget: SignDataWidget
    private val functionBar: FunctionButtonBar
    private val callbackId: Long = signable.callbackId

    private var walletType: WalletType? = null
    private var actionCompleted = false

    init {
        val view = View.inflate(activity, R.layout.dialog_action_sheet_sign, null)
        setContentView(view)

        // 使用 Kotlin Android Extensions 或 ViewBinding 的替代方案：findViewById
        toolbar = view.findViewById(R.id.bottom_sheet_toolbar)
        confirmationWidget = view.findViewById(R.id.confirmation_view)
        requesterDetail = view.findViewById(R.id.requester)
        addressDetail = view.findViewById(R.id.wallet)
        signWidget = view.findViewById(R.id.sign_widget)
        functionBar = view.findViewById(R.id.layoutButtons)

        // 观察 ViewModel 中的 LiveData
        viewModel.completed().observe(activity as LifecycleOwner, ::signComplete)
        viewModel.message().observe(activity as LifecycleOwner, ::onMessage)
        viewModel.onWallet().observe(activity as LifecycleOwner, ::onWallet)

        setCanceledOnTouchOutside(false)
    }

    /**
     * 当 Dialog 创建时调用，用于执行初始化操作。
     * 这里确保钱包设置已完成，然后才开始配置视图。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 确保钱包已准备好，以便后续流程可以获取正确的钱包信息
        viewModel.completeWalletSetup()
    }

    /**
     * 根据签名数据 `signable` 配置和初始化弹窗的各个 UI 组件。
     */
    private fun setupView() {
        requesterDetail.setupRequester(signable.origin)
        signWidget.setLockCallback(this)

        when {
            isEip4361(signable) -> {
                functionBar.setPrimaryButtonEnabled(false) // 初始禁用确认按钮，直到 EIP-4361 消息解析完成
                toolbar.setTitle(R.string.dialog_title_sign_in_with_ethereum)
                signWidget.setupSignData(signable) {
                    // 当签名数据（特别是EIP-4361消息）准备好后，启用主操作按钮
                    functionBar.setPrimaryButtonEnabled(true)
                }
            }

            signable.messageType == SignMessageType.SIGN_MESSAGE -> {
                toolbar.setTitle(Utils.getSignMessageTitle(context.getString(R.string.dialog_title_sign_message_sheet)))
                signWidget.setupSignData(signable)
            }

            else -> { // EIP-712 或个人签名
                toolbar.setTitle(Utils.getSigningTitle(signable))
                signWidget.setupSignData(signable)
            }
        }

        setupCancelListeners()
        actionCompleted = false

        // 确保视图完全展开，以防止在锁定滚动时内容被截断
        fullExpand()
    }

    /**
     * 检查给定的 `Signable` 是否为 EIP-4361 (Sign-In with Ethereum) 格式。
     * @param message 待检查的签名对象。
     * @return 如果是 EIP-4361 消息，则返回 true。
     */
    private fun isEip4361(message: Signable): Boolean {
        val userMsg = message.userMessage.toString()
        val domain = Utils.getDomainName(message.origin)
        // EIP-4361 消息体包含一个标准格式的句子
        return userMsg.contains("$domain wants you to sign in with your Ethereum account")
    }

    /**
     * 当 ViewModel 获取到当前钱包信息后调用此方法。
     * 根据钱包类型（硬件/软件）设置不同的 UI 和操作逻辑。
     * @param wallet 当前用户的钱包对象。
     */
    private fun onWallet(wallet: Wallet) {
        this.walletType = wallet.type
        if (walletType == WalletType.HARDWARE) {
            functionBar.setupFunctions(this, arrayListOf(R.string.use_hardware_card))
            functionBar.setClickable(false) // 等待硬件设备响应
            // 提示用户使用硬件钱包进行签名
            viewModel.getAuthentication(activity, this)
        } else {
            functionBar.setupFunctions(this, arrayListOf(R.string.action_confirm))
        }

        functionBar.revealButtons()
        setupView()
    }

    /**
     * 设置发起请求的 DApp 图标。
     * @param icon DApp 图标的 URL。
     */
    override fun setIcon(icon: String?) {
        val iconView = findViewById<ImageView>(R.id.logo)
        Glide.with(activity)
            .load(icon)
            .circleCrop() // 使用圆形裁剪
            .into(iconView?: return)
    }

    /**
     * 处理底部功能栏按钮的点击事件。
     * @param action 当前点击的按钮对应的操作文本（未使用）。
     * @param id 被点击按钮的资源 ID。
     */
    override fun handleClick(action: String?, actionId: Int) {
        if (walletType == WalletType.HARDWARE) {
            // TODO: 为硬件钱包用户弹出更明确的指示，例如“请将卡片贴近手机”
            return
        }

        // 对于软件钱包，点击“确认”后隐藏按钮并请求用户授权（密码/生物识别）
        functionBar.visibility = View.GONE
        viewModel.getAuthentication(activity, this)
    }

    /**
     * 为需要固定签名账户的场景（如 WalletConnect v2）设置签名钱包地址。
     * @param account 固定的钱包地址。
     */
    override fun setSigningWallet(account: String?) {
        viewModel.setSigningWallet(account)
        addressDetail.visibility = View.VISIBLE
        addressDetail.setupAddress(account, "", null)
    }

    /**
     * 在地址详情区域显示一条附带状态的消息。
     * @param res 一个包含消息字符串资源ID和颜色资源ID的 Pair。
     */
    private fun onMessage(res: Pair<Int, Int>?) {
        res?.let {
            addressDetail.addMessage(context.getString(res.first), res.second)
        }
    }

    /**
     * 当签名流程成功时调用，显示成功的动画并随后关闭弹窗。
     */ override fun success() {
        if (isShowing && confirmationWidget.isShown) {
            confirmationWidget.completeProgressMessage(".", this::dismiss)
        } else {
            dismiss() // 如果确认小部件不可见，直接关闭
        }
    }

    /**
     * 设置关闭和取消事件的监听器。
     */
    private fun setupCancelListeners() {
        toolbar.setCloseListener { dismiss() }

        setOnDismissListener {
            actionSheetCallback.dismissed("", callbackId, actionCompleted)
        }
    }

    /**
     * 来自 `SignAuthenticationCallback` 的回调，在用户完成身份验证后调用。
     * @param gotAuth 用户是否成功授权。
     */
    override fun gotAuthorisation(gotAuth: Boolean) {
        val signWidget = findViewById<SignDataWidget>(R.id.sign_widget)
        if (gotAuth) {
            if (walletType != WalletType.HARDWARE) {
                // 开始显示进度动画
                confirmationWidget.startProgressCycle(1)
                actionSheetCallback.notifyConfirm(ActionSheetMode.SIGN_MESSAGE.value)
            }
            // 请求 ViewModel 执行签名
            viewModel.signMessage(signWidget?.signable, actionSheetCallback)
        } else {
            // 授权失败
            Toast.makeText(activity, activity.getString(R.string.error_while_signing_transaction), Toast.LENGTH_SHORT).show()
            cancelAuthentication()
        }
    }

    /**
     * 来自 `SignAuthenticationCallback`，当用户取消授权时调用。
     */
    override fun cancelAuthentication() {
        dismiss()
    }

    /**
     * 来自 `SignAuthenticationCallback`，在硬件钱包返回签名数据后调用。
     * @param signature 包含签名结果或失败信息的对象。
     */
    override fun gotSignature(signature: SignatureFromKey?) {
        if (signature?.sigType == SignatureReturnType.SIGNATURE_GENERATED) {
            functionBar.visibility = View.GONE
            confirmationWidget.startProgressCycle(1)
            actionSheetCallback.notifyConfirm(ActionSheetMode.SIGN_MESSAGE.value)
            // 请求 ViewModel 完成签名流程
            viewModel.completeSignMessage(signature, actionSheetCallback)
        } else {
            // TODO: 以更友好的方式报告硬件钱包错误，而不是用 Toast
            activity.runOnUiThread {
                Toast.makeText(activity, "ERROR: ${signature?.failMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 观察 ViewModel 的签名完成状态。
     * @param success 签名流程是否成功。
     */
    private fun signComplete(success: Boolean) {
        if (success) {
            actionCompleted = true
            this.success() // 调用 success() 显示成功动画
        } else {
            dismiss() // 如果失败，直接关闭弹窗
        }
    }
}
