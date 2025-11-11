package com.alphawallet.app.ui

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebHistoryItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alphawallet.app.C
import com.alphawallet.app.C.RESET_TOOLBAR
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.DApp
import com.alphawallet.app.entity.EIP681Type
import com.alphawallet.app.entity.FragmentMessenger
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.URLLoadInterface
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.analytics.ActionSheetSource
import com.alphawallet.app.entity.analytics.QrScanResultType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.service.GasService
import com.alphawallet.app.ui.HomeActivity.Companion.RESET_TOKEN_SERVICE
import com.alphawallet.app.ui.QRScanning.QRScannerActivity
import com.alphawallet.app.ui.widget.OnDappHomeNavClickListener
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.ui.widget.entity.DappBrowserSwipeInterface
import com.alphawallet.app.ui.widget.entity.DappBrowserSwipeLayout
import com.alphawallet.app.ui.widget.entity.ItemClickListener
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.DappBrowserUtils
import com.alphawallet.app.util.LocaleUtils
import com.alphawallet.app.util.QRParser
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.DappBrowserViewModel
import com.alphawallet.app.web3.OnEthCallListener
import com.alphawallet.app.web3.OnSignMessageListener
import com.alphawallet.app.web3.OnSignPersonalMessageListener
import com.alphawallet.app.web3.OnSignTransactionListener
import com.alphawallet.app.web3.OnSignTypedMessageListener
import com.alphawallet.app.web3.OnWalletActionListener
import com.alphawallet.app.web3.OnWalletAddEthereumChainObjectListener
import com.alphawallet.app.web3.Web3View
import com.alphawallet.app.web3.entity.Address
import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject
import com.alphawallet.app.web3.entity.Web3Call
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.AWalletAlertDialog.Companion.ERROR
import com.alphawallet.app.widget.AWalletAlertDialog.Companion.WARNING
import com.alphawallet.app.widget.ActionSheet
import com.alphawallet.app.widget.ActionSheetDialog
import com.alphawallet.app.widget.ActionSheetSignDialog
import com.alphawallet.app.widget.AddressBar
import com.alphawallet.app.widget.AddressBarListener
import com.alphawallet.app.widget.TestNetDialog
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.EthereumTypedMessage
import com.alphawallet.token.entity.MagicLinkInfo
import com.alphawallet.token.entity.SalesOrderMalformed
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable
import com.alphawallet.token.tools.ParseMagicLink
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.io.UnsupportedEncodingException
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * DApp 浏览器 Fragment
 *
 * 这是应用的核心功能之一，提供一个内置的 Web3 浏览器，允许用户与去中心化应用 (DApp) 交互。
 * 它实现了多个 Web3 监听器接口，用于处理来自 DApp 的请求，例如：
 * - 交易签名 (OnSignTransactionListener)
 * - 消息签名 (OnSignPersonalMessageListener, OnSignTypedMessageListener)
 * - eth_call (OnEthCallListener)
 * - 添加/切换网络 (OnWalletAddEthereumChainObjectListener, OnWalletActionListener)
 *
 * 它还管理：
 * - 浏览器历史记录、收藏夹 (My DApps) 和 DApp 发现。
 * - 网页加载、刷新和导航。
 * - 权限请求（相机、地理位置、文件）。
 * - WalletConnect 会话。
 * - 用户余额和当前网络的显示。
 */
