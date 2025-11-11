package com.alphawallet.app.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.viewmodel.AdvancedSettingsViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.SettingsItemView
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the advanced settings menu for managing developer and experimental options.
 */
@AndroidEntryPoint
class AdvancedSettingsActivity : BaseActivity() {

    private lateinit var viewModel: AdvancedSettingsViewModel
    private lateinit var nodeStatus: SettingsItemView
    private lateinit var console: SettingsItemView
    private lateinit var clearBrowserCache: SettingsItemView
    private lateinit var tokenScriptManagement: SettingsItemView
    private lateinit var fullScreenSettings: SettingsItemView
    private lateinit var refreshTokenDatabase: SettingsItemView
    private lateinit var eip1559Transactions: SettingsItemView
    private lateinit var analytics: SettingsItemView
    private lateinit var crashReporting: SettingsItemView
    private lateinit var developerOverride: SettingsItemView
    private lateinit var tokenScriptViewer: SettingsItemView

    private var waitDialog: AWalletAlertDialog? = null
    private var clearTokenJob: Job? = null

    /**
     * Sets up the view model, toolbar, and the list of settings.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AdvancedSettingsViewModel::class.java]

        setContentView(R.layout.activity_generic_settings)
        toolbar()
        setTitle(R.string.title_advanced)

        initialiseSettings()
        addSettingsToLayout()
    }

    /**
     * Cleans up subscriptions when the activity is destroyed.
     */
    override fun onDestroy() {
        clearTokenJob?.cancel()
        super.onDestroy()
    }

