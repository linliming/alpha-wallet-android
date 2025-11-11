package com.alphawallet.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.BackupOperationType
import com.alphawallet.app.entity.BackupState
import com.alphawallet.app.entity.CreateWalletCallbackInterface
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.ui.QRScanning.DisplayUtils
import com.alphawallet.app.viewmodel.BackupKeyViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.LayoutCallbackListener
import com.alphawallet.app.widget.PasswordInputView
import com.alphawallet.app.widget.SignTransactionDialog
import com.alphawallet.hardware.SignatureFromKey
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupKeyActivity : BaseActivity(), View.OnClickListener, CreateWalletCallbackInterface,
    TextWatcher, SignAuthenticationCallback, LayoutCallbackListener, StandardFunctionInterface {
    private lateinit var viewModel: BackupKeyViewModel
    private var state: BackupState? = null
    private var wallet: Wallet? = null
    private lateinit var titleTextView: TextView
    private lateinit var detail: TextView
    private lateinit var layoutWordHolder: FlexboxLayout
    private var inputView: PasswordInputView? = null
    private lateinit var backupImage: ImageView
    private lateinit var verifyTextBox: TextView
    private lateinit var verifyTextContainer: MaterialCardView
    private var mnemonicArray: Array<String>? = null
    private var successOverlay: LinearLayout? = null
    private var alertDialog: AWalletAlertDialog? = null
    private var keystorePassword = ""
    private lateinit var functionButtonBar: com.alphawallet.app.widget.FunctionButtonBar
    private var hasNoLock = false
    private var screenWidth = 0

    // 创建一个 ActivityResultLauncher 来处理备份钱包的结果
    private val handleBackupWallet =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                backupKeySuccess(BackupOperationType.BACKUP_KEYSTORE_KEY)
            } else {
                askUserSuccess()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow()
        alertDialog = null
        lockOrientation()
        toolbar()
        initViewModel()
        screenWidth = DisplayUtils.getScreenResolution(this).x
        wallet = intent.getParcelableExtra(C.Key.WALLET)
        if (intent.extras?.containsKey("STATE") == true) {
            initBackupState()
        } else {
            initBackupType()
        }
    }

    /**
     * 初始化备份状态
     */
    private fun initBackupState() {
        state = intent.getSerializableExtra("STATE") as BackupState?
        when (state) {
            BackupState.SHOW_SEED_PHRASE_SINGLE -> showSeedPhrase()
            BackupState.ENTER_BACKUP_STATE_HD -> {
                writeDownSeedPhrase()
                displaySeed()
            }
            BackupState.WRITE_DOWN_SEED_PHRASE, BackupState.SHOW_SEED_PHRASE -> verifySeedPhrase()
            BackupState.VERIFY_SEED_PHRASE -> testSeedPhrase()
            BackupState.SEED_PHRASE_INVALID -> {
                resetInputBox()
                verifySeedPhrase()
            }
            BackupState.ENTER_JSON_BACKUP -> jsonBackup()
            BackupState.UPGRADE_KEY_SECURITY ->                 //first open authentication
                setupUpgradeKey(false)
            else -> {}
        }
    }

    /**
     * 显示助记词
     */
    private fun showSeedPhrase() {
        setupTestSeed()
        findViewById<TextView>(R.id.text_title).setText(R.string.your_seed_phrase)
        displaySeed()
        functionButtonBar.setPrimaryButtonText(R.string.hide_seed_text)
        functionButtonBar.setPrimaryButtonClickListener(this)
    }

    /**
     * 初始化备份类型
     */
    private fun initBackupType() {
        var type = intent.getSerializableExtra("TYPE") as BackupOperationType?
        if (type == null) type = BackupOperationType.UNDEFINED
        when (type) {
            BackupOperationType.UNDEFINED -> {
                state = BackupState.UNDEFINED
                displayKeyFailureDialog("Unknown Key operation")
            }
            BackupOperationType.BACKUP_HD_KEY -> {
                state = BackupState.ENTER_BACKUP_STATE_HD
                writeDownSeedPhrase()
                displaySeed()
            }
            BackupOperationType.BACKUP_KEYSTORE_KEY -> {
                state = BackupState.ENTER_JSON_BACKUP
                jsonBackup()
            }
            BackupOperationType.SHOW_SEED_PHRASE -> {
                state = BackupState.SHOW_SEED_PHRASE
                setupTestSeed()
                displaySeed()
            }
            BackupOperationType.EXPORT_PRIVATE_KEY -> {
                displayKeyFailureDialog("Export Private key not yet implemented")
                //TODO: Not yet implemented
            }
            BackupOperationType.UPGRADE_KEY -> setupUpgradeKey(false)
            else -> {}
        }
    }

    /**
     * 锁定屏幕方向
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
     * 设置升级密钥
     * @param showSuccess 是否显示成功
     */
    private fun setupUpgradeKey(showSuccess: Boolean) {
        setContentView(R.layout.activity_backup)
        initViews()
        successOverlay = findViewById(R.id.layout_success_overlay)
        if (successOverlay != null && showSuccess) {
            successOverlay?.visibility = View.VISIBLE
            lifecycleScope.launch {
                delay(1000)
                if (successOverlay == null) return@launch
                if (successOverlay!!.alpha > 0) {
                    successOverlay?.animate()?.alpha(0.0f)?.duration = 500
                    delay(750)
                    successOverlay?.visibility = View.GONE
                    successOverlay?.alpha = 1.0f
                } else {
                    successOverlay?.visibility = View.GONE
                    successOverlay?.alpha = 1.0f
                }
            }
        }
        state = BackupState.UPGRADE_KEY_SECURITY
        if (wallet?.type == WalletType.KEYSTORE) {
            titleTextView.setText(R.string.lock_keystore_upgrade)
        } else {
            titleTextView.setText(R.string.lock_key_upgrade)
        }
        backupImage.setImageResource(R.drawable.biometrics)
        detail.visibility = View.VISIBLE
        detail.setText(R.string.upgrade_key_security_detail)
        val res: Int = if (wallet?.type == WalletType.HDKEY) {
            R.string.lock_seed_phrase
        } else {
            R.string.action_upgrade_key
        }
        functionButtonBar.setPrimaryButtonText(res)
        functionButtonBar.setPrimaryButtonClickListener(this)
    }

    override fun keyUpgraded(upgrade: KeyService.UpgradeKeyResult?) {
        lifecycleScope.launch {
            when (upgrade?.result) {
                KeyService.UpgradeKeyResultType.REQUESTING_SECURITY -> {}
                KeyService.UpgradeKeyResultType.NO_SCREENLOCK -> {
                    hasNoLock = true
                    displayKeyFailureDialog(getString(R.string.enable_screenlock))
                }
                KeyService.UpgradeKeyResultType.ALREADY_LOCKED -> finishBackupSuccess(false)
                KeyService.UpgradeKeyResultType.ERROR -> {
                    hasNoLock = true
                    displayKeyFailureDialog(
                        getString(
                            R.string.unable_to_upgrade_key,
                            upgrade?.message
                        )
                    )
                }
                KeyService.UpgradeKeyResultType.SUCCESSFULLY_UPGRADED -> createdKey(wallet?.address)
                else -> {}
            }
        }
    }

    /**
     * 升级密钥安全性
     */
    private fun upgradeKeySecurity() {
        when (wallet?.type) {
            WalletType.KEYSTORE, WalletType.KEYSTORE_LEGACY, WalletType.HDKEY -> viewModel.upgradeKeySecurity(
                wallet,
                this,
                this
            )
            else -> {}
        }
    }

    override fun createdKey(address: String?) {
        //key upgraded
        //store wallet upgrade
        if (wallet?.address.equals(address, ignoreCase = true)) {
            when (wallet?.type) {
                WalletType.KEYSTORE_LEGACY, WalletType.KEYSTORE, WalletType.HDKEY -> {
                    viewModel.upgradeWallet(address)
                    finishBackupSuccess(true)
                }
                else -> cancelAuthentication()
            }
        }
    }

    /**
     * 设置测试助记词
     */
    private fun setupTestSeed() {
        setContentView(R.layout.activity_backup_write_seed)
        initViews()
    }

    override fun onPause() {
        super.onPause()
        viewModel.resetSignDialog()
        //hide seed phrase and any visible words
        layoutWordHolder.removeAllViews()
        when (state) {
            BackupState.WRITE_DOWN_SEED_PHRASE, BackupState.SHOW_SEED_PHRASE ->                 //note, the OS calls onPause if user chooses to authenticate using PIN or password (takes them to the auth screen).
                writeDownSeedPhrase()
            BackupState.SEED_PHRASE_INVALID, BackupState.VERIFY_SEED_PHRASE -> {
                state =
                    BackupState.ENTER_BACKUP_STATE_HD //reset view back to splash screen
                writeDownSeedPhrase()
                displaySeed()
            }
            BackupState.SET_JSON_PASSWORD -> jsonBackup()
            BackupState.ENTER_JSON_BACKUP, BackupState.ENTER_BACKUP_STATE_HD, BackupState.UPGRADE_KEY_SECURITY, BackupState.FINISH -> {}
            else -> {}
        }
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        titleTextView = findViewById(R.id.text_title)
        detail = findViewById(R.id.text_detail)
        layoutWordHolder = findViewById(R.id.layout_word_holder)
        verifyTextBox = findViewById(R.id.text_verify)
        verifyTextContainer = findViewById(R.id.container)
        backupImage = findViewById(R.id.backup_seed_image)
        functionButtonBar = findViewById(R.id.layoutButtons)
        inputView = findViewById(R.id.input_password)
        inputView?.getEditText()?.addTextChangedListener(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        toolbar()
        setTitle(getString(R.string.empty))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            backPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun handleBackPressed() {
        backPressed()
    }

    /**
     * 处理返回按钮
     */
    private fun backPressed() {
        when (state) {
            BackupState.VERIFY_SEED_PHRASE, BackupState.SEED_PHRASE_INVALID -> {
                //if we're currently verifying seed or we made a mistake copying the seed down then allow user to restart
                state = BackupState.WRITE_DOWN_SEED_PHRASE
                writeDownSeedPhrase()
                displaySeed()
            }
            BackupState.WRITE_DOWN_SEED_PHRASE -> {
                state = BackupState.ENTER_BACKUP_STATE_HD
                keyFailure("")
            }
            BackupState.SET_JSON_PASSWORD -> {
                state = BackupState.ENTER_JSON_BACKUP
                keyFailure("")
            }
            BackupState.FINISH, BackupState.SHOW_SEED_PHRASE_SINGLE -> {
                state = BackupState.FINISH
                finish()
            }
            else -> keyFailure("")
        }
    }

    override fun onClick(view: View) {
        // Passing an empty String as this class handles clicks based on state
        handleClick("", 0)
    }

    /**
     * 重置输入框
     */
    private fun resetInputBox() {
        // Removed - currently not needed
        // verifyTextContainer.setStrokeColor(ContextCompat.getColor(this, R.color.text_secondary));
        // verifyTextBox.setTextColor(getColor(R.color.text_secondary));
        verifyTextBox.setText(R.string.empty)
        val invalid = findViewById<TextView>(R.id.text_invalid)
        invalid.visibility = View.GONE
    }

    /**
     * JSON备份
     */
    private fun jsonBackup() {
        setContentView(R.layout.activity_set_json_password)
        initViews()
        setTitle(getString(R.string.set_keystore_password))
        inputView?.setInstruction(R.string.password_6_characters_or_more)
        state = BackupState.SET_JSON_PASSWORD
        inputView?.getEditText()?.addTextChangedListener(this)
        functionButtonBar.setPrimaryButtonText(R.string.save_keystore)
        functionButtonBar.setPrimaryButtonClickListener(this)
        functionButtonBar.setPrimaryButtonEnabled(false)
        inputView?.setLayoutListener(this, this)
    }

    /**
     * 测试助记词
     */
    private fun testSeedPhrase() {
        val currentText = verifyTextBox.text.toString()
        val currentTest = currentText.split(" ").toTypedArray()
        if (currentTest.size != mnemonicArray!!.size) {
            //fail. This should never happen
            seedIncorrect()
            return
        } else {
            for (i in mnemonicArray!!.indices) {
                if (mnemonicArray!![i] != currentTest[i]) {
                    seedIncorrect()
                    return
                }
            }
        }
        layoutWordHolder.visibility = View.GONE
        verifyTextBox.visibility = View.GONE

        //terminate and display tick
        backupKeySuccess(BackupOperationType.BACKUP_HD_KEY)
    }

    /**
     * 助记词不正确
     */
    private fun seedIncorrect() {
        // Removed for now:
        // The color switch is not shown anyway because ResetInputBox() is called immediately after
        // verifyTextContainer.setStrokeColor(ContextCompat.getColor(this, R.color.negative));
        // verifyTextBox.setTextColor(getColor(R.color.text_secondary));
        // TextView invalid = findViewById(R.id.text_invalid);
        // invalid.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.invalid_phrase, Toast.LENGTH_LONG).show()
        resetInputBox()
        verifySeedPhrase()
    }

    /**
     * 备份密钥成功
     * @param type 备份操作类型
     */
    private fun backupKeySuccess(type: BackupOperationType?) {
        //first record backup time success, in case user aborts operation during key locking
        viewModel.backupSuccess(wallet)

        //now ask if user wants to upgrade the key security (if required)
        when (wallet?.authLevel) {
            KeyService.AuthenticationLevel.STRONGBOX_NO_AUTHENTICATION, KeyService.AuthenticationLevel.TEE_NO_AUTHENTICATION ->                 //improve key security
                setupUpgradeKey(true)
            else -> finishBackupSuccess(true)
        }
    }

    /**
     * 完成备份成功
     * @param upgradeKey 是否升级密钥
     */
    private fun finishBackupSuccess(upgradeKey: Boolean) {
        state = BackupState.SEED_PHRASE_VALIDATED
        val intent = Intent()
        when (wallet?.type) {
            WalletType.KEYSTORE_LEGACY, WalletType.KEYSTORE -> intent.putExtra(
                "TYPE",
                BackupOperationType.BACKUP_KEYSTORE_KEY
            )
            WalletType.HDKEY -> intent.putExtra("TYPE", BackupOperationType.BACKUP_HD_KEY)
            else -> cancelAuthentication()
        }
        intent.putExtra("Key", wallet?.address)
        intent.putExtra("Upgrade", upgradeKey)
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
     * 验证助记词
     */
    private fun verifySeedPhrase() {
        setContentView(R.layout.activity_verify_seed_phrase)
        initViews()
        functionButtonBar.setPrimaryButtonText(R.string.action_continue)
        functionButtonBar.setPrimaryButtonClickListener { testSeedPhrase() }
        functionButtonBar.setPrimaryButtonEnabled(false)
        state = BackupState.VERIFY_SEED_PHRASE
        titleTextView.setText(R.string.verify_seed_phrase)
        val invalid = findViewById<TextView>(R.id.text_invalid)
        invalid.visibility = View.INVISIBLE
        layoutWordHolder.visibility = View.VISIBLE
        layoutWordHolder.removeAllViews()
        if (mnemonicArray != null) {
            jumbleList()
        }
    }

    /**
     * 打乱列表
     */
    private fun jumbleList() {
        val numberList: MutableList<Int> = ArrayList()
        for (i in mnemonicArray!!.indices) numberList.add(i)
        for (i in mnemonicArray!!.indices) {
            val random = (Math.random() * numberList.size.toDouble()).toInt()
            val mnemonicIndex = numberList[random]
            numberList.removeAt(random) //remove this index
            val tv = generateSeedWordTextView(mnemonicArray!![mnemonicIndex])
            tv.setOnClickListener { view: View? -> onWordClick(tv) }
            layoutWordHolder.addView(tv)
        }
    }

    /**
     * 当单词被点击时
     * @param tv 文本视图
     */
    private fun onWordClick(tv: TextView) {
        tv.isSelected = true
        tv.setOnClickListener(null)
        var currentText = verifyTextBox.text.toString()
        if (currentText.isNotEmpty()) currentText += " "
        currentText += tv.text.toString()
        verifyTextBox.text = currentText
        val currentTest = currentText.split(" ").toTypedArray()
        if (currentTest.size == mnemonicArray!!.size) {
            functionButtonBar.setPrimaryButtonEnabled(true)
        }
    }

    /**
     * 写下助记词
     */
    private fun writeDownSeedPhrase() {
        setContentView(R.layout.activity_backup_write_seed)
        initViews()
        state = BackupState.WRITE_DOWN_SEED_PHRASE
        titleTextView.setText(R.string.write_down_seed_phrase)
        functionButtonBar.setPrimaryButtonText(R.string.wrote_down_seed_phrase)
        functionButtonBar.setPrimaryButtonClickListener(this)
    }

    /**
     * 显示助记词
     */
    private fun displaySeed() {
        layoutWordHolder.visibility = View.VISIBLE
        layoutWordHolder.removeAllViews()
        viewModel.getAuthentication(wallet, this, this)
    }

    /**
     * 生成助记词文本视图
     * @param word 单词
     * @return 文本视图
     */
    private fun generateSeedWordTextView(word: String): TextView {
        val margin = resources.getDimension(R.dimen.mini_4).toInt()
        val params = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(margin, margin, margin, margin)
        val seedWord = TextView(this, null, R.attr.seedWordStyle)
        seedWord.text = word
        seedWord.layoutParams = params
        return seedWord
    }

    override fun HDKeyCreated(
        address: String?,
        ctx: Context?,
        level: KeyService.AuthenticationLevel?
    ) {
        //empty, doesn't get called
    }

    override fun keyFailure(message: String?) {
        if (message?.isNotEmpty() == true) {
            displayKeyFailureDialog(message)
        } else {
            val intent = Intent()
            setResult(RESULT_CANCELED, intent)
            intent.putExtra("Key", wallet?.address)
            finish()
        }
    }

    /**
     * 显示密钥失败对话框
     * @param message 消息
     */
    private fun displayKeyFailureDialog(message: String?) {
        hideDialog()
        alertDialog = AWalletAlertDialog(this)
        alertDialog?.setIcon(AWalletAlertDialog.ERROR)
        alertDialog?.setTitle(R.string.key_error)
        alertDialog?.setMessage(message)
        alertDialog?.setButtonText(R.string.action_continue)
        alertDialog?.setCanceledOnTouchOutside(true)
        alertDialog?.setButtonListener {
            cancelAuthentication()
            alertDialog?.dismiss()
        }
        alertDialog?.setOnCancelListener { cancelAuthentication() }
        alertDialog?.show()
    }

    override fun fetchMnemonic(mnemonic: String?) {
        lifecycleScope.launch {
            when (state) {
                BackupState.WRITE_DOWN_SEED_PHRASE -> {
                    writeDownSeedPhrase()
                    mnemonicArray = mnemonic?.split(" ")?.toTypedArray()
                    addSeedWordsToScreen()
                }
                BackupState.ENTER_JSON_BACKUP, BackupState.SET_JSON_PASSWORD -> viewModel.exportWallet(
                    wallet,
                    mnemonic,
                    keystorePassword
                )
                BackupState.SHOW_SEED_PHRASE -> {
                    setupTestSeed() //drop through
                    mnemonicArray = mnemonic?.split(" ")?.toTypedArray()
                    addSeedWordsToScreen()
                }
                BackupState.SHOW_SEED_PHRASE_SINGLE -> {
                    mnemonicArray = mnemonic?.split(" ")?.toTypedArray()
                    addSeedWordsToScreen()
                }
                BackupState.VERIFY_SEED_PHRASE -> {
                    verifySeedPhrase()
                    mnemonicArray = mnemonic?.split(" ")?.toTypedArray()
                    addSeedWordsToScreen()
                }
                BackupState.SEED_PHRASE_INVALID, BackupState.UNDEFINED, BackupState.ENTER_BACKUP_STATE_HD, BackupState.UPGRADE_KEY_SECURITY -> displayKeyFailureDialog(
                    "Error in key restore: " + state?.ordinal
                )
                else -> {}
            }
        }
    }

    /**
     * 将助记词添加到屏幕
     */
    private fun addSeedWordsToScreen() {
        if (mnemonicArray == null) return
        layoutWordHolder.flexDirection = FlexDirection.ROW
        for (word in mnemonicArray!!) {
            layoutWordHolder.addView(generateSeedWordTextView(word))
        }
    }

    override fun gotAuthorisation(gotAuth: Boolean) {
        if (gotAuth) {
            //use this to get seed backup
            when (state) {
                BackupState.UNDEFINED -> {}
                BackupState.ENTER_BACKUP_STATE_HD -> {}
                BackupState.WRITE_DOWN_SEED_PHRASE ->                     //proceed and get the mnemonic
                    viewModel.getSeedPhrase(wallet, this, this)
                BackupState.VERIFY_SEED_PHRASE -> {}
                BackupState.SEED_PHRASE_INVALID -> {}
                BackupState.ENTER_JSON_BACKUP, BackupState.SET_JSON_PASSWORD -> viewModel.getPasswordForKeystore(
                    wallet,
                    this,
                    this
                )
                BackupState.SHOW_SEED_PHRASE_SINGLE, BackupState.SHOW_SEED_PHRASE -> viewModel.getSeedPhrase(
                    wallet,
                    this,
                    this
                )
                BackupState.UPGRADE_KEY_SECURITY -> upgradeKeySecurity()
                else -> {}
            }
        } else {
            displayKeyFailureDialog(getString(R.string.authentication_error))
        }
    }

    override fun cancelAuthentication() {
        val intent = Intent()
        setResult(RESULT_CANCELED, intent)
        intent.putExtra("Key", wallet?.address)
        if (hasNoLock) intent.putExtra("nolock", true)
        finish()
    }

    override fun gotSignature(signature: SignatureFromKey?) {
        //No code here since we don't need to backup hardware key
    }

    /**
     * 初始化视图模型
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[BackupKeyViewModel::class.java]
        viewModel.exportedStore().observe(this) { keystore: String -> onExportKeystore(keystore) }
    }

    /**
     * 导出密钥库
     * @param keystore 密钥库
     */
    private fun onExportKeystore(keystore: String) {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Keystore")
        sharingIntent.putExtra(Intent.EXTRA_TEXT, keystore)
        handleBackupWallet.launch(Intent.createChooser(sharingIntent, "Share via"))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        var taskCode: Operation? = null
        var reqCode = requestCode

        //Interpret the return code; if it's within the range of values possible to return from PIN confirmation then separate out
        //the task code from the return value. We have to do it this way because there's no way to send a bundle across the PIN dialog
        //and out through the PIN dialog's return back to here
        if (reqCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && reqCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10) {
            taskCode =
                Operation.values()[reqCode - SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS]
            reqCode = SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS
        }
        when (reqCode) {
            SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> if (resultCode == RESULT_OK) {
                viewModel.completeAuthentication(taskCode)
            } else {
                viewModel.failedAuthentication(taskCode)
            }
        }
    }

    /**
     * 询问用户是否成功
     */
    private fun askUserSuccess() {
        hideDialog()
        alertDialog = AWalletAlertDialog(this)
        alertDialog?.setIcon(AWalletAlertDialog.SUCCESS)
        alertDialog?.setTitle(R.string.do_manage_make_backup)
        alertDialog?.setButtonText(R.string.yes_continue)
        alertDialog?.setButtonListener {
            hideDialog()
            backupKeySuccess(BackupOperationType.BACKUP_KEYSTORE_KEY)
        }
        alertDialog?.setSecondaryButtonText(R.string.no_repeat)
        alertDialog?.setSecondaryButtonListener {
            hideDialog()
            cancelAuthentication()
        }
        alertDialog?.show()
    }

    /**
     * 隐藏对话框
     */
    private fun hideDialog() {
        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog?.dismiss()
            alertDialog = null
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun afterTextChanged(editable: Editable) {
        when (state) {
            BackupState.ENTER_BACKUP_STATE_HD -> {}
            BackupState.WRITE_DOWN_SEED_PHRASE -> {}
            BackupState.VERIFY_SEED_PHRASE -> {}
            BackupState.SEED_PHRASE_INVALID -> {}
            BackupState.ENTER_JSON_BACKUP -> {}
            BackupState.SET_JSON_PASSWORD -> {
                val txt = inputView?.getText().toString()
                if (txt.length >= 6) {
                    inputView?.setError("")
                    functionButtonBar.setPrimaryButtonEnabled(true)
                }
            }
            BackupState.SHOW_SEED_PHRASE -> {}
            else -> {}
        }
    }

    override fun onLayoutShrunk() {}
    override fun onLayoutExpand() {}
    override fun onInputDoneClick(view: View?) {
        inputView = findViewById(R.id.input_password)
        keystorePassword = inputView?.getText().toString()
        if (keystorePassword.length > 5) {
            //get authentication
            viewModel.getAuthentication(wallet, this, this)
        } else {
            inputView?.setError(R.string.password_6_characters_or_more)
        }
    }

    override fun handleClick(action: String?, id: Int) {
        when (state) {
            BackupState.ENTER_BACKUP_STATE_HD -> {
                writeDownSeedPhrase()
                displaySeed()
            }
            BackupState.WRITE_DOWN_SEED_PHRASE, BackupState.SHOW_SEED_PHRASE -> verifySeedPhrase()
            BackupState.VERIFY_SEED_PHRASE -> testSeedPhrase()
            BackupState.SEED_PHRASE_INVALID -> {
                resetInputBox()
                verifySeedPhrase()
            }
            BackupState.SHOW_SEED_PHRASE_SINGLE -> {
                state = BackupState.FINISH
                finish()
            }
            BackupState.ENTER_JSON_BACKUP -> jsonBackup()
            BackupState.SET_JSON_PASSWORD -> {
                inputView = findViewById(R.id.input_password)
                onInputDoneClick(inputView!!)
            }
            BackupState.UPGRADE_KEY_SECURITY ->                 //first open authentication
                viewModel.getAuthentication(wallet, this, this)
            else -> {}
        }
    }

    /**
     * 安全窗口
     */
    private fun secureWindow() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
