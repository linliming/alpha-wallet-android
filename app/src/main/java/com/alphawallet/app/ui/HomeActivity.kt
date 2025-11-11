package com.alphawallet.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.alphawallet.app.C
import com.alphawallet.app.C.ADDED_TOKEN
import com.alphawallet.app.C.RESET_WALLET
import com.alphawallet.app.C.SHOW_BACKUP
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.DeepLinkType
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.FragmentMessenger
import com.alphawallet.app.entity.HomeCommsInterface
import com.alphawallet.app.entity.HomeReceiver
import com.alphawallet.app.entity.MediaLinks
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletPage
import com.alphawallet.app.entity.WalletPage.ACTIVITY
import com.alphawallet.app.entity.WalletPage.SETTINGS
import com.alphawallet.app.entity.WalletPage.WALLET
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.attestation.AttestationImportInterface
import com.alphawallet.app.entity.attestation.SmartPassReturn
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.router.ImportTokenRouter
import com.alphawallet.app.service.DeepLinkService
import com.alphawallet.app.service.PriceAlertsService
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.ui.widget.entity.PagerCallback
import com.alphawallet.app.util.LocaleUtils
import com.alphawallet.app.util.UpdateUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.BaseNavigationActivity
import com.alphawallet.app.viewmodel.HomeViewModel
import com.alphawallet.app.viewmodel.WalletConnectViewModel
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.AWalletConfirmationDialog
import com.alphawallet.app.widget.SignTransactionDialog
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.Signable
import com.github.florent37.tutoshowcase.TutoShowcase
import com.journeyapps.barcodescanner.ScanContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * [HomeActivity] 是应用的主活动，作为导航中心承载了四个主要的 Fragment：
 * [WalletFragment], [ActivityFragment], [DappBrowserFragment], 和 [NewSettingsFragment]。
 *
 * 它管理以下功能：
 * - 底部导航栏和 ViewPager2 的同步。
 * - Fragment 之间的通信（通过 [FragmentMessenger] 和 [androidx.fragment.app.FragmentResultListener]）。
 * - 处理深度链接 (Deep Links) 和 URI Intent。
 * - 管理 ActivityResult（例如相机、权限、PIN 码验证）。
 * - WalletConnect (WC) 的回调和签名流程。
 * - 显示全局对话框（如备份提示、错误信息、成功覆盖层）。
 * - 监听应用的前后台状态 [LifecycleEventObserver]。
 * - 处理权限请求。
 * - 作为多个接口（如 [HomeCommsInterface], [AttestationImportInterface]）的回调实现者。
 */
