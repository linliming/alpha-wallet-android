package com.alphawallet.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Nullable
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
import com.alphawallet.app.C.RESET_WALLET
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
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.attestation.AttestationImportInterface
import com.alphawallet.app.entity.attestation.SmartPassReturn
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.router.ImportTokenRouter
import com.alphawallet.app.service.DeepLinkService
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.PriceAlertsService
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.ui.widget.entity.PagerCallback
import com.alphawallet.app.util.CoroutineUtils.launchSafely
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
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import org.web3j.utils.Numeric
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivityKt :
    BaseNavigationActivity(),
    View.OnClickListener,
    HomeCommsInterface,
    FragmentMessenger,
    Runnable,
    ActionSheetCallback,
    LifecycleEventObserver,
    PagerCallback,
    AttestationImportInterface {
    @Inject
    lateinit var awWalletConnectClient: AWWalletConnectClient

    companion object {
        const val RC_ASSET_EXTERNAL_WRITE_PERM = 223
        const val RC_ASSET_NOTIFICATION_PERM = 224
        const val DAPP_BARCODE_READER_REQUEST_CODE = 1
        const val STORED_PAGE = "currentPage"
        const val RESET_TOKEN_SERVICE = "HOME_reset_ts"
        const val AW_MAGICLINK_DIRECT = "openurl?url="

        private var updatePrompt = false

        fun setUpdatePrompt() {
            updatePrompt = true
        }
    }

    private lateinit var viewModel: HomeViewModel
    private lateinit var viewModelWC: WalletConnectViewModel
    private var dialog: Dialog? = null
    private lateinit var viewPager: ViewPager2
    private lateinit var successOverlay: LinearLayout
    private lateinit var successImage: ImageView
    private var homeReceiver: HomeReceiver? = null
    private var walletTitle: String? = null
    private var backupWalletDialog: TutoShowcase? = null
    private var isForeground = false

    @Volatile
    private var tokenClicked = false
    private var openLink: String? = null
    private var openToken: String? = null
    private var wcProgressDialog: AWalletAlertDialog? = null

    private val handler = Handler(Looper.getMainLooper())
    private val pager2Adapter = ScreenSlidePagerAdapter(this)

    private val networkSettingsHandler =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            supportFragmentManager.setFragmentResult(RESET_TOKEN_SERVICE, Bundle())
        }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                launchSafely(
                    scope = lifecycleScope,
                    onError = { throwable: Throwable? -> Timber.e(throwable) },
                ) {
                    viewModel.subscribeToNotifications()
                }
            } else {
                Toast
                    .makeText(
                        this,
                        getString(R.string.message_deny_request_post_notifications_permission),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }

    private val qrCodeScanner =
        registerForActivityResult(ScanContract()) { result ->
            when {
                result.contents == null -> {
                    Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
                }

                viewModel.requiresProcessing(result.contents) -> {
                    viewModel.handleQRCode(this@HomeActivityKt, result.contents)
                }
            }
        }

    private val getGasSettings =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            // awWalletConnectClient.setCurrentGasIndex(result)
        }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                // No action needed
            }

            Lifecycle.Event.ON_START -> {
                Timber.tag("LIFE").d("AlphaWallet into foreground")
                handler.postDelayed({
                    viewModel?.let { it.checkTransactionEngine() }
                }, 5000)
                isForeground = true
            }

            Lifecycle.Event.ON_RESUME -> {
                // No action needed
            }

            Lifecycle.Event.ON_PAUSE -> {
                // No action needed
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.tag("LIFE").d("AlphaWallet into background")
                if (!tokenClicked) viewModel.stopTransactionUpdate()
                viewModel.outOfFocus()
                isForeground = false
            }

            Lifecycle.Event.ON_DESTROY -> {
                // No action needed
            }

            Lifecycle.Event.ON_ANY -> {
                // No action needed
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

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

    @SuppressLint("RestrictedApi")
    override fun onCreate(
        @Nullable savedInstanceState: Bundle?,
    ) {
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
        viewPager.isUserInputEnabled = false
        viewPager.adapter = pager2Adapter
        viewPager.offscreenPageLimit = WalletPage.values().size
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int,
                ) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                }
            },
        )

        initBottomNavigation()
        disableDisplayHomeAsUp()

        viewModel.error().observe(this) { this.onError(it) }
        viewModel.walletName().observe(this) { this.onWalletName(it) }
        viewModel.backUpMessage().observe(this) { this.onBackup(it) }
        viewModel.splashReset().observe(this) { this.onRequireInit(it) }
        viewModel.defaultWallet().observe(this) { this.onDefaultWallet(it) }
        viewModel.updateAvailable().observe(this) { this.onUpdateAvailable(it) }

        if (CustomViewSettings.hideDappBrowser()) {
            removeDappBrowser()
        }

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
        viewModel.tryToShowEmailPrompt(this, successOverlay, handler, this)

        if (Utils.verifyInstallerId(this)) {
            UpdateUtils.checkForUpdates(this, this)
        } else {
            if (MediaLinks.isMediaTargeted(applicationContext)) {
                viewModel.checkLatestGithubRelease()
            }
        }

        setupFragmentListeners()

        val intent = intent
        val data = intent.data
        if (intent.hasExtra(C.FROM_HOME_ROUTER) &&
            intent.getStringExtra(C.FROM_HOME_ROUTER) == C.FROM_HOME_ROUTER
        ) {
            viewModel.storeCurrentFragmentId(-1)
        }

        data?.let { handleDeeplink(it.toString(), intent) }

        val i = Intent(this, PriceAlertsService::class.java)
        try {
            startService(i)
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    private fun onUpdateAvailable(availableVersion: String) {
        externalUpdateReady(availableVersion)
    }

    private fun setWCConnect() {
        try {
            awWalletConnectClient.setCallback(this)
        } catch (e: Exception) {
            Timber.tag("WalletConnect").e(e)
        }
    }

    private fun onDefaultWallet(wallet: Wallet) {
        if (viewModel.checkNewWallet(wallet.address)) {
            viewModel.setNewWallet(wallet.address, false)
            val selectNetworkIntent = Intent(this, NetworkToggleActivity::class.java)
            selectNetworkIntent.putExtra(C.EXTRA_SINGLE_ITEM, false)
            networkSettingsHandler.launch(selectNetworkIntent)
        }
    }

    private fun setupFragmentListeners() {
        supportFragmentManager.setFragmentResultListener(RESET_TOKEN_SERVICE, this) { _, _ ->
            viewModel.restartTokensService()
            resetTokens()
        }

        supportFragmentManager.setFragmentResultListener(C.RESET_WALLET, this) { _, _ ->
            viewModel.restartTokensService()
            resetTokens()
            showPage(WalletPage.WALLET)
        }

        supportFragmentManager.setFragmentResultListener(C.CHANGE_CURRENCY, this) { _, _ ->
            resetTokens()
            showPage(WalletPage.WALLET)
        }

        supportFragmentManager.setFragmentResultListener(C.RESET_TOOLBAR, this) { _, _ ->
            invalidateOptionsMenu()
        }

        supportFragmentManager.setFragmentResultListener(C.ADDED_TOKEN, this) { _, b ->
            val contractList = b.getParcelableArrayList<ContractLocator>(C.ADDED_TOKEN)
            contractList?.let {
                getFragment(WalletPage.ACTIVITY).addedToken(it)
            }
        }

        supportFragmentManager.setFragmentResultListener(C.SHOW_BACKUP, this) { _, b ->
            showBackupWalletDialog(b.getBoolean(C.SHOW_BACKUP, false))
        }

        supportFragmentManager.setFragmentResultListener(C.HANDLE_BACKUP, this) { _, b ->
            if (b.getBoolean(C.HANDLE_BACKUP)) {
                backupWalletSuccess(b.getString("Key"))
            } else {
                backupWalletFail(b.getString("Key"), b.getBoolean("nolock"))
            }
        }

        supportFragmentManager.setFragmentResultListener(C.TOKEN_CLICK, this) { _, _ ->
            tokenClicked = true
            handler.postDelayed({ tokenClicked = false }, 10000)
        }

        supportFragmentManager.setFragmentResultListener(C.CHANGED_LOCALE, this) { _, _ ->
            viewModel.restartHomeActivity(applicationContext)
        }

        supportFragmentManager.setFragmentResultListener(C.SETTINGS_INSTANTIATED, this) { _, _ ->
            loadingComplete()
        }

        supportFragmentManager.setFragmentResultListener(C.QRCODE_SCAN, this) { _, _ ->
            val options = Utils.getQRScanOptions(this)
            hideDialog()
            qrCodeScanner.launch(options)
        }

        supportFragmentManager.setFragmentResultListener(C.AWALLET_CODE, this) { _, b ->
            val code = b.getString(C.AWALLET_CODE)
            if (code != null) {
                handleDeeplink(code, null)
            }
        }
    }

    override fun onNewIntent(startIntent: Intent) {
        super.onNewIntent(startIntent)
        val data = startIntent.data
        data?.let { handleDeeplink(it.toString(), startIntent) }
    }

    private fun onRequireInit(aBoolean: Boolean) {
        val intent = Intent(this, SplashActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun onBackup(address: String) {
        if (Utils.isAddressValid(address)) {
            Toast.makeText(this, getString(R.string.postponed_backup_warning), Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        successOverlay = findViewById(R.id.layout_success_overlay)
        successImage = findViewById(R.id.success_image)

        successOverlay.setOnClickListener {
            successOverlay.visibility = View.GONE
        }
    }

    private fun showBackupWalletDialog(walletImported: Boolean) {
        if (!viewModel.isFindWalletAddressDialogShown) {
            if (!walletImported) {
                val background = ContextCompat.getColor(applicationContext, R.color.translucent_dark)
                val statusBarColor = window.statusBarColor
                backupWalletDialog = TutoShowcase.from(this)
                backupWalletDialog
                    ?.setContentView(R.layout.showcase_backup_wallet)
                    ?.setBackgroundColor(background)
                    ?.onClickContentView(R.id.btn_close) {
                        window.statusBarColor = statusBarColor
                        backupWalletDialog?.dismiss()
                    }?.onClickContentView(R.id.showcase_layout) {
                        window.statusBarColor = statusBarColor
                        backupWalletDialog?.dismiss()
                    }?.on(R.id.settings_tab)
                    ?.addCircle()
                    ?.onClick {
                        window.statusBarColor = statusBarColor
                        backupWalletDialog?.dismiss()
                        showPage(WalletPage.SETTINGS)
                    }
                backupWalletDialog?.show()
                window.statusBarColor = background
            }
            viewModel.isFindWalletAddressDialogShown = true
        }
    }

    private fun onWalletName(name: String?) {
        walletTitle = if (!name.isNullOrEmpty()) name else getString(R.string.toolbar_header_wallet)
        getFragment(WalletPage.WALLET).setToolbarTitle(walletTitle)
    }

    private fun onError(errorEnvelope: ErrorEnvelope?) {
        // Handle error
    }

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
        initViews()
    }

    override fun onPause() {
        super.onPause()
        dialog?.let { if (it.isShowing) it.dismiss() }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt(STORED_PAGE, viewPager.currentItem)
        getSelectedItem()?.let { viewModel.storeCurrentFragmentId(it.ordinal) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val oldPage = savedInstanceState.getInt(STORED_PAGE)
        if (oldPage >= 0 && oldPage < WalletPage.values().size) {
            showPage(WalletPage.values()[oldPage])
        }
    }

    override fun onClick(view: View) {
        // Handle click events
    }

    override fun onBottomNavigationItemSelected(index: WalletPage): Boolean =
        when (index) {
            WalletPage.DAPP_BROWSER -> {
                showPage(WalletPage.DAPP_BROWSER)
                true
            }

            WalletPage.WALLET -> {
                showPage(WalletPage.WALLET)
                true
            }

            WalletPage.SETTINGS -> {
                showPage(WalletPage.SETTINGS)
                true
            }

            WalletPage.ACTIVITY -> {
                showPage(WalletPage.ACTIVITY)
                true
            }
        }

    fun onBrowserWithURL(url: String) {
        showPage(WalletPage.DAPP_BROWSER)
        getFragment(WalletPage.DAPP_BROWSER).onItemClick(url)
    }

    override fun onDestroy() {
        getSelectedItem()?.let { viewModel.storeCurrentFragmentId(it.ordinal) }
        super.onDestroy()
        viewModel.onClean()
        homeReceiver?.let {
            it.unregister(this)
            homeReceiver = null
        }
    }

    override fun getGasService(): GasService = viewModel.getGasService()

    private fun showPage(page: WalletPage) {
        val oldPage = WalletPage.values()[viewPager.currentItem]
        var enableDisplayAsHome = false

        when (page) {
            WalletPage.DAPP_BROWSER -> {
                hideToolbar()
                setTitle(getString(R.string.toolbar_header_browser))
                selectNavigationItem(WalletPage.DAPP_BROWSER)
                enableDisplayAsHome = true
            }

            WalletPage.WALLET -> {
                showToolbar()
                setTitle(walletTitle ?: getString(R.string.toolbar_header_wallet))
                selectNavigationItem(WalletPage.WALLET)
            }

            WalletPage.SETTINGS -> {
                showToolbar()
                setTitle(getString(R.string.toolbar_header_settings))
                selectNavigationItem(WalletPage.SETTINGS)
            }

            WalletPage.ACTIVITY -> {
                showToolbar()
                setTitle(getString(R.string.activity_label))
                selectNavigationItem(WalletPage.ACTIVITY)
            }
        }

        enableDisplayHomeAsHome(enableDisplayAsHome)
        switchAdapterToPage(page)
        invalidateOptionsMenu()
        checkWarnings()

        signalPageVisibilityChange(oldPage, page)
    }

    private fun switchAdapterToPage(page: WalletPage) {
        handler.post { viewPager.setCurrentItem(page.ordinal, false) }
    }

    private fun signalPageVisibilityChange(
        oldPage: WalletPage,
        newPage: WalletPage,
    ) {
        val inFocus = getFragment(newPage)
        inFocus.comeIntoFocus()

        if (oldPage != newPage) {
            val leavingFocus = getFragment(oldPage)
            leavingFocus.leaveFocus()
        }
    }

    private fun checkWarnings() {
        if (updatePrompt) {
            hideDialog()
            updatePrompt = false
            val warns = viewModel.updateWarnings + 1
            if (warns < 3) {
                val cDialog = AWalletConfirmationDialog(this)
                cDialog.setTitle(R.string.alphawallet_update)
                cDialog.setCancelable(true)
                cDialog.setSmallText("Using an old version of Alphawallet. Please update from the Play Store or Alphawallet website.")
                cDialog.setPrimaryButtonText(R.string.ok)
                cDialog.setPrimaryButtonListener { cDialog.dismiss() }
                dialog = cDialog
                dialog?.show()
            } else if (warns > 10) {
                viewModel.setUpdateWarningCount(0)
            } else {
                viewModel.setUpdateWarningCount(warns)
            }
        }
    }

    override fun playStoreUpdateReady(updateVersion: Int) {
        getFragment(WalletPage.SETTINGS).signalPlayStoreUpdate(updateVersion)
    }

    override fun externalUpdateReady(updateVersion: String) {
        getFragment(WalletPage.SETTINGS).signalExternalUpdate(updateVersion)
    }

    override fun tokenScriptError(message: String) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            hideDialog()
            val aDialog = AWalletAlertDialog(this)
            aDialog.setTitle(getString(R.string.tokenscript_file_error))
            aDialog.setMessage(message)
            aDialog.setIcon(AWalletAlertDialog.ERROR)
            aDialog.setButtonText(R.string.button_ok)
            aDialog.setButtonListener { aDialog.dismiss() }
            dialog = aDialog
            dialog?.show()
        }, 500)
    }

    fun backupWalletFail(
        keyBackup: String?,
        hasNoLock: Boolean,
    ) {
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(hasNoLock)
        keyBackup?.let {
            getFragment(WalletPage.WALLET).remindMeLater(Wallet(it))
            viewModel.checkIsBackedUp(it)
        }
    }

    fun backupWalletSuccess(keyBackup: String?) {
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(false)
        keyBackup?.let { getFragment(WalletPage.WALLET).storeWalletBackupTime(it) }
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
        successImage?.setImageResource(R.drawable.big_green_tick)
        successOverlay?.visibility = View.VISIBLE
        handler.postDelayed(this, 1000)
    }

    override fun run() {
        if (successOverlay.alpha > 0) {
            successOverlay.animate().alpha(0.0f).duration = 500
            handler.postDelayed(this, 750)
        } else {
            successOverlay.visibility = View.GONE
            successOverlay.alpha = 1.0f
        }
    }

    override fun loadingComplete() {
        val lastId = viewModel.lastFragmentId
        when {
            !openLink.isNullOrEmpty() -> {
                showPage(WalletPage.DAPP_BROWSER)
                getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(0, openLink)
                openLink = null
                viewModel.storeCurrentFragmentId(-1)
            }

            !openToken.isNullOrEmpty() -> {
                showPage(WalletPage.WALLET)
                getFragment(WalletPage.WALLET).setImportFilename(openToken)
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

    private fun getFragment(page: WalletPage): BaseFragment =
        if ((page.ordinal + 1) > supportFragmentManager.fragments.size) {
            recreate()
            BaseFragment()
        } else {
            supportFragmentManager.fragments[page.ordinal] as BaseFragment
        }

    override fun requestNotificationPermission() {
        checkNotificationPermission(RC_ASSET_NOTIFICATION_PERM)
    }

    override fun backupSuccess(keyAddress: String) {
        if (Utils.isAddressValid(keyAddress)) backupWalletSuccess(keyAddress)
    }

    override fun resetTokens() {
        getFragment(WalletPage.ACTIVITY).resetTokens()
        getFragment(WalletPage.WALLET).resetTokens()
    }

    override fun resetTransactions() {
        getFragment(WalletPage.ACTIVITY).resetTransactions()
    }

    private fun hideDialog() {
        dialog?.let { if (it.isShowing) it.dismiss() }
        wcProgressDialog?.let { if (it.isShowing) it.dismiss() }
    }

    private fun checkNotificationPermission(permissionTag: Int) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    arrayOf(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                }
            requestPermissions(permissions, permissionTag)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            DappBrowserFragment.REQUEST_CAMERA_ACCESS -> {
                getFragment(WalletPage.DAPP_BROWSER).gotCameraAccess(permissions, grantResults)
            }

            DappBrowserFragment.REQUEST_FILE_ACCESS -> {
                getFragment(WalletPage.DAPP_BROWSER).gotFileAccess(permissions, grantResults)
            }

            DappBrowserFragment.REQUEST_FINE_LOCATION -> {
                getFragment(WalletPage.DAPP_BROWSER).gotGeoAccess(permissions, grantResults)
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        val actualRequestCode =
            if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS &&
                requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10
            ) {
                SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS
            } else {
                requestCode
            }

        when (actualRequestCode) {
            DAPP_BARCODE_READER_REQUEST_CODE -> {
                getFragment(WalletPage.DAPP_BROWSER).handleQRCode(resultCode, data, this)
            }

            SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> {
                if (getSelectedItem() == WalletPage.DAPP_BROWSER) {
                    getFragment(WalletPage.DAPP_BROWSER).pinAuthorisation(resultCode == RESULT_OK)
                }
            }

            C.REQUEST_BACKUP_WALLET -> {
                val keyBackup = data?.getStringExtra("Key")
                val noLockScreen = data?.getBooleanExtra("nolock", false) ?: false
                if (resultCode == RESULT_OK) {
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
                            viewModel.handleQRCode(this, qrCode)
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
                when {
                    data != null && resultCode == Activity.RESULT_OK && data.hasExtra(C.DAPP_URL_LOAD) -> {
                        getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(
                            data.getLongExtra(C.EXTRA_CHAIN_ID, MAINNET_ID),
                            data.getStringExtra(C.DAPP_URL_LOAD),
                        )
                        showPage(WalletPage.DAPP_BROWSER)
                    }

                    data != null && resultCode == Activity.RESULT_OK && data.hasExtra(C.EXTRA_TXHASH) -> {
                        showPage(WalletPage.ACTIVITY)
                    }
                }
            }

            C.TERMINATE_ACTIVITY -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    getFragment(WalletPage.ACTIVITY).scrollToTop()
                    showPage(WalletPage.ACTIVITY)
                }
            }

            C.ADDED_TOKEN_RETURN -> {
                when {
                    data?.hasExtra(C.EXTRA_TOKENID_LIST) == true -> {
                        val tokenData = data.getParcelableArrayListExtra<ContractLocator>(C.EXTRA_TOKENID_LIST)
                        getFragment(WalletPage.ACTIVITY).addedToken(tokenData)
                    }

                    data?.getBooleanExtra(RESET_WALLET, false) == true -> {
                        viewModel.restartTokensService()
                        resetTokens()
                    }
                }
            }
        }
    }

    fun postponeWalletBackupWarning(walletAddress: String) {
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
    }

    override fun onBackPressed() {
        when {
            viewPager.currentItem == WalletPage.DAPP_BROWSER.ordinal -> {
                getFragment(WalletPage.DAPP_BROWSER).backPressed()
            }

            viewPager.currentItem != WalletPage.WALLET.ordinal && isNavBarVisible() -> {
                showPage(WalletPage.WALLET)
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT)
        inset.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun handleDeeplink(
        importData: String,
        startIntent: Intent?,
    ) {
        val request = DeepLinkService.parseIntent(importData, startIntent)
        when (request.type) {
            DeepLinkType.WALLETCONNECT -> {
                if (request.data.contains("relay-protocol")) {
                    val intent = Intent(this, WalletConnectV2Activity::class.java)
                    intent.putExtra("url", request.data)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                } else {
                    walletConnectRequestPending()
                }
            }

            DeepLinkType.SMARTPASS -> {
                viewModel.handleSmartPass(this, request.data)
            }

            DeepLinkType.URL_REDIRECT -> {
                viewModel.track(Analytics.Action.DEEP_LINK)
                if (fragmentsInitialised()) {
                    showPage(WalletPage.DAPP_BROWSER)
                    getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(0, request.data)
                } else {
                    openLink = request.data
                }
            }

            DeepLinkType.TOKEN_NOTIFICATION -> {
                if (fragmentsInitialised()) {
                    showPage(WalletPage.WALLET)
                    getFragment(WalletPage.WALLET).setImportFilename(request.data)
                } else {
                    openToken = request.data
                }
            }

            DeepLinkType.WALLET_API_DEEPLINK -> {
                val intent = Intent(this, ApiV1Activity::class.java)
                intent.putExtra(C.Key.API_V1_REQUEST_URL, request.data)
                viewModel.track(Analytics.Action.DEEP_LINK_API_V1)
                startActivity(intent)
            }

            DeepLinkType.LEGACY_MAGICLINK -> {
                ImportTokenRouter().open(this, request.data)
                finish()
            }

            DeepLinkType.IMPORT_SCRIPT -> {
                if (startIntent != null) {
                    viewModel.importScriptFile(this, startIntent)
                }
            }

            DeepLinkType.INVALID_LINK -> {
                // No action needed
            }
        }
    }

    override fun signingComplete(
        signature: SignatureFromKey,
        message: Signable,
    ) {
        val signHex = Numeric.toHexString(signature.signature)
        Timber.d("Initial Msg: %s", message.message)
        awWalletConnectClient.signComplete(signature, message)
    }

    override fun signingFailed(
        error: Throwable,
        message: Signable,
    ) {
        error.message?.let { awWalletConnectClient.signFail(it, message) }
    }

    override fun getWalletType(): WalletType = viewModel.defaultWallet().value?.type ?: WalletType.KEYSTORE

    override fun getAuthorisation(callback: SignAuthenticationCallback) {
        viewModelWC.getAuthenticationForSignature(viewModel.defaultWallet().value, this, callback)
    }

    override fun sendTransaction(tx: Web3Transaction) {
        // Implementation needed
    }

    override fun completeSendTransaction(
        tx: Web3Transaction,
        signature: SignatureFromKey,
    ) {
        // Implementation needed
    }

    override fun dismissed(
        txHash: String,
        callbackId: Long,
        actionCompleted: Boolean,
    ) {
        if (!actionCompleted) {
            awWalletConnectClient.dismissed(callbackId)
        }
    }

    override fun notifyConfirm(mode: String) {
        // Implementation needed
    }

    override fun gasSelectLauncher(): ActivityResultLauncher<Intent> = getGasSettings

    private fun fragmentsInitialised(): Boolean = supportFragmentManager.fragments.size >= WalletPage.SETTINGS.ordinal

    override fun attestationImported(newToken: TokenCardMeta) {
        runOnUiThread {
            val frag = getFragment(WalletPage.WALLET)
            if (frag is WalletFragment) {
                frag.updateAttestationMeta(newToken)
            }

            if (dialog == null || !dialog!!.isShowing) {
                val aDialog = AWalletAlertDialog(this)
                aDialog.setTitle(R.string.attestation_imported)
                aDialog.setIcon(AWalletAlertDialog.SUCCESS)
                aDialog.setButtonText(R.string.button_ok)
                aDialog.setButtonListener { aDialog.dismiss() }
                dialog = aDialog
                dialog?.show()
            }
        }
    }

    override fun importError(error: String) {
        runOnUiThread {
            hideDialog()
            val aDialog = AWalletAlertDialog(this)
            aDialog.setTitle(R.string.attestation_import_failed)
            aDialog.setMessage(error)
            aDialog.setIcon(AWalletAlertDialog.ERROR)
            aDialog.setButtonText(R.string.button_ok)
            aDialog.setButtonListener { aDialog.dismiss() }
            dialog = aDialog
            dialog?.show()
        }
    }

    override fun smartPassValidation(validation: SmartPassReturn) {
        when (validation) {
            SmartPassReturn.ALREADY_IMPORTED -> {
                // No need to report anything
            }

            SmartPassReturn.IMPORT_SUCCESS -> {
                importedSmartPass()
            }

            SmartPassReturn.IMPORT_FAILED -> {
                // No need to report anything
            }

            SmartPassReturn.NO_CONNECTION -> {
                showNoConnection()
            }
        }
    }

    private fun showNoConnection() {
        runOnUiThread {
            val aDialog = AWalletAlertDialog(this)
            aDialog.setTitle(R.string.no_connection)
            aDialog.setMessage(R.string.no_connection_to_smart_layer)
            aDialog.setIcon(AWalletAlertDialog.WARNING)
            aDialog.setButtonText(R.string.button_ok)
            aDialog.setButtonListener { aDialog.dismiss() }
            dialog = aDialog
            dialog?.show()
        }
    }

    private fun importedSmartPass() {
        runOnUiThread {
            val aDialog = AWalletAlertDialog(this)
            aDialog.setTitle(R.string.imported_smart_pass)
            aDialog.setMessage(R.string.smartpass_imported)
            aDialog.setIcon(AWalletAlertDialog.SUCCESS)
            aDialog.setButtonText(R.string.button_ok)
            aDialog.setButtonListener { aDialog.dismiss() }
            dialog = aDialog
            dialog?.show()
        }
    }

    private fun walletConnectRequestPending() {
        hideDialog()
        runOnUiThread {
            wcProgressDialog = AWalletAlertDialog(this)
            wcProgressDialog?.setProgressMode()
            wcProgressDialog?.setTitle(R.string.title_wallet_connect)
            wcProgressDialog?.setCancelable(false)
            wcProgressDialog?.show()
            handler.postDelayed({ hideDialog() }, 10000)
        }
    }

    fun clearWalletConnectRequest() {
        handler.removeCallbacksAndMessages(null)
        runOnUiThread { hideDialog() }
    }

    private class ScreenSlidePagerAdapter(
        fragmentActivity: FragmentActivity,
    ) : FragmentStateAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment =
            when (WalletPage.entries[position]) {
                WalletPage.WALLET -> WalletFragment()
                WalletPage.ACTIVITY -> ActivityFragment()
                WalletPage.DAPP_BROWSER -> {
                    if (CustomViewSettings.hideDappBrowser()) {
                        BaseFragment()
                    } else {
                        DappBrowserFragment()
                    }
                }

                WalletPage.SETTINGS -> NewSettingsFragment()
            }

        override fun getItemCount(): Int = WalletPage.values().size
    }
}
