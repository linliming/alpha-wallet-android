package com.alphawallet.app.widget

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.ENSCallback
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.analytics.QrScanResultType
import com.alphawallet.app.entity.analytics.QrScanSource
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction
import com.alphawallet.app.ui.QRScanning.QRScannerActivity
import com.alphawallet.app.ui.widget.adapter.AutoCompleteAddressAdapter
import com.alphawallet.app.ui.widget.entity.AddressReadyCallback
import com.alphawallet.app.ui.widget.entity.BoxStatus
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.app.ui.widget.entity.ItemClickListener
import com.alphawallet.app.util.KeyboardUtils
import com.alphawallet.app.util.Utils.formatAddress
import com.alphawallet.app.util.Utils.isAddressValid
import timber.log.Timber
import java.util.regex.Pattern

/**
 * A custom view for inputting and validating Ethereum addresses, with support for ENS, QR code scanning, and paste functionality.
 * This class has been migrated to idiomatic Kotlin, ensuring null safety and improved readability.
 */
class InputAddress @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs), ItemClickListener, ENSCallback, TextWatcher {

    private val editText: AutoCompleteTextView
    private val labelText: TextView
    private val pasteItem: TextView
    private val statusText: TextView
    private val avatar: UserAvatar
    private val scanQrIcon: ImageButton
    private val boxLayout: RelativeLayout
    private val errorText: TextView

    private val ensHandler: ENSHandler?
    private val standardTextSize: Float

    private var labelResId: Int = 0
    private var hintResId: Int = 0
    private var noCam: Boolean = false
    private var imeOptions: String? = null
    private var handleENS: Boolean = false
    private var dialog: AWalletAlertDialog? = null

    private var addressReadyCallback: AddressReadyCallback? = null
    private var chainOverride: Long = 0

    var fullAddress: String? = null
        private set
    var ensName: String? = null
        private set

    /**
     * The current text input by the user, trimmed of whitespace.
     */
    val inputText: String
        get() = editText.text.toString().trim()