@AndroidEntryPoint
class HomeActivity : BaseNavigationActivity(), View.OnClickListener, HomeCommsInterface,
    FragmentMessenger, ActionSheetCallback, LifecycleEventObserver, PagerCallback, AttestationImportInterface {
    @Inject
    lateinit var awWalletConnectClient: AWWalletConnectClient

    /**
     * ViewPager2 的 Fragment 适配器。
     */
    private val pager2Adapter: FragmentStateAdapter = ScreenSlidePagerAdapter(this)

    /**
     * 用于 UI 操作的主线程 Handler。
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 处理从网络设置页返回的结果，触发代币服务重置。
     */
    private val networkSettingsHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        supportFragmentManager.setFragmentResult(RESET_TOKEN_SERVICE, Bundle())
    }

    private lateinit var viewModel: HomeViewModel
    private lateinit var viewModelWC: WalletConnectViewModel

    /**
     * 用于显示通用对话框（如错误、确认）。
     */
    private var dialog: Dialog? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var successOverlay: LinearLayout
    private lateinit var successImage: ImageView

    /**
     * 广播接收器，用于处理系统广播（例如钱包变更）。
     */
    private var homeReceiver: HomeReceiver? = null

    /**
     * 当前钱包的标题（名称）。
     */
    private var walletTitle: String = ""

    /**
     * 用于显示 "备份钱包" 教程提示的对话框。
     */
    private var backupWalletDialog: TutoShowcase? = null

    /**
     * 标记应用是否处于前台。
     */
    private var isForeground: Boolean = false

    /**
     * 标记代币是否被点击（用于防止在查看代币时停止交易更新）。
     */
    @Volatile
    private var tokenClicked: Boolean = false // Volatile 保持，因为它在不同线程中被访问

    /**
     * 存储从 Intent 传入的、需要延迟打开的 URL。
     */
    private var openLink: String? = null

    /**
     * 存储从 Intent 传入的、需要延迟导入的代币文件。
     */
    private var openToken: String? = null

    /**
     * WalletConnect 进度对话框。
     */
    private var wcProgressDialog: AWalletAlertDialog? = null

    /**
     * 处理通知权限请求的结果。
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // FCM SDK (和应用) 可以发送通知。
                lifecycleScope.launch {
                    viewModel.subscribeToNotifications()
                }
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.message_deny_request_post_notifications_permission),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /**
     * 用于隐藏成功提示（绿色对勾）的 Runnable。
     * 使用独立的 Runnable 实例以避免 Activity 泄漏。
     */
    private val successOverlayRunnable: Runnable = object : Runnable {
        override fun run() {
            if (successOverlay.alpha > 0) {
                successOverlay.animate().alpha(0.0f).setDuration(500)
                handler.postDelayed(this, 750)
            } else {
                successOverlay.visibility = View.GONE
                successOverlay.alpha = 1.0f
            }
        }
    }

    /**
     * 启动二维码扫描仪的 ActivityResultLauncher。
     */
    private val qrCodeScanner = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
        } else if (viewModel.requiresProcessing(result.contents)) {
            viewModel.handleQRCode(this, result.contents)
        }
    }

    /**
     * 用于气体费用设置页的 ActivityResultLauncher。
     */
    var getGasSettings: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // TODO: awWalletConnectClient.setCurrentGasIndex(result)
        }

    /**
     * 伴生对象，存储静态常量。
     */
    companion object {
        const val RC_ASSET_EXTERNAL_WRITE_PERM = 223
        const val RC_ASSET_NOTIFICATION_PERM = 224
        const val DAPP_BARCODE_READER_REQUEST_CODE = 1
        const val STORED_PAGE = "currentPage"
        const val RESET_TOKEN_SERVICE = "HOME_reset_ts"
        const val AW_MAGICLINK_DIRECT = "openurl?url="

        private var updatePrompt = false

        /**
         * 设置一个标记，提示用户有应用更新可用。
         */
        @JvmStatic
        fun setUpdatePrompt() {
            // TODO: 定期检查此值 (例如在页面切换时)
            // 设置提醒用户更新应用的警报
            updatePrompt = true
        }
    }

    /**
     * [LifecycleEventObserver] 的实现，用于监听 Activity 生命周期事件。
     *
     * @param source 生命周期所有者。
     * @param event 发生的生命周期事件。
     */
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.tag("LIFE").d("AlphaWallet into foreground")
                // 5秒后检查交易引擎
                handler.postDelayed({
                    viewModel.checkTransactionEngine()
                }, TimeUnit.SECONDS.toMillis(5))
                isForeground = true
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.tag("LIFE").d("AlphaWallet into background")
                if (!tokenClicked) viewModel.stopTransactionUpdate()
                viewModel.outOfFocus()
                isForeground = false
            }

            Lifecycle.Event.ON_DESTROY -> {
                // 清理 handler 中的回调
                handler.removeCallbacksAndMessages(null)
            }

            else -> {} // 其他事件（ON_CREATE, ON_RESUME, ON_PAUSE, ON_ANY）不做特殊处理
        }
    }

    /**
     * 附加基础上下文（Context）。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    /**
     * 当窗口焦点改变时调用，用于控制系统 UI（全屏/非全屏）。
     *
     * @param hasFocus 窗口是否获得焦点。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (viewModel.fullScreenSelected()) {
                hideSystemUI()
            } else {
                showSystemUI()
            }
        }
    }

    /**
     * Activity 创建时调用。
     *
     * @param savedInstanceState 保存的实例状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleUtils.setDeviceLocale(baseContext)
        super.onCreate(savedInstanceState)
        LocaleUtils.setActiveLocale(this)
        lifecycle.addObserver(this)
        isForeground = true
        setWCConnect()

        supportActionBar?.hide()

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModelWC = ViewModelProvider(this)[WalletConnectViewModel::class.java]

        viewModel.identify()
        viewModel.setWalletStartup()
        viewModel.setCurrencyAndLocale(this)
        viewModel.tryToShowWhatsNewDialog(this)
        setContentView(R.layout.activity_home)

        initViews()
        toolbar()

        viewPager = findViewById(R.id.view_pager)
        viewPager.isUserInputEnabled = false // 禁用滑动切换
        viewPager.adapter = pager2Adapter
        viewPager.offscreenPageLimit = WalletPage.values().size // 缓存所有页面
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            // ViewPager 回调（当前未使用）
        })

        initBottomNavigation()
        disableDisplayHomeAsUp()

        // 观察 ViewModel 的 LiveData
        viewModel.error().observe(this, this::onError)
        viewModel.walletName().observe(this, this::onWalletName)
        viewModel.backUpMessage().observe(this, this::onBackup)
        viewModel.splashReset().observe(this, this::onRequireInit)
        viewModel.defaultWallet().observe(this, this::onDefaultWallet)
        viewModel.updateAvailable().observe(this, this::onUpdateAvailable)

        if (CustomViewSettings.hideDappBrowser()) {
            removeDappBrowser()
        }

        // 监听软键盘可见性
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (isOpen) {
                setNavBarVisibility(View.GONE)
                getFragment(WalletPage.values()[viewPager.currentItem]).softKeyboardVisible()
            } else {
                setNavBarVisibility(View.VISIBLE)
                getFragment(WalletPage.values()[viewPager.currentItem]).softKeyboardGone()
            }
        }

        viewModel.tryToShowRateAppDialog(this)
        viewModel.tryToShowEmailPrompt(this, successOverlay, handler, successOverlayRunnable)

        // 检查应用更新
        if (Utils.verifyInstallerId(this)) {
            UpdateUtils.checkForUpdates(this, this)
        } else {
            if (MediaLinks.isMediaTargeted(applicationContext)) {
                viewModel.checkLatestGithubRelease()
            }
        }

        setupFragmentListeners()

        // 处理启动 Intent 和深度链接
        val data = intent.data
        if (intent.hasExtra(C.FROM_HOME_ROUTER) && intent.getStringExtra(C.FROM_HOME_ROUTER) == C.FROM_HOME_ROUTER) {
            viewModel.storeCurrentFragmentId(-1)
        }

        if (data != null) {
            handleDeeplink(data.toString(), intent)
        }

        // 启动价格提醒服务
        val i = Intent(this, PriceAlertsService::class.java)
        try {
            startService(i) // 应用在后台时可能启动失败
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    /**
     * 当 ViewModel 检测到有可用的应用更新时调用。
     *
     * @param availableVersion 可用更新的版本号。
     */
    private fun onUpdateAvailable(availableVersion: String) {
        externalUpdateReady(availableVersion)
    }

    /**
     * 设置 WalletConnect 客户端的回调。
     */
    private fun setWCConnect() {
        try {
            awWalletConnectClient.setCallback(this)
        } catch (e: Exception) {
            Timber.tag("WalletConnect").e(e)
        }
    }

    /**
     * 当默认钱包加载或变更时调用。
     *
     * @param wallet 默认钱包实例。
     */
    private fun onDefaultWallet(wallet: Wallet) {
        // TODO: [通知] 后端服务实现后取消注释
//        if (!viewModel.isWatchOnlyWallet() && !viewModel.isPostNotificationsPermissionRequested(wallet.address))
//        {
//            viewModel.setPostNotificationsPermissionRequested(wallet.address)
//            if (PermissionUtils.requestPostNotificationsPermission(this, requestPermissionLauncher))
//            {
//                viewModel.subscribeToNotifications()
//            }
//        }

        // 如果是新钱包，引导用户选择网络
        if (viewModel.checkNewWallet(wallet.address)) {
            viewModel.setNewWallet(wallet.address, false)
            val selectNetworkIntent = Intent(this, NetworkToggleActivity::class.java)
            selectNetworkIntent.putExtra(C.EXTRA_SINGLE_ITEM, false)
            networkSettingsHandler.launch(selectNetworkIntent)
        }
    }

    /**
     * 设置 Fragment 结果监听器，用于 Fragment 之间的通信。
     */
    private fun setupFragmentListeners() {
        // 监听代币服务重置请求
        supportFragmentManager.setFragmentResultListener(RESET_TOKEN_SERVICE, this) { _, _ ->
            viewModel.restartTokensService()
            resetTokens() // 触发钱包适配器重置
        }

        // 监听钱包重置请求
        supportFragmentManager.setFragmentResultListener(C.RESET_WALLET, this) { _, _ ->
            viewModel.restartTokensService()
            resetTokens()
            showPage(WalletPage.WALLET)
        }

        // 监听货币单位变更请求
        supportFragmentManager.setFragmentResultListener(C.CHANGE_CURRENCY, this) { _, _ ->
            resetTokens()
            showPage(WalletPage.WALLET)
        }

        // 监听工具栏重置请求
        supportFragmentManager.setFragmentResultListener(C.RESET_TOOLBAR, this) { _, _ ->
            invalidateOptionsMenu()
        }

        // 监听添加代币事件
        supportFragmentManager.setFragmentResultListener(C.ADDED_TOKEN, this) { _, b ->
            val contractList = b.getParcelableArrayList<ContractLocator>(ADDED_TOKEN)
            contractList?.let {
                getFragment(WalletPage.ACTIVITY).addedToken(it)
            }
        }

        // 监听显示备份提示请求
        supportFragmentManager.setFragmentResultListener(C.SHOW_BACKUP, this) { _, b ->
            showBackupWalletDialog(b.getBoolean(SHOW_BACKUP, false))
        }

        // 监听处理备份结果
        supportFragmentManager.setFragmentResultListener(C.HANDLE_BACKUP, this) { _, b ->
            if (b.getBoolean(C.HANDLE_BACKUP)) {
                backupWalletSuccess(b.getString("Key"))
            } else {
                backupWalletFail(b.getString("Key"), b.getBoolean("nolock"))
            }
        }

        // 监听代币点击事件
        supportFragmentManager.setFragmentResultListener(C.TOKEN_CLICK, this) { _, _ ->
            tokenClicked = true
            handler.postDelayed({ tokenClicked = false }, 10000)
        }

        // 监听语言环境变更
        supportFragmentManager.setFragmentResultListener(C.CHANGED_LOCALE, this) { _, _ ->
            viewModel.restartHomeActivity(applicationContext)
        }

        // 监听设置页面实例化完成
        supportFragmentManager.setFragmentResultListener(C.SETTINGS_INSTANTIATED, this) { _, _ ->
            loadingComplete()
        }

        // 监听二维码扫描请求
        supportFragmentManager.setFragmentResultListener(C.QRCODE_SCAN, this) { _, _ ->
            val options = Utils.getQRScanOptions(this)
            hideDialog()
            qrCodeScanner.launch(options)
        }

        // 监听内部二维码扫描（例如来自 DApp 浏览器）
        supportFragmentManager.setFragmentResultListener(C.AWALLET_CODE, this) { _, b ->
            val code = b.getString(C.AWALLET_CODE)
            code?.let { handleDeeplink(it, null) }
        }
    }

    /**
     * 当 Activity 收到新的 Intent 时调用（例如，通过深度链接启动已在运行的 Activity）。
     *
     * @param startIntent 新的 Intent。
     */
    override fun onNewIntent(startIntent: Intent) {
        super.onNewIntent(startIntent)
        startIntent.data?.let {
            handleDeeplink(it.toString(), startIntent)
        }
    }

    /**
     * 当需要初始化钱包时调用（例如首次启动）。
     *
     * @param aBoolean 是否需要初始化。
     */
    private fun onRequireInit(boolean: Boolean) {
        val intent = Intent(this, SplashActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * 当收到备份提示消息时调用。
     *
     * @param address 需要备份的钱包地址。
     */
    private fun onBackup(address: String) {
        if (Utils.isAddressValid(address)) {
            Toast.makeText(this, getString(R.string.postponed_backup_warning), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 初始化视图组件。
     */
    private fun initViews() {
        successOverlay = findViewById(R.id.layout_success_overlay)
        successImage = findViewById(R.id.success_image)

        successOverlay.setOnClickListener {
            // 点击隐藏绿色对勾提示
            successOverlay.visibility = View.GONE
        }
    }

    /**
     * 显示 "备份钱包" 的引导提示。
     *
     * @param walletImported 钱包是否是导入的（导入的钱包不需要备份提示）。
     */
    private fun showBackupWalletDialog(walletImported: Boolean) {
        if (viewModel.isFindWalletAddressDialogShown) {
            if (!walletImported) {
                val background = ContextCompat.getColor(applicationContext, R.color.translucent_dark)
                val statusBarColor = window.statusBarColor

                backupWalletDialog = TutoShowcase.from(this).apply {
                    setContentView(R.layout.showcase_backup_wallet)
                    setBackgroundColor(background)
                    onClickContentView(R.id.btn_close) {
                        window.statusBarColor = statusBarColor
                        dismiss() // 'this.dismiss()' (TutoShowcase 实例)
                    }
                    onClickContentView(R.id.showcase_layout) {
                        window.statusBarColor = statusBarColor
                        dismiss()
                    }
                    // .on() 返回一个编辑器，但 apply 仍会返回 TutoShowcase
                    on(R.id.settings_tab)
                        .addCircle()
                        .onClick {
                            window.statusBarColor = statusBarColor
                            dismiss()
                            showPage(WalletPage.SETTINGS)
                        }
                }
                // 现在 backupWalletDialog 被正确赋值为 TutoShowcase? 类型
                backupWalletDialog?.show()
                window.statusBarColor = background
            }
            viewModel.isFindWalletAddressDialogShown = true
        }
    }

    /**
     * 当钱包名称更新时调用。
     *
     * @param name 新的钱包名称。
     */
    private fun onWalletName(name: String?) {
        walletTitle = if (!name.isNullOrEmpty()) {
            name
        } else {
            getString(R.string.toolbar_header_wallet)
        }
        getFragment(WalletPage.WALLET).setToolbarTitle(walletTitle)
    }

    /**
     * 当 ViewModel 报告错误时调用。
     *
     * @param errorEnvelope 错误信息封套。
     */
    private fun onError(errorEnvelope: ErrorEnvelope?) {
        // TODO: 实现错误处理
    }

    /**
     * Activity 恢复时调用。
     */
    @SuppressLint("RestrictedApi")
    override fun onResume() {
        super.onResume()
        setWCConnect()
        viewModel.prepare()
        viewModel.getWalletName(this)
        viewModel.setErrorCallback(this)
        if (homeReceiver == null) {
            homeReceiver = HomeReceiver(this, this)
            homeReceiver?.register(this)
        }
        initViews() // 重新初始化视图引用（以防万一）
    }

    /**
     * Activity 暂停时调用。
     */
    override fun onPause() {
        super.onPause()
        dialog?.dismiss()
    }

    /**
     * 保存实例状态。
     *
     * @param savedInstanceState 用于保存状态的 Bundle。
     */
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt(STORED_PAGE, viewPager.currentItem)
        selectedItem?.let {
            viewModel.storeCurrentFragmentId(it.ordinal)
        }
    }

    /**
     * 恢复实例状态。
     *
     * @param savedInstanceState 包含已保存状态的 Bundle。
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val oldPage = savedInstanceState.getInt(STORED_PAGE)
        if (oldPage >= 0 && oldPage < WalletPage.values().size) {
            showPage(WalletPage.values()[oldPage])
        }
    }

    /**
     * [View.OnClickListener] 的实现（当前未使用）。
     */
    override fun onClick(view: View) {
        // 未使用
    }

    /**
     * [BaseNavigationActivity] 的回调，当底部导航项被选中时调用。
     *
     * @param index 被选中的页面 [WalletPage]。
     * @return true 表示已处理该事件。
     */
    override fun onBottomNavigationItemSelected(index: WalletPage): Boolean {
        return when (index) {
            WalletPage.DAPP_BROWSER -> {
                showPage(WalletPage.DAPP_BROWSER)
                true
            }

            WALLET -> {
                showPage(WalletPage.WALLET)
                true
            }

            SETTINGS -> {
                showPage(WalletPage.SETTINGS)
                true
            }

            ACTIVITY -> {
                showPage(WalletPage.ACTIVITY)
                true
            }
        }
    }

    /**
     * 切换到 DApp 浏览器并加载指定的 URL。
     *
     * @param url 要加载的 URL。
     */
    fun onBrowserWithURL(url: String) {
        showPage(WalletPage.DAPP_BROWSER)
        getFragment(WalletPage.DAPP_BROWSER).onItemClick(url)
    }

    /**
     * Activity 销毁时调用。
     */
    override fun onDestroy() {
        selectedItem?.let { viewModel.storeCurrentFragmentId(it.ordinal) }
        super.onDestroy()
        viewModel.onClean()
        homeReceiver?.unregister(this)
        homeReceiver = null
    }

    /**
     * [HomeCommsInterface] 的实现，获取 Gas 服务。
     */
    override val gasService = viewModel.getGasService()

    /**
     * 切换 ViewPager2 到指定的页面，并更新工具栏和导航栏状态。
     *
     * @param page 要显示的页面 [WalletPage]。
     */
    private fun showPage(page: WalletPage) {
        val oldPage = WalletPage.values()[viewPager.currentItem]
        var enableDisplayAsHome = false
        var targetPage = page

        when (targetPage) {
            WalletPage.DAPP_BROWSER -> {
                hideToolbar()
                title = getString(R.string.toolbar_header_browser)
                selectNavigationItem(WalletPage.DAPP_BROWSER)
                enableDisplayAsHome = true
            }

            WALLET -> {
                showToolbar()
                title = if (walletTitle.isEmpty()) getString(R.string.toolbar_header_wallet) else walletTitle
                selectNavigationItem(WalletPage.WALLET)
            }

            SETTINGS -> {
                showToolbar()
                title = getString(R.string.toolbar_header_settings)
                selectNavigationItem(WalletPage.SETTINGS)
            }

            ACTIVITY -> {
                showToolbar()
                title = getString(R.string.activity_label)
                selectNavigationItem(WalletPage.ACTIVITY)
            }

            else -> {
                // 默认情况（例如，如果 DAPP_BROWSER 被禁用）
                targetPage = WalletPage.WALLET
                showToolbar()
                title = if (walletTitle.isEmpty()) getString(R.string.toolbar_header_wallet) else walletTitle
                selectNavigationItem(WalletPage.WALLET)
            }
        }

        enableDisplayHomeAsHome(enableDisplayAsHome)
        switchAdapterToPage(targetPage)
        invalidateOptionsMenu()
        checkWarnings()

        signalPageVisibilityChange(oldPage, targetPage)
    }

    /**
     * 在主线程切换 ViewPager2 的当前页面。
     *
     * @param page 目标页面。
     */
    private fun switchAdapterToPage(page: WalletPage) {
        handler.post { viewPager.setCurrentItem(page.ordinal, false) }
    }

    /**
     * 通知 Fragment 它们正在进入或离开焦点。
     *
     * @param oldPage 离开焦点的页面。
     * @param newPage 进入焦点的页面。
     */
    private fun signalPageVisibilityChange(oldPage: WalletPage, newPage: WalletPage) {
        getFragment(newPage).comeIntoFocus()

        if (oldPage != newPage) {
            getFragment(oldPage).leaveFocus()
        }
    }

    /**
     * 检查并显示应用更新警告（如果需要）。
     */
    private fun checkWarnings() {
        if (updatePrompt) {
            hideDialog()
            updatePrompt = false
            var warns = viewModel.updateWarnings + 1
            if (warns < 3) {
                val cDialog = AWalletConfirmationDialog(this).apply {
                    setTitle(R.string.alphawallet_update)
                    setCancelable(true)
                    setSmallText("Using an old version of Alphawallet. Please update from the Play Store or Alphawallet website.")
                    setPrimaryButtonText(R.string.ok)
                    setPrimaryButtonListener { this@apply.dismiss() }
                }
                dialog = cDialog
                cDialog.show()
            } else if (warns > 10) {
                warns = 0
            }
            viewModel.setUpdateWarningCount(warns)
        }
    }

    /**
     * [UpdateUtils.UpdateCallback] 的实现：Google Play 商店更新准备就绪。
     *
     * @param updateVersion 更新版本号。
     */
    override fun playStoreUpdateReady(updateVersion: Int) {
        getFragment(WalletPage.SETTINGS).signalPlayStoreUpdate(updateVersion)
    }

    /**
     * [UpdateUtils.UpdateCallback] 的实现：外部（如 Github）更新准备就绪。
     *
     * @param version 更新版本号。
     */
    override fun externalUpdateReady(version: String?) {
        getFragment(WalletPage.SETTINGS).signalExternalUpdate(version)
    }

    /**
     * [HomeCommsInterface] 的实现：显示 TokenScript 错误。
     *
     * @param message 错误消息。
     */
    override fun tokenScriptError(message: String?) {
        handler.removeCallbacksAndMessages(null) // 移除之前的错误调用
        handler.postDelayed({
            hideDialog()
            val aDialog = AWalletAlertDialog(this).apply {
                setTitle(getString(R.string.tokenscript_file_error))
                setMessage(message)
                setIcon(AWalletAlertDialog.ERROR)
                setButtonText(R.string.button_ok)
                setButtonListener { this@apply.dismiss() }
            }
            dialog = aDialog
            aDialog.show()
        }, 500)
    }

    /**
     * 钱包备份失败（或推迟）时调用。
     *
     * @param keyBackup 钱包地址。
     * @param hasNoLock 是否没有锁屏。
     */
    fun backupWalletFail(keyBackup: String?, hasNoLock: Boolean) {
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(hasNoLock)
        keyBackup?.let {
            getFragment(WalletPage.WALLET).remindMeLater(Wallet(it))
            viewModel.checkIsBackedUp(it)
        }
    }

    /**
     * 钱包备份成功时调用。
     *
     * @param keyBackup 钱包地址。
     */
    fun backupWalletSuccess(keyBackup: String?) {
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(false)
        keyBackup?.let { getFragment(WalletPage.WALLET).storeWalletBackupTime(it) }
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
        successImage.setImageResource(R.drawable.big_green_tick)
        successOverlay.visibility = View.VISIBLE
        handler.postDelayed(successOverlayRunnable, 1000)
    }

    /**
     * [Runnable] 的实现（已废弃，由 [successOverlayRunnable] 替代）。
     */
    fun run() {
        // 此方法已废弃，逻辑移至 successOverlayRunnable
        // 保留此空方法以符合 HomeActivity, Runnable 接口（如果它仍在 Java 侧被引用）
        // 最佳实践是从 'implements Runnable' 中移除 HomeActivity
    }

    /**
     * [FragmentMessenger] 的实现：当 Fragment 加载完成时调用。
     */
    override fun loadingComplete() {
        val lastId = viewModel.lastFragmentId
        when {
            !openLink.isNullOrEmpty() -> {
                // 处理延迟的深度链接 URL
                showPage(WalletPage.DAPP_BROWSER)
                getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(0, openLink!!)
                openLink = null
                viewModel.storeCurrentFragmentId(-1)
            }

            !openToken.isNullOrEmpty() -> {
                // 处理延迟的代币导入
                showPage(WalletPage.WALLET)
                getFragment(WalletPage.WALLET).setImportFilename(openToken!!)
                openToken = null
            }

            intent.getBooleanExtra(C.Key.FROM_SETTINGS, false) -> {
                showPage(WalletPage.SETTINGS)
            }

            lastId >= 0 && lastId < WalletPage.values().size -> {
                showPage(WalletPage.values()[lastId])
                viewModel.storeCurrentFragmentId(-1)
            }

            else -> {
                showPage(WalletPage.WALLET)
                getFragment(WalletPage.WALLET).comeIntoFocus()
            }
        }
    }

    /**
     * 按页面获取 [BaseFragment] 实例。
     * 注意：这依赖于 ViewPager2 的内部实现，即使用 "f" + position 作为标签。
     *
     * @param page 要获取的页面 [WalletPage]。
     * @return 对应的 [BaseFragment]，如果未找到则是一个虚拟的 BaseFragment（并可能触发 activity 重建）。
     */
    private fun getFragment(page: WalletPage): BaseFragment {
        val tag = "f${page.ordinal}"
        return (supportFragmentManager.findFragmentByTag(tag) as? BaseFragment) ?: run {
            // 原始代码在找不到 fragment 时会调用 recreate()。
            // 我们保留这个逻辑，因为它似乎是原始作者处理 fragment 状态丢失的方式。
            Timber.e("Fragment not found for tag: $tag. This might be a lifecycle issue. Recreating activity as a fallback.")
            if (!isFinishing && !isDestroyed) {
                try {
                    recreate()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to recreate activity.")
                }
            }
            // 返回一个临时的、无害的实例，以防止在 recreate() 生效前发生崩溃。
            BaseFragment()
        }
    }

    /**
     * [HomeCommsInterface] 的实现：请求通知权限。
     */
    override fun requestNotificationPermission() {
        checkNotificationPermission(RC_ASSET_NOTIFICATION_PERM)
    }

    /**
     * [HomeCommsInterface] 的实现：备份成功回调。
     *
     * @param keyAddress 备份的钱包地址。
     */
    override fun backupSuccess(keyAddress: String?) {
        if (Utils.isAddressValid(keyAddress)) backupWalletSuccess(keyAddress)
    }

    /**
     * [HomeCommsInterface] 的实现：重置代币列表。
     */
    override fun resetTokens() {
        getFragment(ACTIVITY).resetTokens()
        getFragment(WALLET).resetTokens()
    }

    /**
     * [HomeCommsInterface] 的实现：重置交易列表。
     */
    override fun resetTransactions() {
        getFragment(ACTIVITY).resetTransactions()
    }

    /**
     * 隐藏当前显示的对话框。
     */
    private fun hideDialog() {
        dialog?.dismiss()
        wcProgressDialog?.dismiss()
    }

    /**
     * 检查并请求通知权限。
     *
     * @param permissionTag 请求代码。
     */
    private fun checkNotificationPermission(permissionTag: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), permissionTag)
            }
        }
        // 对于 Android 13 以下版本，不需要特殊权限。
    }

    /**
     * 处理权限请求的结果。
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            DappBrowserFragment.REQUEST_CAMERA_ACCESS ->
                getFragment(WalletPage.DAPP_BROWSER).gotCameraAccess(permissions, grantResults)

            DappBrowserFragment.REQUEST_FILE_ACCESS ->
                getFragment(WalletPage.DAPP_BROWSER).gotFileAccess(permissions, grantResults)

            DappBrowserFragment.REQUEST_FINE_LOCATION ->
                getFragment(WalletPage.DAPP_BROWSER).gotGeoAccess(permissions, grantResults)

            RC_ASSET_EXTERNAL_WRITE_PERM -> {
                // 无法到达这里
            }

            RC_ASSET_NOTIFICATION_PERM -> {
                // 显示导入通知
            }
        }
    }

    /**
     * 处理从其他 Activity 返回的结果。
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        var mutableRequestCode = requestCode

        // 统一处理 PIN/凭证 验证请求码
        if (mutableRequestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS &&
            mutableRequestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10
        ) {
            mutableRequestCode = SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS
        }

        when (mutableRequestCode) {
            DAPP_BARCODE_READER_REQUEST_CODE -> {
                getFragment(WalletPage.DAPP_BROWSER).handleQRCode(resultCode, data, this)
            }

            SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> {
                if (selectedItem == WalletPage.DAPP_BROWSER) {
                    getFragment(WalletPage.DAPP_BROWSER).pinAuthorisation(resultCode == Activity.RESULT_OK)
                }
            }

            C.REQUEST_BACKUP_WALLET -> {
                val keyBackup = data?.getStringExtra("Key")
                val noLockScreen = data?.getBooleanExtra("nolock", false) ?: false
                if (resultCode == Activity.RESULT_OK) {
                    backupWalletSuccess(keyBackup)
                } else {
                    backupWalletFail(keyBackup, noLockScreen)
                }
            }

            C.REQUEST_UNIVERSAL_SCAN -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    when {
                        data.hasExtra(C.EXTRA_QR_CODE) -> {
                            val qrCode = data.getStringExtra(C.EXTRA_QR_CODE)
                            qrCode?.let { viewModel.handleQRCode(this, it) }
                        }

                        data.hasExtra(C.EXTRA_ACTION_NAME) -> {
                            val action = data.getStringExtra(C.EXTRA_ACTION_NAME)
                            if (action.equals(C.ACTION_MY_ADDRESS_SCREEN, ignoreCase = true)) {
                                viewModel.showMyAddress(this)
                            }
                        }
                    }
                }
            }

            C.TOKEN_SEND_ACTIVITY -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    when {
                        data.hasExtra(C.DAPP_URL_LOAD) -> {
                            getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(
                                data.getLongExtra(C.EXTRA_CHAIN_ID, MAINNET_ID),
                                data.getStringExtra(C.DAPP_URL_LOAD)!!
                            )
                            showPage(WalletPage.DAPP_BROWSER)
                        }

                        data.hasExtra(C.EXTRA_TXHASH) -> {
                            showPage(ACTIVITY)
                        }
                    }
                }
            }

            C.TERMINATE_ACTIVITY -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    getFragment(ACTIVITY).scrollToTop()
                    showPage(ACTIVITY)
                }
            }

            C.ADDED_TOKEN_RETURN -> {
                if (data != null && data.hasExtra(C.EXTRA_TOKENID_LIST)) {
                    val tokenData: MutableList<ContractLocator?>? = data.getParcelableArrayListExtra(C.EXTRA_TOKENID_LIST)
                    tokenData?.let {
                        getFragment(WalletPage.ACTIVITY).addedToken(it)
                    }
                } else if (data != null && data.getBooleanExtra(RESET_WALLET, false)) {
                    viewModel.restartTokensService()
                    resetTokens()
                }
            }

            else -> {
                // 无操作
            }
        }
    }

    /**
     * 推迟钱包备份警告。
     *
     * @param walletAddress 钱包地址。
     */
    fun postponeWalletBackupWarning(walletAddress: String) {
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
    }

    /**
     * 处理返回按钮事件。
     */
    override fun onBackPressed() {
        if (viewPager.currentItem == WalletPage.DAPP_BROWSER.ordinal) {
            getFragment(WalletPage.DAPP_BROWSER).backPressed()
        } else if (viewPager.currentItem != WalletPage.WALLET.ordinal && isNavBarVisible) {
            showPage(WalletPage.WALLET)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 隐藏系统 UI（状态栏和导航栏）以实现全屏。
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        inset.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    /**
     * 显示系统 UI。
     */
    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    /**
     * 解析和处理深度链接（Deep Link）或 Intent 数据。
     *
     * @param importData 传入的数据字符串（URI）。
     * @param startIntent 启动 Intent（可能为 null）。
     */
    private fun handleDeeplink(importData: String, startIntent: Intent?) {
        val request = DeepLinkService.parseIntent(importData, startIntent)
        val  requestData = request.data ?: ""
        when (request.type) {
            DeepLinkType.WALLETCONNECT -> {
                if (requestData.contains("relay-protocol")) {
                    val intent = Intent(this, WalletConnectV2Activity::class.java)
                    intent.putExtra("url", requestData)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                } else {
                    walletConnectRequestPending()
                }
            }

            DeepLinkType.SMARTPASS
                -> {
                viewModel.handleSmartPass(this, requestData)
            }

            DeepLinkType.URL_REDIRECT -> {
                viewModel.track(Analytics.Action.DEEP_LINK)
                if (fragmentsInitialised()) {
                    showPage(WalletPage.DAPP_BROWSER)
                    getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(0, requestData)
                } else {
                    openLink = requestData // 延迟打开
                }
            }

            DeepLinkType.TOKEN_NOTIFICATION -> {
                if (fragmentsInitialised()) {
                    showPage(WalletPage.WALLET)
                    getFragment(WalletPage.WALLET).setImportFilename(requestData)
                } else {
                    openToken = requestData // 延迟打开
                }
            }

            DeepLinkType.WALLET_API_DEEPLINK -> {
                val intent = Intent(this, ApiV1Activity::class.java)
                intent.putExtra(C.Key.API_V1_REQUEST_URL, requestData)
                viewModel.track(Analytics.Action.DEEP_LINK_API_V1)
                startActivity(intent)
            }

            DeepLinkType.LEGACY_MAGICLINK -> {
                ImportTokenRouter().open(this, requestData)
                finish()
            }

            DeepLinkType.IMPORT_SCRIPT -> {
                startIntent?.let { viewModel.importScriptFile(this, it) }
            }

            DeepLinkType.INVALID_LINK -> {
                // 无操作
            }
        }
    }

    /**
     * [ActionSheetCallback] 的实现：签名完成。
     *
     * @param signature 签名结果。
     * @param message 被签名的消息。
     */
    override fun signingComplete(signature: SignatureFromKey, message: Signable) {
        Timber.d("Initial Msg: %s", message.message)
        awWalletConnectClient.signComplete(signature, message)
    }

    /**
     * [ActionSheetCallback] 的实现：签名失败。
     *
     * @param error 错误。
     * @param message 试图签名的消息。
     */
    override fun signingFailed(error: Throwable, message: Signable) {
        error.message?.let { awWalletConnectClient.signFail(it, message) }
    }

    /**
     * [ActionSheetCallback] 的实现：获取钱包类型。
     */
    override val walletType: WalletType
        // !!. 假设 defaultWallet() 的 value 绝不为 null，因为 HomeActivity 依赖它
        get() = viewModel.defaultWallet().value?.type ?: WalletType.NOT_DEFINED

    /**
     * [ActionSheetCallback] 的实现：获取签名授权。
     *
     * @param callback 授权回调。
     */
    override fun getAuthorisation(callback: SignAuthenticationCallback?) {
        // !!. 同上，假设 value 存在
        viewModelWC.getAuthenticationForSignature(viewModel.defaultWallet().value, this, callback)
    }

    /**
     * [ActionSheetCallback] 的实现：发送交易（占位）。
     */
    override fun sendTransaction(tx: Web3Transaction?) {
        // TODO: 实现交易发送逻辑
    }

    /**
     * [ActionSheetCallback] 的实现：完成交易发送（占位）。
     */
    override fun completeSendTransaction(tx: Web3Transaction?, signature: SignatureFromKey?) {
        // TODO: 实现交易完成逻辑
    }

    /**
     * [ActionSheetCallback] 的实现：ActionSheet 被关闭。
     *
     * @param txHash 交易哈希（如果适用）。
     * @param callbackId 回调 ID。
     * @param actionCompleted 动作是否已完成（例如，交易已发送）。
     */
    override fun dismissed(txHash: String?, callbackId: Long, actionCompleted: Boolean) {
        if (!actionCompleted) {
            awWalletConnectClient.dismissed(callbackId)
        }
    }

    /**
     * [ActionSheetCallback] 的实现：通知确认（占位）。
     */
    override fun notifyConfirm(mode: String?) {
        // TODO: 实现
    }

    /**
     * [ActionSheetCallback] 的实现：获取气体费用设置的启动器。
     */
    override fun gasSelectLauncher(): ActivityResultLauncher<Intent> {
        return getGasSettings
    }

    /**
     * ViewPager2 的 FragmentStateAdapter。
     */
    private class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        /**
         * 返回 Fragment 的总数。
         */
        override fun getItemCount(): Int = WalletPage.values().size

        /**
         * 为给定位置创建 Fragment。
         */
        override fun createFragment(position: Int): Fragment {
            return when (WalletPage.values()[position]) {
                WALLET -> WalletFragment()
                ACTIVITY -> ActivityFragment()
                WalletPage.DAPP_BROWSER -> if (CustomViewSettings.hideDappBrowser()) {
                    BaseFragment()
                } else {
                    DappBrowserFragment()
                }

                SETTINGS -> NewSettingsFragment()
            }
        }
    }

    /**
     * 检查 Fragment 是否已初始化。
     *
     * @return 如果所有主要 Fragment 都已创建，则返回 true。
     */
    private fun fragmentsInitialised(): Boolean {
        // 检查 SETTINGS fragment 是否已创建（它是最后一个）
        return supportFragmentManager.fragments.size >= SETTINGS.ordinal
    }

    /**
     * [AttestationImportInterface] 的实现：凭证导入成功。
     *
     * @param newToken 导入的新凭证的元数据。
     */
    override fun attestationImported(newToken: TokenCardMeta?) {
        runOnUiThread {
            val frag = getFragment(WALLET)
            if (frag is WalletFragment) {
                frag.updateAttestationMeta(newToken) // 添加到钱包
            }

            // 显示导入成功对话框
            if (dialog == null || dialog?.isShowing == false) {
                val aDialog = AWalletAlertDialog(this).apply {
                    setTitle(R.string.attestation_imported)
                    setIcon(AWalletAlertDialog.SUCCESS)
                    setButtonText(R.string.button_ok)
                    setButtonListener { this@apply.dismiss() }
                }
                dialog = aDialog
                aDialog.show()
            }
        }
    }

    /**
     * [AttestationImportInterface] 的实现：导入凭证时出错。
     *
     * @param error 错误信息。
     */
    override fun importError(error: String?) {
        runOnUiThread {
            hideDialog()
            val aDialog = AWalletAlertDialog(this).apply {
                setTitle(R.string.attestation_import_failed)
                setMessage(error)
                setIcon(AWalletAlertDialog.ERROR)
                setButtonText(R.string.button_ok)
                setButtonListener { this@apply.dismiss() }
            }
            dialog = aDialog
            aDialog.show()
        }
    }

    /**
     * [AttestationImportInterface] 的实现：SmartPass 验证回调。
     *
     * @param validation 验证结果。
     */
    override fun smartPassValidation(validation: SmartPassReturn?) {
        when (validation) {
            SmartPassReturn.ALREADY_IMPORTED -> {
                // 无需报告
            }

            SmartPassReturn.IMPORT_SUCCESS -> importedSmartPass()
            SmartPassReturn.IMPORT_FAILED -> {
                // 无需报告?
            }

            SmartPassReturn.NO_CONNECTION -> showNoConnection()
            else -> {}
        }
    }

    /**
     * 显示 "无网络连接" 的对话框。
     */
    private fun showNoConnection() {
        runOnUiThread {
            val aDialog = AWalletAlertDialog(this).apply {
                setTitle(R.string.no_connection)
                setMessage(R.string.no_connection_to_smart_layer)
                setIcon(AWalletAlertDialog.WARNING)
                setButtonText(R.string.button_ok)
                setButtonListener { this@apply.dismiss() }
            }
            dialog = aDialog
            aDialog.show()
        }
    }

    /**
     * 显示 "SmartPass 导入成功" 的对话框。
     */
    private fun importedSmartPass() {
        runOnUiThread {
            val aDialog = AWalletAlertDialog(this).apply {
                setTitle(R.string.imported_smart_pass)
                setMessage(R.string.smartpass_imported)
                setIcon(AWalletAlertDialog.SUCCESS)
                setButtonText(R.string.button_ok)
                setButtonListener { this@apply.dismiss() }
            }
            dialog = aDialog
            aDialog.show()
        }
    }

    /**
     * 显示 WalletConnect 请求等待中的进度对话框。
     */
    private fun walletConnectRequestPending() {
        hideDialog()
        runOnUiThread {
            wcProgressDialog = AWalletAlertDialog(this).apply {
                setProgressMode()
                setTitle(R.string.title_wallet_connect)
                setCancelable(false)
            }
            wcProgressDialog?.show()
            handler.postDelayed(this::hideDialog, 10000) // 10秒超时
        }
    }

    /**
     * 清除 WalletConnect 请求（例如，请求已处理或超时）。
     */
    fun clearWalletConnectRequest() {
        handler.removeCallbacksAndMessages(null)
        runOnUiThread(this::hideDialog)
    }
}