@AndroidEntryPoint
class DappBrowserFragment : BaseFragment(), OnSignTransactionListener, OnSignPersonalMessageListener,
    OnSignTypedMessageListener, OnSignMessageListener, OnEthCallListener, OnWalletAddEthereumChainObjectListener,
    OnWalletActionListener, URLLoadInterface, ItemClickListener, OnDappHomeNavClickListener,
    DappBrowserSwipeInterface,
    ActionSheetCallback, TestNetDialog.TestNetDialogCallback {

    companion object {
        private const val TAG = "DappBrowserFragment"
        const val SEARCH = "SEARCH"
        const val PERSONAL_MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n"
        const val CURRENT_FRAGMENT = "currentFragment"
        const val DAPP_CLICK = "dapp_click"
        const val DAPP_REMOVE_HISTORY = "dapp_remove"
        const val REQUEST_FILE_ACCESS = 31
        const val REQUEST_FINE_LOCATION = 110
        const val REQUEST_CAMERA_ACCESS = 111
        private const val DAPP_BROWSER = "DAPP_BROWSER"
        private const val MY_DAPPS = "MY_DAPPS"
        private const val DISCOVER_DAPPS = "DISCOVER_DAPPS"
        private const val HISTORY = "HISTORY"
        private const val CURRENT_URL = "urlInBar"
        private const val WALLETCONNECT_CHAINID_ERROR = "Error: ChainId missing or not supported"
        private const val MAGIC_BUNDLE_VAL = 0xACED00DL
        private const val BUNDLE_FILE = "awbrowse"

        @Volatile
        private var forceChainChange = 0L
    }

    /**
     * 用于 UI 线程操作的 Handler。
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 用于处理文件上传的回调。
     */
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    /**
     * 文件选择器的参数。
     */
    private var fileChooserParams: WebChromeClient.FileChooserParams? = null

    /**
     * ActivityResultLauncher 用于处理文件内容获取。
     */
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadMessage?.onReceiveValue(arrayOf(uri))
        }
    }

    /**
     * Realm 监听器，用于监听原生代币余额变化。
     */
    private var realmUpdate: RealmResults<RealmToken>? = null

    /**
     * 当前 Fragment 使用的 Realm 实例。
     */
    private var realm: Realm? = null

    /**
     * 用于显示交易签名、消息签名等的底部弹窗。
     */
    private var confirmationDialog: ActionSheet? = null

    /**
     * ActivityResultLauncher 用于获取 Gas 设置。
     */
    private val getGasSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            confirmationDialog?.setCurrentGasIndex(result)
        }

    /**
     * DApp 浏览器的 ViewModel。
     */
    private lateinit var viewModel: DappBrowserViewModel

    /**
     * 支持下拉刷新的布局。
     */
    private lateinit var swipeRefreshLayout: DappBrowserSwipeLayout

    /**
     * 核心的 Web3View 实例。
     */
    private lateinit var web3: Web3View

    /**
     * 页面加载进度条。
     */
    private lateinit var progressBar: ProgressBar

    /**
     * 当前用户的钱包。
     */
    private lateinit var wallet: Wallet

    /**
     * 当前激活的网络。
     */
    private var activeNetwork: NetworkInfo? = null

    /**
     * 用于切换网络的确认对话框。
     */
    private var chainSwapDialog: AWalletAlertDialog? = null

    /**
     * 用于显示通用结果（如错误、警告）的对话框。
     */
    private var resultDialog: AWalletAlertDialog? = null

    /**
     * 用于显示错误的对话框。
     */
    private var errorDialog: AWalletAlertDialog? = null

    /**
     * 初始加载的 URL（例如，从其他 Fragment 跳转而来）。
     */
    private var loadOnInit: String? = null

    /**
     * 标记是否点击了“主页”按钮。
     */
    private var homePressed: Boolean = false

    /**
     * 添加自定义EVM链的提示框。
     */
    private var addCustomChainDialog: AddEthereumChainPrompt? = null

    /**
     * 顶部的工具栏。
     */
    private lateinit var toolbar: Toolbar

    /**
     * 刷新按钮（仅在紧凑模式下显示）。
     */
    private var refresh: ImageView? = null

    /**
     * 包含 WebView 的 FrameLayout。
     */
    private lateinit var webFrame: FrameLayout

    /**
     * 显示原生代币余额。
     */
    private lateinit var balance: TextView

    /**
     * 显示原生代币符号。
     */
    private lateinit var symbol: TextView

    /**
     * 顶部的地址栏/搜索栏。
     */
    private lateinit var addressBar: AddressBar

    /**
     * 监听软键盘弹出和收起，以调整 WebView 大小。
     * 这修复了输入框在页面底部时被键盘遮挡的问题。
     */
    private val resizeListener = View.OnApplyWindowInsetsListener { v, insets ->
        val activity = activity ?: return@OnApplyWindowInsetsListener insets

        val r = Rect()
        v.getWindowVisibleDisplayFrame(r)

        val heightDifference = v.rootView.height - (r.bottom - r.top)
        val navBarHeight = (activity as? HomeActivity)?.navBarHeight ?: 0

        val layoutParams = webFrame.layoutParams as ViewGroup.MarginLayoutParams

        // 检查是否需要调整 webview 大小
        if (heightDifference > 0 && webFrame.layoutParams.height != heightDifference) {
            // 进入“收缩”模式，避免网页数据被遮挡
            layoutParams.bottomMargin = heightDifference
            webFrame.layoutParams = layoutParams
        } else if (heightDifference == 0 && layoutParams.bottomMargin != navBarHeight) {
            // 恢复全屏模式
            layoutParams.bottomMargin = 0
            webFrame.layoutParams = layoutParams
            toolbar.menu.setGroupVisible(R.id.dapp_browser_menu, true)
            addressBar.shrinkSearchBar()
        }

        insets
    }

    /**
     * 地理位置权限回调。
     */
    private var geoCallback: GeolocationPermissions.Callback? = null

    /**
     * 网页权限请求回调（例如相机）。
     */
    private var requestCallback: PermissionRequest? = null

    /**
     * 地理位置权限请求的来源 URL。
     */
    private var geoOrigin: String? = null

    /**
     * 当前的 WalletConnect 会话 URI。
     */
    private var walletConnectSession: String? = null

    /**
     * 当前网页的标题。
     */
    private var currentWebpageTitle: String? = null

    /**
     * 当前显示的子 Fragment 标签（DAPP_BROWSER, MY_DAPPS, DISCOVER_DAPPS, HISTORY）。
     */
    private var currentFragment: String? = null

    /**
     * ActivityResultLauncher 用于处理网络选择（来自菜单）。
     */
    private val getNetwork =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let {
                val networkId = it.getLongExtra(C.EXTRA_CHAIN_ID, 1L)
                forceChainChange = networkId
                loadNewNetwork(networkId)
                // 可能调整了过滤器
                parentFragmentManager.setFragmentResult(RESET_TOKEN_SERVICE, Bundle())
            }
        }

    /**
     * ActivityResultLauncher 用于处理网络选择（来自 DApp 请求）。
     */
    private val getNewNetwork =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let {
                val networkId = it.getLongExtra(C.EXTRA_CHAIN_ID, 1L)
                loadNewNetwork(networkId)
            }
        }

    /**
     * 用于在切换网络后加载的 URL。
     */
    private var loadUrlAfterReload: String? = null

    /**
     * Fragment 的 onCreate 生命周期方法。
     */
    override fun onCreate(savedInstanceState: Bundle?) {

        LocaleUtils.setActiveLocale(requireContext())
        super.onCreate(savedInstanceState)

        // 监听来自子 Fragment (MyDappsFragment, BrowserHistoryFragment) 的点击事件
        childFragmentManager.setFragmentResultListener(DAPP_CLICK, this) { _, bundle ->
            val dapp = bundle.getParcelable<DApp>(DAPP_CLICK)
            val removedDapp = bundle.getParcelable<DApp>(DAPP_REMOVE_HISTORY)
            addToBackStack(DAPP_BROWSER)
            when {
                dapp != null -> loadUrl(dapp.url?:"")
                removedDapp != null -> addressBar.removeSuggestion(removedDapp)
            }
        }
    }

    /**
     * Fragment 的 onResume 生命周期方法。
     */
    override fun onResume() {
        super.onResume()
        homePressed = false
        if (currentFragment == null) currentFragment = DAPP_BROWSER
        attachFragment(currentFragment)

        if (viewModel == null) {
            // 如果 VM 为 null，说明 Fragment 状态丢失，需要重启
            activity?.recreate()
        } else {
            viewModel.track(Analytics.Navigation.BROWSER)
            web3.urlLoadInterface = this
        }

        startBalanceListener()
    }

    /**
     * Fragment 的 onCreateView 生命周期方法。
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocaleUtils.setActiveLocale(requireActivity())
        loadOnInit = null
        val webViewID =
            if (CustomViewSettings.minimiseBrowserURLBar()) R.layout.fragment_webview_compact else R.layout.fragment_webview
        val view = inflater.inflate(webViewID, container, false)
        initViewModel()
        initView(view)

        // 设置地址栏
        addressBar.setup(viewModel.getDappsMasterList(requireContext()), object : AddressBarListener {
            /**
             * 当用户在地址栏确认加载 URL 时调用。
             */
            override fun onLoad(urlText: String): Boolean {
                addToBackStack(DAPP_BROWSER)
                val handled = loadUrl(urlText)
                detachFragments()
                cancelSearchSession()
                return handled
            }

            /**
             * 当用户清除搜索框时调用。
             */
            override fun onClear() {
                cancelSearchSession()
            }

            /**
             * 当点击“前进”按钮时调用。
             */
            override fun loadNext(): WebBackForwardList {
                goToNextPage()
                return web3.copyBackForwardList()
            }

            /**
             * 当点击“后退”按钮时调用。
             */
            override fun loadPrevious(): WebBackForwardList {
                backPressed()
                return web3.copyBackForwardList()
            }

            /**
             * 当点击“主页”按钮时调用。
             */
            override fun onHomePagePressed(): WebBackForwardList {
                homePressed()
                return web3.copyBackForwardList()
            }
        })

        attachFragment(DAPP_BROWSER)

        // 从应用内的链接加载 URL
        loadOnInit = arguments?.getString("url")

        return view
    }

    /**
     * 附加一个子 Fragment（例如“我的DApps”、“历史记录”）。
     * @param tag Fragment 的标签。
     */
    private fun attachFragment(tag: String?) {
        if (tag != null && host != null && childFragmentManager.findFragmentByTag(tag) == null) {
            val f: Fragment? = when (tag) {
                DISCOVER_DAPPS -> DiscoverDappsFragment()
                MY_DAPPS -> MyDappsFragment()
                HISTORY -> BrowserHistoryFragment()
                DAPP_BROWSER -> { // DAPP_BROWSER 是特殊情况，表示没有子 Fragment
                    addToBackStack(DAPP_BROWSER)
                    null
                }
                else -> null
            }

            if (f != null && !f.isAdded) {
                showFragment(f, tag)
            }
        }
    }

    /**
     * 显示一个子 Fragment。
     * @param fragment 要显示的 Fragment。
     * @param tag Fragment 的标签。
     */
    private fun showFragment(fragment: Fragment, tag: String) {
        addToBackStack(tag)
        childFragmentManager.beginTransaction()
            .add(R.id.frame, fragment, tag)
            .commit()

        addressBar.updateNavigationButtons(web3.copyBackForwardList())
    }

    /**
     * 分离所有子 Fragment，只显示 DApp 浏览器。
     */
    private fun detachFragments() {
        detachFragment(MY_DAPPS)
        detachFragment(DISCOVER_DAPPS)
        detachFragment(HISTORY)
        detachFragment(SEARCH)
    }

    /**
     * 处理点击“主页”按钮的逻辑。
     */
    private fun homePressed() {
        homePressed = true
        detachFragments()
        currentFragment = DAPP_BROWSER
        addressBar.clear()
        resetDappBrowser()
    }

    /**
     * [OnDappHomeNavClickListener] 接口实现（用于 Discover DApps 等页面）。
     */
    override fun onDappHomeNavClick(position: Int) {
        detachFragments()
        addToBackStack(DAPP_BROWSER)
    }

    /**
     * Fragment 的 onDestroy 生命周期方法。
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.onDestroy()
        stopBalanceListener()
        addressBar.destroy()
    }

    /**
     * 设置工具栏菜单及其点击事件。
     * @param baseView Fragment 的根视图。
     */
    private fun setupMenu(baseView: View) {
        refresh = baseView.findViewById(R.id.refresh)
        val reload = toolbar.menu.findItem(R.id.action_reload)
        val share = toolbar.menu.findItem(R.id.action_share)
        val scan = toolbar.menu.findItem(R.id.action_scan)
        val add = toolbar.menu.findItem(R.id.action_add_to_my_dapps)
        val history = toolbar.menu.findItem(R.id.action_history)
        val bookmarks = toolbar.menu.findItem(R.id.action_my_dapps)
        val clearCache = toolbar.menu.findItem(R.id.action_clear_cache)
        val network = toolbar.menu.findItem(R.id.action_network)
        val setAsHomePage = toolbar.menu.findItem(R.id.action_set_as_homepage)

        reload?.setOnMenuItemClickListener {
            reloadPage()
            true
        }
        share?.setOnMenuItemClickListener {
            if (web3.url != null && currentFragment == DAPP_BROWSER) {
                context?.let { ctx -> viewModel.share(ctx, web3.url!!) }
            } else {
                displayNothingToShare()
            }
            true
        }
        scan?.setOnMenuItemClickListener {
            viewModel.startScan(activity)
            true
        }
        add?.setOnMenuItemClickListener {
            viewModel.addToMyDapps(context, currentWebpageTitle, addressBar.getUrl())
            true
        }
        history?.setOnMenuItemClickListener {
            attachFragment(HISTORY)
            true
        }
        bookmarks?.setOnMenuItemClickListener {
            attachFragment(MY_DAPPS)
            true
        }
        clearCache?.setOnMenuItemClickListener {
            viewModel.onClearBrowserCacheClicked(context)
            true
        }
        network?.setOnMenuItemClickListener {
            openNetworkSelection()
            true
        }
        setAsHomePage?.setOnMenuItemClickListener {
            viewModel.setHomePage(context, addressBar.getUrl())
            true
        }

        updateNetworkMenuItem()
    }

    /**
     * 更新工具栏中的网络菜单项（显示当前网络名称）。
     */
    private fun updateNetworkMenuItem() {
        activeNetwork?.let {
            toolbar.menu.findItem(R.id.action_network)
                ?.title = getString(R.string.network_menu_item, it.shortName)
            symbol.text = it.shortName
        }
    }

    /**
     * 初始化 Fragment 的视图组件。
     * @param view Fragment 的根视图。
     */
    private fun initView(view: View) {
        web3 = view.findViewById(R.id.web3view)
        val savedState = readBundleFromLocal()
        if (savedState != null) {
            web3.restoreState(savedState)
            val lastUrl = savedState.getString(CURRENT_URL)
            loadOnInit = if (TextUtils.isEmpty(lastUrl)) getDefaultDappUrl() else lastUrl
        } else {
            loadOnInit = getDefaultDappUrl()
        }

        addressBar = view.findViewById(R.id.address_bar_widget)
        progressBar = view.findViewById(R.id.progressBar)
        webFrame = view.findViewById(R.id.frame)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh)
        swipeRefreshLayout.setRefreshInterface(this)

        toolbar = view.findViewById(R.id.address_bar)

        // 使用本地化的 Context 来加载菜单
        val inflater = MenuInflater(LocaleUtils.getActiveLocaleContext(requireContext()))
        if (CustomViewSettings.minimiseBrowserURLBar()) {
            inflater.inflate(R.menu.menu_scan, toolbar.menu)
        } else if (getDefaultDappUrl() != null) {
            inflater.inflate(R.menu.menu_bookmarks, toolbar.menu)
        }
        refresh = view.findViewById(R.id.refresh)

        val layout = view.findViewById<RelativeLayout>(R.id.address_bar_layout)
        layout.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        refresh?.setOnClickListener { reloadPage() }

        balance = view.findViewById(R.id.balance)
        symbol = view.findViewById(R.id.symbol)
        web3.urlLoadInterface = this

        webFrame.setOnApplyWindowInsetsListener(resizeListener)

        setupMenu(view)
    }

    /**
     * 当没有内容可分享时，显示提示对话框。
     */
    private fun displayNothingToShare() {
        activity ?: return
        resultDialog = AWalletAlertDialog(requireActivity()).apply {
            setTitle(getString(R.string.nothing_to_share))
            setMessage(getString(R.string.nothing_to_share_message))
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            setCancelable(true)
            show()
        }
    }

    /**
     * 打开网络选择器 Activity。
     */
    private fun openNetworkSelection() {
        val intent = Intent(context, NetworkChooserActivity::class.java).apply {
            putExtra(C.EXTRA_SINGLE_ITEM, true)
            activeNetwork?.let { putExtra(C.EXTRA_CHAIN_ID, it.chainId) }
        }
        getNetwork.launch(intent)
    }

    /**
     * 当 Fragment 进入焦点时调用（来自 HomeActivity）。
     */
    override fun comeIntoFocus() {
        if (viewModel.getActiveNetwork() == null || activeNetwork == null || activeNetwork!!.chainId != viewModel.getActiveNetwork()!!.chainId) {
            viewModel.checkForNetworkChanges()
        } else {
            viewModel.startBalanceUpdate()
            startBalanceListener()
            viewModel.updateGasPrice(activeNetwork!!.chainId)
        }
        viewModel.getTokenService().stopUpdateCycle()
        addressBar.leaveEditMode()
    }

    /**
     * 当 Fragment 离开焦点时调用（来自 HomeActivity）。
     */
    override fun leaveFocus() {
        web3.requestFocus()
        addressBar.leaveFocus()
        viewModel.stopBalanceUpdate()
        stopBalanceListener()
    }

    /**
     * 将一个子 Fragment 添加到返回堆栈（逻辑上的）。
     * @param nextFragment 下一个 Fragment 的标签。
     */
    private fun addToBackStack(nextFragment: String) {
        if (currentFragment != null && currentFragment != DAPP_BROWSER) {
            currentFragment?.let {
                detachFragment(it)
            }
        }
        currentFragment = nextFragment
    }

    /**
     * 取消搜索会话并分离搜索 Fragment。
     */
    private fun cancelSearchSession() {
        detachFragment(SEARCH)
        addressBar.updateNavigationButtons(web3.copyBackForwardList())
    }

    /**
     * 分离一个子 Fragment。
     * @param tag Fragment 的标签。
     */
    private fun detachFragment(tag: String) {
        if (!isAdded) return // DappBrowserFragment 自己可能还未附加
        val fragment = childFragmentManager.findFragmentByTag(tag)
        if (fragment != null && fragment.isVisible && !fragment.isDetached) {
            fragment.onDetach()
            childFragmentManager.beginTransaction()
                .remove(fragment)
                .commitAllowingStateLoss()
        }
        currentFragment = DAPP_BROWSER
    }

    /**
     * 初始化 ViewModel 并设置观察者。
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[DappBrowserViewModel::class.java]
        viewModel.activeNetwork().observe(viewLifecycleOwner) { onNetworkChanged(it) }
        viewModel.defaultWallet().observe(viewLifecycleOwner) { onDefaultWallet(it) }
        viewModel.transactionFinalised().observe(viewLifecycleOwner) { txWritten(it) }
        viewModel.transactionSigned().observe(viewLifecycleOwner) { txSigned(it) }
        viewModel.transactionError().observe(viewLifecycleOwner) { txError(it) }
        activeNetwork = viewModel.getActiveNetwork()
        viewModel.findWallet()
    }

    /**
     * 开始监听原生代币余额的变化。
     */
    private fun startBalanceListener() {
        val currentWallet = wallet
        val currentNetwork = activeNetwork
        if (currentWallet == null || currentNetwork == null) return
        if (realm == null || realm!!.isClosed) {
            realm = viewModel.getRealmInstance(currentWallet)
        }

        realmUpdate?.removeAllChangeListeners()
        realmUpdate = realm?.where(RealmToken::class.java)
            ?.equalTo("address", TokensRealmSource.databaseKey(currentNetwork.chainId, "eth"))
            ?.findAllAsync()

        realmUpdate?.addChangeListener { realmTokens ->
            if (realmTokens.isEmpty()) return@addChangeListener
            val realmToken = realmTokens.first()
            realmToken?.let {
                balance.visibility = View.VISIBLE
                symbol.visibility = View.VISIBLE
                val newBalanceStr = BalanceUtils.getScaledValueFixed(
                    BigDecimal(it.balance),
                    C.ETHER_DECIMALS.toLong(),
                    Token.TOKEN_BALANCE_PRECISION
                )
                balance.text = newBalanceStr
                symbol.text = activeNetwork?.shortName?: ""
            }
        }
    }

    /**
     * 停止监听原生代币余额的变化。
     */
    private fun stopBalanceListener() {
        realmUpdate?.removeAllChangeListeners()
        realmUpdate = null
        realm?.close()
        realm = null
    }

    /**
     * 当默认钱包加载时调用。
     * @param wallet 加载的钱包实例。
     */
    private fun onDefaultWallet(wallet: Wallet) {
        this.wallet = wallet
        if (activeNetwork != null) {
            val needsReload = loadOnInit == null
            setupWeb3(wallet)
            if (needsReload) reloadPage()
        }
    }

    /**
     * [URLLoadInterface] 接口实现：切换网络并加载 URL。
     * @param chainId 目标链 ID。
     * @param url 目标 URL。
     */
    override fun switchNetworkAndLoadUrl(chainId: Long, url: String?) {
        forceChainChange = chainId // 强制切换链，避免某些 DApp (如 1inch) 弹出提示
        loadUrlAfterReload = url // 在网络切换和页面重载后加载此 URL

        activeNetwork = viewModel.getNetworkInfo(chainId)
        updateNetworkMenuItem()
        viewModel.setNetwork(chainId)
        startBalanceListener()
        setupWeb3(wallet)
        web3.resetView()
        web3.reload()
    }

    /**
     * 当 ViewModel 通知网络变更时调用。
     * @param networkInfo 新的网络信息。
     */
    private fun onNetworkChanged(networkInfo: NetworkInfo?) {
        val networkChanged = networkInfo != null && (activeNetwork == null || activeNetwork?.chainId != networkInfo.chainId)
        this.activeNetwork = networkInfo
        if (networkInfo != null) {
            if (networkChanged) {
                viewModel.findWallet()
                updateNetworkMenuItem()
            }
            if (networkChanged && addressBar.isOnHomePage()) {
                resetDappBrowser() // 如果在主页且网络变化，则重置浏览器
            }
            updateFilters(networkInfo)
        } else {
            openNetworkSelection()
            resetDappBrowser()
        }
    }

    /**
     * 更新代币过滤器（在 HomeActivity 中）以匹配当前网络。
     * @param networkInfo 当前网络信息。
     */
    private fun updateFilters(networkInfo: NetworkInfo) {
        viewModel.addNetworkToFilters(networkInfo)
        parentFragmentManager.setFragmentResult(RESET_TOKEN_SERVICE, Bundle()) // 重置代币服务和钱包页面
    }

    /**
     * 启动网络选择器（用于 WalletConnect）。
     */
    private fun launchNetworkPicker() {
        val intent = Intent(context, NetworkChooserActivity::class.java).apply {
            putExtra(C.EXTRA_SINGLE_ITEM, true)
            activeNetwork?.let { putExtra(C.EXTRA_CHAIN_ID, it.chainId) }
        }
        getNewNetwork.launch(intent)
    }

    /**
     * 取消 WalletConnect 会话。
     */
    private fun launchWalletConnectSessionCancel() {
        // TODO: (WalletConnect V2) 实现关闭会话的逻辑
        reloadPage()
    }

    /**
     * 显示关闭 WalletConnect 的提示（当链 ID 不支持时）。
     */
    private fun displayCloseWC() {
        handler.post {
            resultDialog?.dismiss()
            resultDialog = AWalletAlertDialog(requireContext()).apply {
                setIcon(WARNING)
                setTitle(R.string.title_wallet_connect)
                setMessage(getString(R.string.unsupported_walletconnect))
                setButtonText(R.string.button_ok)
                setButtonListener {
                    launchWalletConnectSessionCancel()
                    launchNetworkPicker()
                    dismiss()
                }
                show()
            }
        }
    }

    /**
     * 设置 Web3View 的核心客户端和监听器。
     * @param wallet 当前钱包。
     */
    private fun setupWeb3(wallet: Wallet) {
        val currentNetwork = activeNetwork ?: return

        web3.setChainId(currentNetwork.chainId)
        web3.setWalletAddress(Address(wallet.address.toString()))

        web3.setWebChromeClient(object : WebChromeClient() {
            /**
             * 页面加载进度变化时调用。
             */
            override fun onProgressChanged(webview: WebView, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    refresh?.isEnabled = true
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                    swipeRefreshLayout.isRefreshing = true
                }
            }

            /**
             * 处理控制台消息。
             */
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    if (msg.message().contains(WALLETCONNECT_CHAINID_ERROR)) {
                        displayCloseWC()
                    }
                }
                return super.onConsoleMessage(msg)
            }

            /**
             * 收到网页标题时调用。
             */
            override fun onReceivedTitle(view: WebView, title: String) {
                super.onReceivedTitle(view, title)
                currentWebpageTitle = title
            }

            /**
             * 网页请求权限时调用（例如相机）。
             */
            override fun onPermissionRequest(request: PermissionRequest) {
                requestCameraPermission(request)
            }

            /**
             * 网页请求地理位置权限时调用。
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
                requestGeoPermission(origin, callback)
            }

            /**
             * 网页请求打开文件选择器时调用。
             */
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fCParams: FileChooserParams
            ): Boolean {
                uploadMessage = filePathCallback
                fileChooserParams = fCParams
                return if (checkReadPermission()) requestUpload() else true
            }
        })

        web3.webViewClient = object : WebViewClient() {
            /**
             * 决定是否覆盖 URL 加载（用于处理自定义协议头）。
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val url = uri.toString()
                return handlePrefix(url)
            }
        }

        // 设置 Web3 监听器
        web3.onSignMessageListener = this
        web3.onSignPersonalMessageListener = this
        web3.onSignTransactionListener = this
        web3.onSignTypedMessageListener = this
        web3.onEthCallListener = this
        web3.onWalletAddEthereumChainObjectListener = this
        web3.onWalletActionListener = this

        // 如果有初始 URL，加载它
        if (loadOnInit != null) {
            web3.clearCache(false) // 重启应用时可能需要清除缓存
            addToBackStack(DAPP_BROWSER)
            web3.resetView()
            web3.loadUrl(Utils.formatUrl(loadOnInit!!))
            setUrlText(Utils.formatUrl(loadOnInit!!))
            loadOnInit = null
        }
    }

    /**
     * 处理 URL 的自定义协议头（例如 `tel:`, `mailto:`, `wc:` 等）。
     * @param url 要处理的 URL。
     * @return true 如果 URL 被处理，否则 false。
     */
    private fun handlePrefix(url: String): Boolean {
        val prefixCheck = url.split(":")
        if (prefixCheck.size > 1) {
            val intent: Intent
            when (prefixCheck[0]) {
                C.DAPP_PREFIX_TELEPHONE -> {
                    intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse(url)
                    startActivity(Intent.createChooser(intent, "Call " + prefixCheck[1]))
                    return true
                }
                C.DAPP_PREFIX_MAILTO -> {
                    intent = Intent(Intent.ACTION_SENDTO)
                    intent.data = Uri.parse(url)
                    startActivity(Intent.createChooser(intent, "Email: " + prefixCheck[1]))
                    return true
                }
                C.DAPP_PREFIX_ALPHAWALLET -> {
                    if (prefixCheck[1] == C.DAPP_SUFFIX_RECEIVE) {
                        viewModel.showMyAddress(context)
                        return true
                    }
                }
                C.DAPP_PREFIX_AWALLET -> {
                    handleAWCode(url)
                    return true
                }
                C.DAPP_PREFIX_WALLETCONNECT -> {
                    if (wallet.type == WalletType.WATCH) {
                        showWalletWatch()
                    } else {
                        walletConnectSession = url
                        context?.let { viewModel.handleWalletConnect(it, url, activeNetwork) }
                    }
                    return true
                }
            }
        }

        // 检查是否来自 WalletConnect 的 Magic Link 弹窗
        if (fromWalletConnectModal(url)) {
            val encodedURL = url.split("=")[1]
            try {
                val decodedURL = URLDecoder.decode(encodedURL, Charset.defaultCharset().name())
                viewModel.handleWalletConnect(context, decodedURL, activeNetwork)
                return true
            } catch (e: UnsupportedEncodingException) {
                Timber.d("Decode URL failed: %s", e.message)
            }
        }

        return false
    }

    /**
     * 处理内部 `awallet:` 协议码。
     * @param awCode `awallet:` 协议字符串。
     */
    private fun handleAWCode(awCode: String) {
        val codeBundle = Bundle().apply {
            putString(C.AWALLET_CODE, awCode)
        }
        parentFragmentManager.setFragmentResult(C.AWALLET_CODE, codeBundle)
    }

    /**
     * 检查 URL 是否来自 WalletConnect 的 Magic Link 弹窗。
     * @param url 要检查的 URL。
     */
    private fun fromWalletConnectModal(url: String): Boolean {
        return url.startsWith("https://${MagicLinkInfo.mainnetMagicLinkDomain}/wc?uri=")
    }

    /**
     * 更新地址栏的 URL 文本和导航按钮状态。
     * @param newUrl 新的 URL。
     */
    private fun setUrlText(newUrl: String) {
        addressBar.setUrl(newUrl)
        addressBar.updateNavigationButtons(web3.copyBackForwardList())
    }

    /**
     * 加载新网络并重载页面。
     * @param newNetworkId 要加载的新网络 ID。
     */
    private fun loadNewNetwork(newNetworkId: Long) {
        if (activeNetwork == null || activeNetwork?.chainId != newNetworkId) {
            balance.visibility = View.GONE
            symbol.visibility = View.GONE
            viewModel.setNetwork(newNetworkId)
            onNetworkChanged(viewModel.getNetworkInfo(newNetworkId))
            startBalanceListener()
            viewModel.updateGasPrice(newNetworkId)
        }
        reloadPage()
    }

    /**
     * 请求文件上传（打开文件选择器）。
     * @return true 如果成功打开选择器，否则 false。
     */
    private fun requestUpload(): Boolean {
        return try {
            getContent.launch(determineMimeType(fileChooserParams))
            true
        } catch (e: ActivityNotFoundException) {
            uploadMessage = null
            Toast.makeText(
                activity?.applicationContext,
                "Cannot Open File Chooser",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * [OnSignMessageListener] 接口实现：处理 `eth_sign` 请求。
     * @param message 签名消息。
     */
    override fun onSignMessage(message: EthereumMessage) {
        handleSignMessage(message)
    }

    /**
     * [OnSignPersonalMessageListener] 接口实现：处理 `personal_sign` 请求。
     * @param message 签名消息。
     */
    override fun onSignPersonalMessage(message: EthereumMessage) {
        handleSignMessage(message)
    }

    /**
     * [OnSignTypedMessageListener] 接口实现：处理 `eth_signTypedData` 请求。
     * @param message 签名消息。
     */
    override fun onSignTypedMessage(message: EthereumTypedMessage) {
        if (message.prehash == null || message.messageType == SignMessageType.SIGN_ERROR) {
            web3.onSignCancel(message.callbackId)
        } else {
            handleSignMessage(message)
        }
    }

    /**
     * [OnEthCallListener] 接口实现：处理 `eth_call` 请求。
     * @param txdata Web3Call 请求对象。
     */
    override fun onEthCall(txdata: Web3Call?) {
        // 使用 viewLifecycleOwner.lifecycleScope 启动协程
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 在 IO 线程执行网络请求
                val result = withContext(Dispatchers.IO) {
                    val web3j: Web3j = TokenRepository.getWeb3jService(activeNetwork!!.chainId)
                    val transaction: Transaction = Transaction.createFunctionCallTransaction(
                        wallet.address,
                        null,
                        null,
                        txdata?.gasLimit,
                        txdata?.to.toString(),
                        txdata?.value,
                        txdata?.payload
                    )
                    web3j.ethCall(transaction, txdata?.blockParam).send()
                }.value // 提取 EthCall 的结果值

                // 回到主线程，将结果回调给 WebView
                txdata?.let {
                    web3.onCallFunctionSuccessful(it.leafPosition, result)

                }
            } catch (e: Throwable) {
                // 回到主线程，将错误回调给 WebView
                txdata?.let {
                    web3.onCallFunctionError(it.leafPosition, e.message?:"")
                }

            }
        }
    }

    /**
     * [OnWalletAddEthereumChainObjectListener] 接口实现：处理 `wallet_addEthereumChain` 请求。
     * @param callbackId 回调 ID。
     * @param chainObject 请求添加的链对象。
     */
    override fun onWalletAddEthereumChainObject(
        callbackId: Long,
        chainObject: WalletAddEthereumChainObject
    ) {
        val chainId = chainObject.chainId
        val info = viewModel.getNetworkInfo(chainId?.toLong()?:0L)

        if (forceChainChange != 0L || context == null) {
            return // 如果正在强制切换链，则不执行任何操作
        }

        if (info == null) {
            // 显示添加自定义链的对话框
            addCustomChainDialog =
                AddEthereumChainPrompt.newInstance(requireContext(), chainObject) { chainObject ->
                    if (viewModel.addCustomChain(chainObject)) {
                        chainObject.chainId?.let {
                            loadNewNetwork(it.toLong())
                        }

                    } else {
                        displayError(R.string.error_invalid_url, 0)
                    }
                    addCustomChainDialog?.dismiss()
                }
            addCustomChainDialog?.show()
        } else {
            // 请求切换到已知网络
            changeChainRequest(callbackId, info)
        }
    }

    /**
     * 请求切换网络。
     * @param callbackId 回调 ID。
     * @param info 目标网络信息。
     */
    private fun changeChainRequest(callbackId: Long, info: NetworkInfo) {
        // 如果网络不需要改变，或者对话框已显示，则直接成功返回
        if ((activeNetwork != null && activeNetwork?.chainId == info.chainId) ||
            (chainSwapDialog != null && chainSwapDialog!!.isShowing)
        ) {
            web3.onWalletActionSuccessful(callbackId, "")
            return
        }

        // 如果在主网和测试网之间切换，显示 TestNetDialog
        if (!info.hasRealValue() && (activeNetwork != null && activeNetwork!!.hasRealValue())) {
            val testnetDialog = TestNetDialog(requireContext(), info.chainId, this)
            testnetDialog.show()
        } else {
            // 否则，直接显示切换链的 ActionSheet
            showChainChangeDialog(callbackId, info)
        }
    }

    /**
     * [OnWalletActionListener] 接口实现：处理 `eth_requestAccounts` 请求。
     * @param callbackId 回调 ID。
     */
    override fun onRequestAccounts(callbackId: Long) {
        // TODO: 弹出对话框请求用户授权暴露地址
        // 目前：自动授权并返回地址
        web3.onWalletActionSuccessful(callbackId, "[\"${wallet.address}\"]")
    }

    /**
     * [OnWalletActionListener] 接口实现：处理 `wallet_switchEthereumChain` (EIP-3326) 请求。
     * @param callbackId 回调 ID。
     * @param chainObj 请求切换的链对象。
     */
    override fun onWalletSwitchEthereumChain(
        callbackId: Long,
        chainObj: WalletAddEthereumChainObject
    ) {
        val chainId = chainObj.chainId
        val info = viewModel.getNetworkInfo(chainId?.toLong() ?: 0L)
        if (info == null) {
            // 未知网络
            chainSwapDialog = AWalletAlertDialog(requireActivity()).apply {
                setTitle(R.string.unknown_network_title)
                setMessage(getString(R.string.unknown_network, chainId.toString()))
                setButton(R.string.dialog_ok) { if (isShowing) dismiss() }
                setSecondaryButton(R.string.action_cancel) { dismiss() }
                setCancelable(false)
                show()
            }
        } else {
            // 已知网络，请求切换
            changeChainRequest(callbackId, info)
        }
    }

    /**
     * 显示切换网络的 ActionSheet 弹窗。
     * @param callbackId 回调 ID。
     * @param newNetwork 目标网络。
     */
    private fun showChainChangeDialog(callbackId: Long, newNetwork: NetworkInfo) {
        val baseToken = viewModel.getTokenService().getTokenOrBase(newNetwork.chainId, wallet.address.toString())
        confirmationDialog = ActionSheetDialog(
            requireActivity(), this, R.string.switch_chain_request, R.string.switch_and_reload,
            callbackId, baseToken, activeNetwork, newNetwork
        ).apply {
            setCanceledOnTouchOutside(true)
            show()
            fullExpand()
        }
    }

    /**
     * 统一处理所有类型的签名请求。
     * @param message 要签名的消息 (Signable)。
     */
    private fun handleSignMessage(message: Signable) {
        if (message.messageType == SignMessageType.SIGN_TYPED_DATA_V3 && message.chainId != activeNetwork?.chainId) {
            showErrorDialogIncompatibleNetwork(
                message.callbackId,
                message.chainId,
                activeNetwork!!.chainId
            )
        } else if (confirmationDialog == null || !confirmationDialog!!.isShowing) {
            confirmationDialog = ActionSheetSignDialog(requireActivity(), this, message)
            confirmationDialog?.show()
        }
    }

    /**
     * 显示 EIP-712 签名时网络不兼容的错误对话框。
     * @param callbackId 回调 ID。
     * @param requestingChainId DApp 请求的链 ID。
     * @param activeChainId 钱包当前激活的链 ID。
     */
    private fun showErrorDialogIncompatibleNetwork(
        callbackId: Long,
        requestingChainId: Long,
        activeChainId: Long
    ) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val message = if (EthereumNetworkBase.isChainSupported(requestingChainId)) {
                getString(
                    R.string.error_eip712_incompatible_network,
                    EthereumNetworkBase.getShortChainName(requestingChainId),
                    EthereumNetworkBase.getShortChainName(activeChainId)
                )
            } else {
                getString(R.string.error_eip712_unsupported_network, requestingChainId.toString())
            }

            errorDialog = AWalletAlertDialog(requireContext(), AWalletAlertDialog.ERROR).apply {
                setMessage(message)
                setButton(R.string.action_cancel) {
                    dismiss()
                    dismissed("", callbackId, false)
                }
                setCancelable(false)
                show()
            }

            viewModel.trackError(Analytics.Error.BROWSER, message)
        }
    }

    /**
     * [ActionSheetCallback] 接口实现：签名成功完成。
     * @param signature 签名结果。
     * @param message 被签名的消息。
     */
    override fun signingComplete(signature: SignatureFromKey, message: Signable) {
        val signHex = Numeric.toHexString(signature.signature)
        Timber.d("Initial Msg: %s", message.message)
        confirmationDialog?.success()
        web3.onSignMessageSuccessful(message, signHex)
    }

    /**
     * [ActionSheetCallback] 接口实现：签名失败。
     * @param error 错误。
     * @param message 试图签名的消息。
     */
    override fun signingFailed(error: Throwable, message: Signable) {
        web3.onSignCancel(message.callbackId)
        confirmationDialog?.dismiss()
    }

    /**
     * [ActionSheetCallback] 接口实现：获取钱包类型。
     */
    override val walletType: WalletType
        get() = wallet.type

    /**
     * [ActionSheetCallback] 接口实现：获取 GasService。
     */
    override val gasService: GasService  = viewModel.getGasService()

    /**
     * [OnSignTransactionListener] 接口实现：处理 `eth_sendTransaction` 请求。
     * @param transaction Web3 交易对象。
     * @param url DApp 的 URL。
     */
    override fun onSignTransaction(transaction: Web3Transaction?, url: String?) {
        if (transaction == null) {
            onInvalidTransaction(null)
            // 我们没有 leafPosition，所以无法调用 onSignCancel。
            // （在原始代码中，如果 transaction 为 null，这里也会崩溃）
            return
        }
        try {
            // 验证交易是否有效
            val isValid = (transaction.recipient == Address.EMPTY && transaction.payload != null) || // 合约部署
                    (transaction.recipient != Address.EMPTY && (transaction.payload != null || transaction.value != null)) // 普通或函数调用

            if ((confirmationDialog == null || !confirmationDialog!!.isShowing) && isValid) {
                val token = viewModel.getTokenService()
                    .getTokenOrBase(activeNetwork!!.chainId, transaction.recipient.toString())
                confirmationDialog = ActionSheetDialog(
                    requireActivity(), transaction, token,
                    "", transaction.recipient.toString(), viewModel.getTokenService(), this
                ).apply {
                    setURL(url)
                    setCanceledOnTouchOutside(false)
                    show()
                    fullExpand()
                }

                // 使用协程异步计算 Gas 估算
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // 假设 calculateGasEstimate 是 suspend 函数
                        val estimate =
                            viewModel.calculateGasEstimate(wallet, transaction, activeNetwork!!.chainId)
                        confirmationDialog?.setGasEstimate(estimate)
                    } catch (e: Throwable) {
                        Timber.e(e) // 打印 Gas 估算错误
                    }
                }
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onInvalidTransaction(transaction)
        web3.onSignCancel(transaction.leafPosition)
    }

    /**
     * 当交易发送失败时由 ViewModel 调用。
     * @param rtn 交易返回结果（包含错误）。
     */
    private fun txError(rtn: TransactionReturn) {
        confirmationDialog?.dismiss()
        rtn.tx.let { web3.onSignCancel(it.leafPosition) }

        resultDialog?.dismiss()
        resultDialog = AWalletAlertDialog(requireContext()).apply {
            setIcon(ERROR)
            setTitle(R.string.error_transaction_failed)
            setMessage(rtn.throwable?.message)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }

        confirmationDialog?.dismiss()
    }

    /**
     * 显示一个通用的错误对话框。
     * @param title 标题资源 ID。
     * @param text 消息资源 ID (如果为 0 则不显示消息)。
     */
    private fun displayError(title: Int, text: Int) {
        resultDialog?.dismiss()
        resultDialog = AWalletAlertDialog(requireContext()).apply {
            setIcon(ERROR)
            setTitle(title)
            if (text != 0) setMessage(text)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
        confirmationDialog?.dismiss()
    }

    /**
     * 显示“观察钱包”不支持签名操作的提示。
     */
    private fun showWalletWatch() {
        resultDialog?.dismiss()
        resultDialog = AWalletAlertDialog(requireContext()).apply {
            setIcon(AWalletAlertDialog.WARNING)
            setTitle(R.string.title_wallet_connect)
            setMessage(R.string.action_watch_account)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 当交易对象无效时，显示错误对话框。
     * @param transaction 无效的交易对象。
     */
    private fun onInvalidTransaction(transaction: Web3Transaction?) {
        if (transaction == null) {
            onInvalidTransaction(null)
            // 我们没有 leafPosition，所以无法调用 onSignCancel。
            // （在原始代码中，如果 transaction 为 null，这里也会崩溃）
            return
        }

        resultDialog = AWalletAlertDialog(requireActivity()).apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(getString(R.string.invalid_transaction))
            val message = when {
                transaction.recipient == Address.EMPTY && (transaction.payload == null || transaction.value != null) ->
                    getString(R.string.contains_no_recipient)
                transaction.payload == null && transaction.value == null ->
                    getString(R.string.contains_no_value)
                else ->
                    getString(R.string.contains_no_data)
            }
            setMessage(message)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            setCancelable(true)
            show()
        }
    }

    /**
     * 处理物理返回按钮或地址栏“后退”按钮。
     */
    override fun backPressed() {
        if (currentFragment != DAPP_BROWSER) {
            detachFragment(currentFragment!!)
        } else if (web3.canGoBack()) {
            setUrlText(getSessionUrl(-1))
            web3.goBack()
            detachFragments()
        } else if (web3.url != getDefaultDappUrl()) {
            homePressed()
            addressBar.updateNavigationButtons(web3.copyBackForwardList())
        }
    }

    /**
     * 导航到 WebView 历史中的下一页。
     */
    private fun goToNextPage() {
        if (web3.canGoForward()) {
            setUrlText(getSessionUrl(1))
            web3.goForward()
        }
    }

    /**
     * 获取 WebView 会话历史中相对位置的 URL。
     * @param relative 相对位置（例如 -1 表示后退一页，1 表示前进一页）。
     * @return URL 字符串，如果索引无效则返回空字符串。
     */
    private fun getSessionUrl(relative: Int): String {
        val sessionHistory: WebBackForwardList = web3.copyBackForwardList()
        val newIndex = sessionHistory.currentIndex + relative
        if (newIndex < sessionHistory.size) {
            val newItem: WebHistoryItem? = sessionHistory.getItemAtIndex(newIndex)
            if (newItem != null) {
                return newItem.url
            }
        }
        return ""
    }

    /**
     * [URLLoadInterface] 接口实现：网页加载时调用（JS 注入后）。
     * @param url 加载的 URL。
     * @param title 网页标题。
     */
    override fun onWebpageLoaded(url: String?, title: String?) {
        context ?: return // Fragment 可能已分离
        if (homePressed) {
            homePressed = false
            if (currentFragment == DAPP_BROWSER && url == getDefaultDappUrl()) {
                web3.clearHistory()
            }
        }

        if (Utils.isValidUrl(url)) {
            val dapp = DApp(title, url)
            DappBrowserUtils.addToHistory(context, dapp)
            addressBar.addSuggestion(dapp)
        }

        onWebpageLoadComplete()
        addressBar.setUrl(url)
    }

    /**
     * [URLLoadInterface] 接口实现：网页加载完成时调用（页面渲染完成）。
     */
    override fun onWebpageLoadComplete() {
        handler.post { // 确保在 UI 线程执行
            addressBar.updateNavigationButtons(web3.copyBackForwardList())
            // 如果是在切换网络后需要加载 URL
            if (loadUrlAfterReload != null) {
                loadUrl(loadUrlAfterReload!!)
                loadUrlAfterReload = null
            }
        }

        if (forceChainChange != 0L) {
            handler.postDelayed({ forceChainChange = 0L }, 5000)
        }
    }

    /**
     * 在浏览器中加载 URL。
     * @param urlText 要加载的 URL。
     * @return true 如果 URL 开始加载，false 如果被阻止（例如未在白名单）。
     */
    private fun loadUrl(urlText: String): Boolean {
        requireContext()
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_URL, urlText)
        }
        viewModel.track(Analytics.Action.LOAD_URL, props)

        // 检查白名单
        if (!viewModel.getDeveloperOverrideState(context) && !DappBrowserUtils.isInDappsList(
                requireContext(),
                urlText
            )
        ) {
            setUrlText(C.ALPHAWALLET_WEB)
            displayError(R.string.title_dialog_error, R.string.not_recommended_to_visit)
            return false
        }

        detachFragments()
        addToBackStack(DAPP_BROWSER)
        cancelSearchSession()

        // 检查是否为 MagicLink
        if (checkForMagicLink(urlText)) {
            return true
        } else if (handlePrefix(urlText)) { // 检查是否为自定义协议
            return true
        }

        web3.resetView()
        web3.loadUrl(Utils.formatUrl(urlText))
        setUrlText(Utils.formatUrl(urlText))
        web3.requestFocus()
        parentFragmentManager.setFragmentResult(RESET_TOOLBAR, Bundle())
        return true
    }

    /**
     * 直接加载 URL（通常由 HomeActivity 调用）。
     * @param urlText 要加载的 URL。
     */
    fun loadDirect(urlText: String) {
        if (!::web3.isInitialized) {
            activity?.recreate()
            loadOnInit = urlText
        } else {
            loadOnInit = null
            cancelSearchSession()
            addToBackStack(DAPP_BROWSER)
            setUrlText(Utils.formatUrl(urlText))
            web3.resetView()
            web3.loadUrl(Utils.formatUrl(urlText))
            addressBar.leaveEditMode()
            web3.requestFocus()

            val props = AnalyticsProperties().apply {
                put(Analytics.PROPS_URL, urlText)
            }
            viewModel.track(Analytics.Action.LOAD_URL, props)
        }
    }

    /**
     * 重新加载当前页面。
     */
    fun reloadPage() {
        if (currentFragment == DAPP_BROWSER) {
            refresh?.isEnabled = false
            web3.resetView()
            web3.reload()
            viewModel.track(Analytics.Action.RELOAD_BROWSER)
        }
    }

    /**
     * 重置 DApp 浏览器到默认主页。
     */
    private fun resetDappBrowser() {
        web3.clearHistory()
        web3.stopLoading()
        web3.resetView()
        val defaultUrl = getDefaultDappUrl()
        web3.loadUrl(defaultUrl)
        setUrlText(defaultUrl)
    }

    /**
     * [ItemClickListener] 接口实现：处理来自 QR 扫描仪的结果。
     * @param resultCode Activity 结果码。
     * @param data 返回的 Intent 数据。
     * @param messenger Fragment 信使（未使用）。
     */
    override fun handleQRCode(resultCode: Int, data: Intent?, messenger: FragmentMessenger?) {
        var qrCode: String?
        try {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    qrCode = data?.getStringExtra(C.EXTRA_QR_CODE)
                    if (qrCode == null || checkForMagicLink(qrCode)) return

                    val props = AnalyticsProperties()
                    val parser =
                        QRParser.getInstance(EthereumNetworkRepository.extraChainsCompat())
                    val result = parser.parse(qrCode)
                        ?: throw Exception("Invalid QR Code") // 确保 result 不为 null

                    when (result.type) {
                        EIP681Type.ADDRESS -> {
                            props.put(QrScanResultType.KEY, QrScanResultType.ADDRESS.value)
                            viewModel.track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                            copyToClipboard(result.address.toString())
                        }
                        EIP681Type.PAYMENT, EIP681Type.TRANSFER -> {
                            props.put(
                                QrScanResultType.KEY,
                                QrScanResultType.ADDRESS_OR_EIP_681.value
                            )
                            viewModel.track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                            viewModel.showSend(context, result)
                        }
                        EIP681Type.FUNCTION_CALL -> {
                            props.put(
                                QrScanResultType.KEY,
                                QrScanResultType.ADDRESS_OR_EIP_681.value
                            )
                            viewModel.track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                            // TODO: 处理 EIP-681 函数调用
                        }
                        EIP681Type.URL -> {
                            props.put(QrScanResultType.KEY, QrScanResultType.URL.value)
                            viewModel.track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                            loadUrlRemote(qrCode)
                        }
                        EIP681Type.OTHER -> {
                            throw Exception("Invalid QR Code")
                        }

                        else -> {}
                    }
                }
                QRScannerActivity.DENY_PERMISSION -> showCameraDenied()
                QRScannerActivity.WALLET_CONNECT -> return
            }
        } catch (e: Exception) {
            Timber.e(e)
            if (activity != null) {
                Toast.makeText(activity, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 从非 UI 线程加载 URL。
     * @param qrCode 要加载的 URL。
     */
    private fun loadUrlRemote(qrCode: String) {
        handler.post { loadUrl(qrCode) }
    }

    /**
     * 显示相机权限被拒绝的提示。
     */
    private fun showCameraDenied() {
        activity ?: return
        resultDialog = AWalletAlertDialog(requireActivity()).apply {
            setTitle(R.string.title_dialog_error)
            setMessage(R.string.error_camera_permission_denied)
            setIcon(ERROR)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * 将地址复制到剪贴板。
     * @param address 要复制的地址。
     */
    private fun copyToClipboard(address: String) {
        val clipboard =
            activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(MyAddressActivity.KEY_ADDRESS, address)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    /**
     * 检查 URL 是否为 MagicLink（例如代币导入链接）。
     * @param data 要检查的 URL 或数据。
     * @return true 如果是 MagicLink 并已处理，否则 false。
     */
    private fun checkForMagicLink(data: String): Boolean {
        try {
            val parser =
                ParseMagicLink(CryptoFunctions(), EthereumNetworkRepository.extraChainsCompat())
            if (parser.parseUniversalLink(data).chainId > 0) {
                viewModel.showImportLink(activity, data)
                return true
            }
        } catch (e: SalesOrderMalformed) {
            // 不是 MagicLink
        }
        return false
    }

    /**
     * 检查读取外部存储的权限。
     * @return true 如果有权限，否则 false 并请求权限。
     */
    private fun checkReadPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireActivity().applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            activity?.requestPermissions(permissions, REQUEST_FILE_ACCESS)
            false
        }
    }

    /**
     * 请求地理位置权限。
     * @param origin 请求权限的 URL 来源。
     * @param callback 地理位置权限回调。
     */
    private fun requestGeoPermission(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        if (ContextCompat.checkSelfPermission(
                requireContext().applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            geoCallback = callback
            geoOrigin = origin
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            activity?.requestPermissions(permissions, REQUEST_FINE_LOCATION)
        } else {
            callback.invoke(origin, true, false)
        }
    }

    /**
     * 请求相机权限。
     * @param request 权限请求。
     */
    private fun requestCameraPermission(request: PermissionRequest) {
        val requestedResources = request.resources
        requestCallback = request
        for (r in requestedResources) {
            if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                val permissions = arrayOf(Manifest.permission.CAMERA)
                activity?.requestPermissions(permissions, REQUEST_CAMERA_ACCESS)
            }
        }
    }

    /**
     * [BaseFragment] 回调：相机权限结果。
     */
    override fun gotCameraAccess(permissions: Array<String>?, grantResults: IntArray?) {
        var cameraAccess = false
        permissions?.forEachIndexed { i, permission ->
            if (permission == Manifest.permission.CAMERA && grantResults?.get(i) != -1) {
                cameraAccess = true
                requestCallback?.grant(requestCallback!!.resources) // 授予网页权限
            }
        }
        if (!cameraAccess) {
            Toast.makeText(context, "Permission not given", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * [BaseFragment] 回调：地理位置权限结果。
     */
    override fun gotGeoAccess(permissions: Array<String>?, grantResults: IntArray?) {
        var geoAccess = false
        permissions?.forEachIndexed { i, permission ->
            if (permission == Manifest.permission.ACCESS_FINE_LOCATION && grantResults?.get(i) != -1) {
                geoAccess = true
            }
        }
        if (!geoAccess) {
            Toast.makeText(context, "Permission not given", Toast.LENGTH_SHORT).show()
        }
        geoCallback?.invoke(geoOrigin, geoAccess, false)
    }

    /**
     * [BaseFragment] 回调：文件访问权限结果。
     */
    override fun gotFileAccess(permissions: Array<String>?, grantResults: IntArray?) {
        var fileAccess = false
        permissions?.forEachIndexed { i, permission ->
            if (permission == Manifest.permission.READ_EXTERNAL_STORAGE && grantResults?.get(i) != -1) {
                fileAccess = true
            }
        }
        if (fileAccess) requestUpload()
    }

    /**
     * Fragment 的 onSaveInstanceState 生命周期方法。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        web3.saveState(outState)
        // 序列化 bundle 并存储到本地
        writeBundleToLocalStorage(outState)
        super.onSaveInstanceState(outState)
    }

    /**
     * 将 WebView 的状态 Bundle 写入本地文件。
     * @param bundle 要写入的 Bundle。
     */
    private fun writeBundleToLocalStorage(bundle: Bundle) {
        val file = File(requireContext().filesDir, BUNDLE_FILE)
        try {
            FileOutputStream(file).use { fos ->
                getSerialisedBundle(bundle)?.writeTo(fos)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing bundle to local storage")
        }
    }

    /**
     * 序列化 Bundle 以便存储。
     * @param bundle 要序列化的 Bundle。
     * @return 包含序列化数据的 ByteArrayOutputStream。
     */
    private fun getSerialisedBundle(bundle: Bundle): ByteArrayOutputStream? {
        return try {
            val bos = ByteArrayOutputStream()
            ObjectOutputStream(bos).use { oos ->
                oos.writeObject(MAGIC_BUNDLE_VAL)
                for (key in bundle.keySet()) {
                    val item = bundle.get(key)
                    if (item is Serializable) {
                        oos.writeObject(key)
                        try {
                            oos.writeObject(item)
                        } catch (e: Exception) {
                            oos.writeObject(0) // 写入失败的标记
                        }
                    }
                }
                oos.writeObject(CURRENT_FRAGMENT)
                oos.writeObject(currentFragment)
                oos.writeObject(CURRENT_URL)
                oos.writeObject(addressBar.getUrl())
            }
            bos
        } catch (e: Exception) {
            Timber.e(e, "Error serialising bundle")
            null
        }
    }

    /**
     * 从本地文件读取 WebView 状态 Bundle。
     * @return 恢复的 Bundle，如果失败则返回 null。
     */
    private fun readBundleFromLocal(): Bundle? {
        val file = File(requireContext().filesDir, BUNDLE_FILE)
        if (!file.exists()) return null

        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { oos ->
                    val check = oos.readObject()
                    if ((MAGIC_BUNDLE_VAL as? Long) != check) {
                        return null // 文件魔术值不匹配
                    }

                    val bundle = Bundle()
                    while (fis.available() > 0) {
                        val key = oos.readObject() as String
                        val value = oos.readObject()
                        if (value is Serializable && (value as? Int) != 0) {
                            bundle.putSerializable(key, value)
                        }
                    }
                    bundle
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading bundle from local storage")
            null
        }
    }

    /**
     * [DappBrowserSwipeInterface] 接口实现：处理下拉刷新事件。
     */
    override fun RefreshEvent() {
        // 仅当 WebView 滚动到顶部时才允许刷新
        if (web3.scrollY == 0) {
            loadUrl(web3.url!!)
        }
    }

    /**
     * [DappBrowserSwipeInterface] 接口实现：获取当前滚动位置。
     */
    override fun getCurrentScrollPosition(): Int {
        return web3.scrollY
    }

    /**
     * [ActionSheetCallback] 接口实现：处理切换网络弹窗的确认按钮点击。
     * @param callbackId 回调 ID。
     * @param baseToken 目标网络的基准代币。
     */
    override fun buttonClick(callbackId: Long, baseToken: Token?) {
        confirmationDialog?.dismiss()
        baseToken?.tokenInfo?.chainId?.let {
            loadNewNetwork(it)
        }

        web3.onWalletActionSuccessful(callbackId, null)
    }

    /**
     * [ActionSheetCallback] 接口实现：获取用户授权（例如 PIN 码或指纹）。
     * @param callback 授权回调。
     */
    override fun getAuthorisation(callback: SignAuthenticationCallback?) {
        viewModel.getAuthorisation(wallet, activity, callback)
    }

    /**
     * [ActionSheetCallback] 接口实现：发送交易（签名 + 发送）。
     * @param finalTx 最终的 Web3 交易对象。
     */
    override fun sendTransaction(finalTx: Web3Transaction?) {
        viewModel.requestSignature(finalTx, wallet, activeNetwork!!.chainId)
    }

    /**
     * [ActionSheetCallback] 接口实现：完成交易发送（已签名）。
     * @param tx Web3 交易对象。
     * @param signature 签名结果。
     */
    override fun completeSendTransaction(tx: Web3Transaction?, signature: SignatureFromKey?) {
        viewModel.sendTransaction(wallet, activeNetwork!!.chainId, tx, signature)
    }

    /**
     * [ActionSheetCallback] 接口实现：仅签名交易。
     * @param tx Web3 交易对象。
     */
    override fun signTransaction(tx: Web3Transaction?) {
        viewModel.requestSignatureOnly(tx, wallet, activeNetwork!!.chainId)
    }

    /**
     * [ActionSheetCallback] 接口实现：完成交易签名（仅签名）。
     * @param w3Tx Web3 交易对象。
     * @param signature 签名结果。
     */
    override fun completeSignTransaction(w3Tx: Web3Transaction?, signature: SignatureFromKey?) {
        viewModel.signTransaction(activeNetwork!!.chainId, w3Tx, signature)
    }

    /**
     * [ActionSheetCallback] 接口实现：PIN 码授权结果。
     * @param gotAuth 是否获得授权。
     */
    override fun pinAuthorisation(gotAuth: Boolean) {
        confirmationDialog?.gotAuthorisation(gotAuth)
    }

    /**
     * 当交易成功写入区块链时由 ViewModel 调用。
     * @param txData 交易返回数据（包含 txHash）。
     */
    private fun txWritten(txData: TransactionReturn) {
        confirmationDialog?.transactionWritten(txData.hash)
        web3.onSignTransactionSuccessful(txData)
    }

    /**
     * 当交易（仅签名）成功时由 ViewModel 调用。
     * @param txData 交易返回数据（包含签名）。
     */
    private fun txSigned(txData: TransactionReturn) {
        confirmationDialog?.transactionWritten(txData.displayData)
        web3.onSignTransactionSuccessful(txData)
    }

    /**
     * [ActionSheetCallback] 接口实现：当 ActionSheet 被关闭时调用。
     * @param txHash 交易哈希（如果适用）。
     * @param callbackId 回调 ID。
     * @param actionCompleted 动作是否已完成。
     */
    override fun dismissed(txHash: String?, callbackId: Long, actionCompleted: Boolean) {
        if (!actionCompleted) {
            // 用户取消了签名
            web3.onSignCancel(callbackId)
        }
    }

    /**
     * [ActionSheetCallback] 接口实现：通知确认按钮被点击（用于分析）。
     * @param mode ActionSheet 的模式。
     */
    override fun notifyConfirm(mode: String?) {
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_ACTION_SHEET_MODE, mode)
            put(Analytics.PROPS_ACTION_SHEET_SOURCE, ActionSheetSource.BROWSER)
        }
        viewModel.track(Analytics.Action.ACTION_SHEET_COMPLETED, props)
    }

    /**
     * [ActionSheetCallback] 接口实现：获取 Gas 设置的 ActivityResultLauncher。
     */
    override fun gasSelectLauncher(): ActivityResultLauncher<Intent> {
        return getGasSettings
    }

    /**
     * 决定文件选择器要使用的 MIME 类型。
     * @param fileChooserParams 文件选择器参数。
     * @return MIME 类型字符串。
     */
    private fun determineMimeType(fileChooserParams: WebChromeClient.FileChooserParams?): String {
        if (fileChooserParams == null || fileChooserParams.acceptTypes.isEmpty()) {
            return "*/*" // 允许所有类型
        }
        val firstType = fileChooserParams.acceptTypes[0]

        return if (fileChooserParams.acceptTypes.size == 1) {
            firstType
        } else {
            // 尝试解析常见的 MIME 类型
            when (firstType) {
                "png", "gif", "svg", "jpg", "jpeg", "bmp" -> "image/$firstType"
                "mp4", "x-msvideo", "x-ms-wmv", "mpeg4-generic", "webm", "avi", "mpg", "m2v" -> "video/$firstType"
                "image/*", "audio/*", "video/*" -> firstType
                "mpeg", "aac", "wav", "ogg", "midi", "x-ms-wma" -> "audio/$firstType"
                "pdf" -> "application/*"
                "xml", "csv" -> "text/$firstType"
                else -> "*/*"
            }
        }
    }

    /**
     * 获取默认的 DApp URL（主页）。
     * @return URL 字符串。
     */
    private fun getDefaultDappUrl(): String {
        val customHome = viewModel.getHomePage(context)
        val chainId = activeNetwork?.chainId ?: 0
        return customHome ?: DappBrowserUtils.defaultDapp(chainId)
    }

    /**
     * [TestNetDialog.TestNetDialogCallback] 接口实现：测试网对话框关闭。
     */
    override fun onTestNetDialogClosed() {
        // 用户取消了切换到测试网
    }

    /**
     * [TestNetDialog.TestNetDialogCallback] 接口实现：测试网对话框确认。
     * @param newChainId 目标测试网的链 ID。
     */
    override fun onTestNetDialogConfirmed(newChainId: Long) {
        // 用户同意切换到测试网，直接加载新网络
        viewModel.getNetworkInfo(newChainId)?.let {
            loadNewNetwork(newChainId)
        }
    }
}
