package com.alphawallet.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.alphawallet.app.C.Key.Companion.WALLET
import com.alphawallet.app.R
import com.alphawallet.app.entity.BackupOperationType
import com.alphawallet.app.entity.BackupState
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.FunctionButtonBar
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint

private const val EXTRA_TYPE = "TYPE"

@AndroidEntryPoint
class BackupFlowActivity : BaseActivity(), View.OnClickListener, StandardFunctionInterface {

    private var state: BackupState? = null
    private lateinit var wallet: Wallet
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private var layoutWordHolder: FlexboxLayout? = null
    private lateinit var backupImage: ImageView
    private var alertDialog: AWalletAlertDialog? = null
    private lateinit var functionButtonBar: FunctionButtonBar
    private var type: BackupOperationType = BackupOperationType.UNDEFINED
    private var launchedBackup = false

    /** 用于接收备份结果并根据结果码处理页面退出逻辑。 */
    private val handleBackupWallet: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                finishBackupSuccess(result.data)
            }
            finish()
        }



    /**
     * Activity 生命周期入口：初始化参数、锁定方向并根据操作类型展示对应视图。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alertDialog = null
        lockOrientation()
        launchedBackup = false

        type = resolveBackupType()
        wallet = extractWallet() ?: run {
            finish()
            return
        }

        toolbar()

        when (type) {
            BackupOperationType.UNDEFINED -> {
                state = BackupState.UNDEFINED
                displayKeyFailureDialog("Unknown Key operation")
            }

            BackupOperationType.BACKUP_HD_KEY -> {
                state = BackupState.ENTER_BACKUP_STATE_HD
                setHDBackupSplash()
            }

            BackupOperationType.BACKUP_KEYSTORE_KEY -> {
                state = BackupState.ENTER_JSON_BACKUP
                setupJSONExport()
            }

            BackupOperationType.UPGRADE_KEY -> handleClick(null, 0)
            else -> {}
        }
    }

    /**
     * 根据当前屏幕方向锁定界面，防止备份过程被意外旋转打断。
     */
    @SuppressLint("SourceLockedOrientationActivity")
    private fun lockOrientation() {
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
    }

    /**
     * 配置助记词备份的引导界面并设置对应按钮行为。
     */
    private fun setHDBackupSplash() {
        setContentView(R.layout.activity_backup)
        initViews()
        title.setText(R.string.backup_seed_phrase)
        backupImage.setImageResource(R.drawable.seed)
        detail.setText(R.string.backup_seed_phrase_detail)
        functionButtonBar.setPrimaryButtonText(R.string.action_back_up_my_wallet)
        functionButtonBar.setPrimaryButtonClickListener(this)
    }

    /**
     * 配置 keystore JSON 导出的说明界面。
     */
    private fun setupJSONExport() {
        setContentView(R.layout.activity_backup)
        initViews()
        title.setText(R.string.what_is_keystore_json)
        backupImage.setImageResource(R.drawable.ic_keystore)
        detail.setText(R.string.keystore_detail_text)
        state = BackupState.ENTER_JSON_BACKUP
        functionButtonBar.setPrimaryButtonText(R.string.export_keystore_json)
        functionButtonBar.setPrimaryButtonClickListener(this)
    }

    /**
     * 暂停时清除敏感信息并根据状态恢复到安全界面。
     */
    override fun onPause() {
        super.onPause()
        layoutWordHolder?.removeAllViews()

        when (state) {
            BackupState.WRITE_DOWN_SEED_PHRASE,
            BackupState.SHOW_SEED_PHRASE -> setHDBackupSplash()

            BackupState.SEED_PHRASE_INVALID,
            BackupState.VERIFY_SEED_PHRASE -> {
                state = BackupState.ENTER_BACKUP_STATE_HD
                setHDBackupSplash()
            }

            BackupState.SET_JSON_PASSWORD -> setupJSONExport()

            BackupState.ENTER_JSON_BACKUP,
            BackupState.ENTER_BACKUP_STATE_HD,
            BackupState.UPGRADE_KEY_SECURITY,
            BackupState.UNDEFINED,
            null -> Unit

            else -> {}
        }
    }

    /**
     * 初始化 UI 控件引用并配置工具栏与输入模式。
     */
    private fun initViews() {
        title = findViewById(R.id.text_title)
        detail = findViewById(R.id.text_detail)
        layoutWordHolder = findViewById(R.id.layout_word_holder)
        backupImage = findViewById(R.id.backup_seed_image)
        functionButtonBar = findViewById(R.id.layoutButtons)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        toolbar()
        setTitle(getString(R.string.empty))
    }

    /**
     * 处理工具栏返回按钮，统一走自定义的返回逻辑。
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            backPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 自定义物理返回键逻辑，通知上个页面备份取消。
     */
    private fun backPressed() {
        val intent = Intent().apply {
            putExtra("Key", wallet.address)
        }
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    /**
     * 当宿主要求处理返回事件时调用，转发至本地实现。
     */
    override fun handleBackPressed() {
        backPressed()
    }

    /**
     * 处理主按钮点击，统一调用 handleClick 流程。
     */
    override fun onClick(v: View?) {
        handleClick(null, 0)
    }

    /**
     * 异常情况下弹出提示框告知用户备份失败原因。
     */
    private fun displayKeyFailureDialog(message: String) {
        hideDialog()
        alertDialog = AWalletAlertDialog(this).apply {
            setIcon(AWalletAlertDialog.ERROR)
            setTitle(R.string.key_error)
            setMessage(message)
            setButtonText(R.string.action_continue)
            setCanceledOnTouchOutside(true)
            setButtonListener { dismiss() }
            setOnCancelListener { }
        }
        alertDialog?.show()
    }

    /**
     * 恢复页面时如已跳转备份界面则直接关闭，避免残留空白界面。
     */
    override fun onResume() {
        super.onResume()
        if (launchedBackup) {
            finish()
        }
    }

    /**
     * 备份完成后返回成功结果并关闭页面。
     */
    private fun finishBackupSuccess(data: Intent?) {
        setResult(RESULT_OK, data)
        finish()
    }

    /**
     * 取消身份验证流程时回传取消结果。
     */
    fun cancelAuthentication() {
        val intent = Intent().apply {
            putExtra("Key", wallet.address)
        }
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    /**
     * 隐藏当前弹窗，防止窗口泄漏。
     */
    private fun hideDialog() {
        alertDialog?.takeIf { it.isShowing }?.dismiss()
        alertDialog = null
    }

    /**
     * 统一处理备份按钮点击，根据备份类型启动不同流程。
     */
    override fun handleClick(action: String?, actionId: Int) {
        val intent = Intent(this, BackupKeyActivity::class.java).apply {
            putExtra(WALLET, wallet)
            // 1. 先将 String? 安全地转换为 BackupOperationType
            val operationType: BackupOperationType = try {
                // .orEmpty() 可以处理 type 为 null 的情况，
                // 这样 valueOf 会失败并进入 catch 块
                BackupOperationType.valueOf(type.orEmpty())
            } catch (e: IllegalArgumentException) {
                // 如果字符串为 null、空或无效值，则统一使用默认值
                BackupOperationType.UNDEFINED
            }
            when (operationType) {
                BackupOperationType.BACKUP_HD_KEY -> putExtra("STATE", BackupState.ENTER_BACKUP_STATE_HD)
                BackupOperationType.BACKUP_KEYSTORE_KEY -> putExtra("STATE", BackupState.ENTER_JSON_BACKUP)
                BackupOperationType.UPGRADE_KEY -> putExtra("STATE", BackupState.UPGRADE_KEY_SECURITY)
                BackupOperationType.UNDEFINED -> Unit
                else -> {}
            }
        }
        launchedBackup = true
        handleBackupWallet.launch(intent)
    }

    /**
     * 解析传入的操作类型，兼容不同 Android 版本的序列化方式。
     */
    private fun resolveBackupType(): BackupOperationType {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_TYPE, BackupOperationType::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_TYPE) as? BackupOperationType
        } ?: BackupOperationType.UNDEFINED
    }

    /**
     * 解析 Wallet 参数，兼容 API 33 以上的泛型签名。
     */
    private fun extractWallet(): Wallet? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(WALLET, Wallet::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(WALLET)
        }
    }
}