    init {
        inflate(context, R.layout.item_input_address, this)

        labelText = findViewById(R.id.label)
        editText = findViewById(R.id.edit_text)
        pasteItem = findViewById(R.id.text_paste)
        statusText = findViewById(R.id.status_text)
        scanQrIcon = findViewById(R.id.img_scan_qr)
        boxLayout = findViewById(R.id.box_layout)
        errorText = findViewById(R.id.error_text)
        avatar = findViewById(R.id.avatar)

        getAttrs(context, attrs)

        editText.addTextChangedListener(this)
        editText.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        ensHandler = if (handleENS) {
            ENSHandler(this, AutoCompleteAddressAdapter(context, C.ENS_HISTORY).apply {
                setListener(this@InputAddress)
            })
        } else {
            null
        }

        editText.setOnFocusChangeListener { _, hasFocus ->
            setBoxColour(if (hasFocus) BoxStatus.SELECTED else BoxStatus.UNSELECTED)
        }

        setViews()
        setImeOptions()
        standardTextSize = editText.textSize
        avatar.resetBinding()
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.InputView, 0, 0)
        try {
            labelResId = a.getResourceId(R.styleable.InputView_label, R.string.empty)
            hintResId = a.getResourceId(R.styleable.InputView_hint, R.string.empty)
            handleENS = a.getBoolean(R.styleable.InputView_ens, false)
            imeOptions = a.getString(R.styleable.InputView_imeOptions)
            noCam = a.getBoolean(R.styleable.InputView_nocam, false)
            val showHeader = a.getBoolean(R.styleable.InputView_show_header, true)
            val headerTextId = a.getResourceId(R.styleable.InputView_label, R.string.recipient)

            findViewById<View>(R.id.layout_header).isVisible = showHeader
            findViewById<TextView>(R.id.text_header).setText(headerTextId)
        } finally {
            a.recycle()
        }
    }

    private fun setViews() {
        if (labelResId != R.string.empty) {
            labelText.setText(labelResId)
            labelText.isVisible = true
        }

        editText.setHint(hintResId)
        pasteItem.setOnClickListener(pasteListener)

        scanQrIcon.isVisible = !noCam
        if (!noCam) {
            scanQrIcon.setOnClickListener {
                val intent = Intent(context, QRScannerActivity::class.java).apply {
                    putExtra(QrScanSource.KEY, QrScanSource.ADDRESS_TEXT_FIELD.value)
                    putExtra(QrScanResultType.KEY, QrScanResultType.ADDRESS.value)
                    putExtra(C.EXTRA_CHAIN_ID, chainOverride)
                }
                (context as? Activity)?.startActivityForResult(intent, C.BARCODE_READER_REQUEST_CODE)
            }
        }
    }

    private fun setImeOptions() {
        when (imeOptions) {
            "actionNext" -> editText.imeOptions = EditorInfo.IME_ACTION_NEXT
            "actionDone" -> {
                editText.imeOptions = EditorInfo.IME_ACTION_DONE
                editText.setRawInputType(InputType.TYPE_CLASS_TEXT)
            }
            else -> editText.imeOptions = EditorInfo.IME_ACTION_DONE // Default action
        }

        editText.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            false
        }
    }

    /**
     * 为内部的 EditText 添加一个 TextWatcher。
     */
    fun addTextChangedListener(watcher: TextWatcher) {
        // 现在这是在类内部访问，是允许的
        editText.addTextChangedListener(watcher)
    }

    val inputView: AutoCompleteTextView
        get() = findViewById(R.id.edit_text)


    val status: String
        get() = statusText.text.toString().trim()

    /**
     * Sets the resolved ENS address and updates the UI.
     * @param address The resolved Ethereum address (e.g., "0x...").
     */
    fun setENSAddress(address: String) {
        this.fullAddress = address
        setStatus(address)
    }

    /**
     * Sets the resolved ENS name and updates the UI.
     * @param ensName The resolved ENS name (e.g., "vitalik.eth").
     */
    fun setENSName(ensName: String) {
        this.ensName = ensName
        setStatus(ensName)
    }

    /**
     * Displays a status message below the input field, such as a resolved address or ENS name.
     * @param statusTxt The text to display. If empty or null, the status view is hidden.
     */
    fun setStatus(statusTxt: CharSequence?) {
        if (statusTxt.isNullOrEmpty()) {
            statusText.isVisible = false
            statusText.text = ""
            avatar.isVisible = false
            avatar.resetBinding()
            if (errorText.isVisible) { // Cancel error
                setBoxColour(BoxStatus.SELECTED)
            }
        } else {
            val status = if (statusTxt.startsWith("0x")) {
                formatAddress(statusTxt.toString())
            } else {
                statusTxt.toString()
            }
            statusText.text = status
            statusText.isVisible = true
        }
    }

    /**
     * Displays an error message and sets the input box to an error state.
     * @param errorTxt The error message to display.
     */
    fun setError(errorTxt: CharSequence?) {
        statusText.isVisible = false
        avatar.isVisible = false
        avatar.resetBinding()
        setBoxColour(BoxStatus.ERROR)
        errorText.text = errorTxt
        errorText.isVisible = true
    }

    /**
     * Shows or hides the progress spinner in the user avatar.
     * @param waiting True to show the spinner, false to hide it.
     */
    fun setWaitingSpinner(waiting: Boolean) {
        if (waiting) {
            avatar.setWaiting()
        } else {
            avatar.finishWaiting()
        }
    }

    private fun setBoxColour(status: BoxStatus) {
        when (status) {
            BoxStatus.ERROR -> {
                boxLayout.setBackgroundResource(R.drawable.background_input_error)
                labelText.setTextColor(context.getColor(R.color.error))
            }
            BoxStatus.UNSELECTED -> {
                boxLayout.setBackgroundResource(R.drawable.background_password_entry)
                labelText.setTextColor(context.getColor(R.color.text_secondary))
                errorText.isVisible = false
            }
            BoxStatus.SELECTED -> {
                boxLayout.setBackgroundResource(R.drawable.background_input_selected)
                labelText.setTextColor(context.getColor(R.color.brand))
                errorText.isVisible = false
            }
        }
    }

    override fun onItemClick(url: String?) {
        ensHandler?.handleHistoryItemClick(url)
    }

    override fun ENSResolved(address: String?, ens: String?) {
        errorText.isVisible = false
        setWaitingSpinner(false)
        bindAvatar(address, ens)
        addressReadyCallback?.resolvedAddress(address, ens)
        addressReadyCallback?.addressValid(true)
    }

    override fun ENSName(name: String?) {
        bindAvatar(TokenscriptFunction.ZERO_ADDRESS, name?:"")
    }

    private fun bindAvatar(address: String?, ensName: String?) {
        avatar.isVisible = true
        val temp = Wallet(address).apply {
            this.ENSname = ensName
        }
        avatar.bindAndFind(temp)
    }

    override fun ENSComplete() {
        displayCheckingDialog(false)
        val callback = addressReadyCallback ?: throw RuntimeException(
            "Need to implement AddressReady in your class which implements InputAddress, and set the AddressReady callback: \"inputAddress.setAddressCallback(this);\""
        )

        callback.addressReady(resolvedAddress, resolvedEnsName)
        callback.addressValid(true)
    }

    /**
     * Returns the currently resolved address, determined by checking the full address, the main text input, and the status text.
     */
    val resolvedAddress: String?
        get() {
            val mainText = editText.text.toString().trim()
            val status = statusText.text.toString().trim()

            return when {
                isAddressValid(fullAddress) -> fullAddress
                isAddressValid(mainText) -> mainText
                isAddressValid(status) -> status
                else -> null
            }
        }

    /**
     * Returns the currently resolved ENS name.
     */
    val resolvedEnsName: String?
        get() {
            val mainText = editText.text.toString().trim()
            val status = statusText.text.toString().trim()

            return when {
                isAddressValid(mainText) && status.contains(".") -> status
                isAddressValid(fullAddress) && mainText.contains(".") -> mainText
                else -> null
            }
        }

    /**
     * Initiates the address resolution process. If ENS handling is enabled, it triggers the ENS handler;
     * otherwise, it completes immediately.
     */
    fun getAddress() {
        if (ensHandler != null) {
            ensHandler.getAddress()
        } else {
            ENSComplete()
        }
    }

    override fun displayCheckingDialog(shouldShow: Boolean) {
        if (shouldShow) {
            dialog = AWalletAlertDialog(context).apply {
                setIcon(AWalletAlertDialog.NONE)
                setTitle(R.string.title_dialog_check_ens)
                setProgressMode()
                setCancelable(false)
                show()
            }
        } else {
            dialog?.dismiss()
        }
    }

    /**
     * Sets the chain ID to use for QR code scanning, which is useful in contexts like WalletConnect.
     * @param chainId The chain ID to override with.
     */
    fun setChainOverrideForWalletConnect(chainId: Long) {
        this.chainOverride = chainId
    }

    fun hideKeyboard() {
        KeyboardUtils.hideKeyboard(this)
    }

    /**
     * Sets the address text in the input field.
     * @param text The address or text to set.
     */
    fun setAddress(text: String?) {
        if (!text.isNullOrEmpty()) {
            editText.setText(text)
        }
    }

    /**
     * Sets the callback for address resolution events.
     * @param callback The callback to be invoked.
     */
    fun setAddressCallback(callback: AddressReadyCallback) {
        this.addressReadyCallback = callback
    }

    /**
     * Dismisses the ENS checking dialog if it's showing.
     */
    fun stopNameCheck() {
        displayCheckingDialog(false)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NO-OP */ }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        setStatus(null)
        val ts = editText.textSize
        val amount = inputText.length
        if (amount > 30 && ts == standardTextSize && !noCam) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, standardTextSize * 0.85f) // Shrink text size to fit
        } else if (amount <= 30 && ts < standardTextSize) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, standardTextSize)
        }
    }

    override fun afterTextChanged(s: Editable?) {
        addressReadyCallback ?: return

        val addressMatch = FIND_ADDRESS_PATTERN.matcher(s.toString())
        val addressValid = addressMatch.find()
        addressReadyCallback?.addressValid(addressValid)

        if (addressValid) {
            this.fullAddress = s.toString().trim()
        }

        setStatus(null)
        if (ensHandler != null && inputText.isNotEmpty()) {
            ensHandler.checkAddress()
        }

        if (s.isNullOrEmpty()) {
            pasteItem.text = context.getString(R.string.paste)
            pasteItem.setOnClickListener(pasteListener)
        } else {
            pasteItem.text = context.getString(R.string.action_clear)
            pasteItem.setOnClickListener(clearListener)
        }
    }

    /**
     * The currently selected chain ID override.
     */
    val chain: Long
        get() = chainOverride

    // Listeners
    private val pasteListener = OnClickListener {
        val clipboard = context.getSystemService<ClipboardManager>()
        try {
            clipboard?.primaryClip?.getItemAt(0)?.text?.let {
                editText.append(it)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private val clearListener = OnClickListener {
        editText.text.clear()
    }

    companion object {
        private val FIND_ADDRESS_PATTERN: Pattern = Pattern.compile("^(\\s?)+(0x)([0-9a-fA-F]{40})(\\s?)+\\z")
    }
}