    /**
     * Builds the various [SettingsItemView] entries and applies initial toggle states.
     */
    private fun initialiseSettings() {
        nodeStatus = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_node_status)
            .withTitle(R.string.action_node_status)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onNodeStatusClicked()
                }
            })
            .build()

        console = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_console)
            .withTitle(R.string.title_console)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onConsoleClicked()
                }
            })
            .build()

        clearBrowserCache = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_cache)
            .withTitle(R.string.title_clear_browser_cache)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onClearBrowserCacheClicked()
                }
            })
            .build()

        tokenScriptManagement = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_tokenscript_manage)
            .withTitle(R.string.tokenscript_management)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onTokenScriptManagementClicked()
                }
            })
            .build()

        tokenScriptViewer = SettingsItemView.Builder(this)
            .withType(SettingsItemView.Type.TOGGLE)
            .withIcon(R.drawable.ic_tokenscript)
            .withTitle(R.string.use_tokenscript_viewer)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onUseTokenScriptViewer()
                }
            })
            .build()

        fullScreenSettings = SettingsItemView.Builder(this)
            .withType(SettingsItemView.Type.TOGGLE)
            .withIcon(R.drawable.ic_phoneicon)
            .withTitle(R.string.fullscreen)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onFullScreenClicked()
                }
            })
            .build()

        refreshTokenDatabase = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_reset_tokens)
            .withTitle(R.string.title_reload_token_data)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onReloadTokenDataClicked()
                }
            })

            .build()

        eip1559Transactions = SettingsItemView.Builder(this)
            .withType(SettingsItemView.Type.TOGGLE)
            .withIcon(R.drawable.ic_icons_settings_1559)
            .withTitle(R.string.experimental_1559)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    on1559TransactionsClicked()
                }
            })
            .build()

        analytics = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_analytics)
            .withTitle(R.string.settings_title_analytics)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onAnalyticsClicked()
                }
            })
            .build()

        crashReporting = SettingsItemView.Builder(this)
            .withIcon(R.drawable.ic_settings_crash_reporting)
            .withTitle(R.string.settings_title_crash_reporting)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onCrashReportingClicked()
                }
            })
            .build()

        developerOverride = SettingsItemView.Builder(this)
            .withType(SettingsItemView.Type.TOGGLE)
            .withIcon(R.drawable.ic_settings_warning)
            .withTitle(R.string.developer_override)
            .withListener(object : SettingsItemView.OnSettingsItemClickedListener {
                // 覆盖接口中定义的方法
                override fun onSettingsItemClicked() {
                    // 在方法内部调用您的函数
                    onDeveloperOverride()
                }
            })
            .build()

        fullScreenSettings.toggleState = viewModel.fullScreenState
        eip1559Transactions.toggleState = viewModel.transactions1559State
        developerOverride.toggleState = viewModel.developerOverrideState
        tokenScriptViewer.toggleState = viewModel.tokenScriptViewerState
    }

    /**
     * Adds all configured settings into the linear layout container.
     */
    private fun addSettingsToLayout() {
        findViewById<LinearLayout>(R.id.layout).apply {
            addView(nodeStatus)
            addView(console)
            addView(clearBrowserCache)
            addView(tokenScriptManagement)
            addView(fullScreenSettings)
            addView(refreshTokenDatabase)
            addView(eip1559Transactions)
            addView(tokenScriptViewer)
            addView(analytics)
            addView(crashReporting)
            addView(developerOverride)
        }
    }

    /**
     * Toggles developer override; prompts the user with a warning when enabling.
     */
    private fun onDeveloperOverride() {
        val enabled = developerOverride.toggleState
        if (enabled) {
            showWarningPopup(R.string.developer_override_warning) { choice ->
                viewModel.toggleDeveloperOverride(choice)
                developerOverride.toggleState = choice
            }
        } else {
            viewModel.toggleDeveloperOverride(false)
        }
    }

    /**
     * Shows a warning dialog and forwards the result to the provided callback.
     */
    private fun showWarningPopup(message: Int, callback: (Boolean) -> Unit) {
        val dialog = AWalletAlertDialog(this)
        dialog.setIcon(AWalletAlertDialog.WARNING)
        dialog.setTitle(R.string.warning)
        dialog.setMessage(message)
        dialog.setButtonText(R.string.i_accept)
        dialog.setButtonListener {
            callback(true)
            dialog.dismiss()
        }
        dialog.setSecondaryButtonText(R.string.action_cancel)
        dialog.setSecondaryButtonListener {
            callback(false)
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Updates the full screen preference when the toggle changes.
     */
    private fun onFullScreenClicked() {
        viewModel.fullScreenState = (fullScreenSettings.toggleState)
    }

    /**
     * Updates the EIP-1559 transaction preference.
     */
    private fun on1559TransactionsClicked() {
        viewModel.toggle1559Transactions(eip1559Transactions.toggleState)
    }

    /**
     * Enables or disables the TokenScript viewer experiment.
     */
    private fun onUseTokenScriptViewer() {
        viewModel.toggleUseViewer(tokenScriptViewer.toggleState)
    }

    /**
     * Opens the node status screen.
     */
    private fun onNodeStatusClicked() {
        startActivity(Intent(this, NodeStatusActivity::class.java))
    }

    /**
     * Placeholder for the console action.
     */
    private fun onConsoleClicked() {
        // TODO implement developer console
    }

    /**
     * Clears browser cache, cookies, and filter settings on a background thread.
     */
    private fun onClearBrowserCacheClicked() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                WebView(this@AdvancedSettingsActivity).apply {
                    clearCache(true)
                    clearFormData()
                    clearHistory()
                    clearSslPreferences()
                }
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                viewModel.blankFilterSettings()
            }
            withContext(Dispatchers.IO) {
                Glide.get(this@AdvancedSettingsActivity).clearDiskCache()
            }
            Toast.makeText(this@AdvancedSettingsActivity, R.string.toast_browser_cache_cleared, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Prompts to reload token data and resets caches when confirmed.
     */
    private fun onReloadTokenDataClicked() {
        if (clearTokenJob?.isActive == true) {
            Toast.makeText(this, R.string.token_data_being_cleared, Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AWalletAlertDialog(this)
        dialog.setIcon(AWalletAlertDialog.NONE)
        dialog.setTitle(R.string.title_reload_token_data)
        dialog.setMessage(R.string.reload_token_data_desc)
        dialog.setButtonText(R.string.action_reload)
        dialog.setButtonListener {
            viewModel.stopChainActivity()
            showWaitDialog()
            clearTokenJob = lifecycleScope.launch {
                val result = viewModel.resetTokenData()
                showResetResult(result)
            }
            viewModel.blankFilterSettings()
        }
        dialog.setSecondaryButtonText(R.string.action_cancel)
        dialog.setSecondaryButtonListener { dialog.dismiss() }
        dialog.show()
    }

    /**
     * Displays a wait dialog while tokens are being reset.
     */
    private fun showWaitDialog() {
        if (waitDialog?.isShowing == true) return
        waitDialog = AWalletAlertDialog(this).apply {
            setTitle(getString(R.string.token_data_being_cleared))
            setIcon(AWalletAlertDialog.NONE)
            setProgressMode()
            setCancelable(true)
            setOnCancelListener {
                clearTokenJob?.cancel()
            }
            show()
        }
    }

    /**
     * Displays the result of resetting the token store.
     */
    private fun showResetResult(resetResult: Boolean) {
        clearTokenJob = null
        waitDialog?.dismiss()
        if (resetResult) {
            Toast.makeText(this, R.string.toast_token_data_cleared, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().apply { putExtra(C.RESET_WALLET, true) })
            finish()
        } else {
            Toast.makeText(this, R.string.error_deleting_account, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens token script management.
     */
    private fun onTokenScriptManagementClicked() {
        startActivity(Intent(this, TokenScriptManagementActivity::class.java))
    }

    /**
     * Opens crash reporting settings.
     */
    private fun onCrashReportingClicked() {
        startActivity(Intent(this, CrashReportSettingsActivity::class.java))
    }

    /**
     * Opens analytics settings.
     */
    private fun onAnalyticsClicked() {
        startActivity(Intent(this, AnalyticsSettingsActivity::class.java))
    }

    /**
     * Handles permission results for creating directories or other advanced actions.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            HomeActivity.RC_ASSET_EXTERNAL_WRITE_PERM ->
                if (viewModel.createDirectory()) {
                    findViewById<LinearLayout>(R.id.layout).removeView(tokenScriptManagement)
                    showAlphaWalletDirectoryConfirmation()
                }
        }
    }

    /**
     * Displays confirmation after creating the AlphaWallet directory.
     */
    private fun showAlphaWalletDirectoryConfirmation() {
        AWalletAlertDialog(this).apply {
            setIcon(AWalletAlertDialog.SUCCESS)
            setTitle(R.string.created_aw_directory)
            setMessage(R.string.created_aw_directory_detail)
            setButtonText(R.string.dialog_ok)
            setButtonListener { dismiss() }
            show()
        }
    }
}
