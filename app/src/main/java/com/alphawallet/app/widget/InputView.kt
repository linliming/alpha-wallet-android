package com.alphawallet.app.widget

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.analytics.QrScanResultType
import com.alphawallet.app.entity.analytics.QrScanSource
import com.alphawallet.app.ui.QRScanning.QRScannerActivity
import com.alphawallet.app.ui.widget.entity.BoxStatus
import com.alphawallet.app.util.Utils
import timber.log.Timber

class InputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labelText: TextView
    private val errorText: TextView
    private val statusText: TextView
    private val pasteItem: TextView
    private val editText: EditText
    private val boxLayout: RelativeLayout
    private val scanQrIcon: ImageButton
    private val header: StandardHeader

    private var labelResId: Int = R.string.empty
    private var lines: Int = 1
    private var inputType: String? = null
    private var imeOptions: String? = null

    init {
        inflate(context, R.layout.item_input_view, this)

        labelText = findViewById(R.id.label)
        errorText = findViewById(R.id.error_text)
        editText = findViewById(R.id.edit_text)
        statusText = findViewById(R.id.status_text)
        boxLayout = findViewById(R.id.box_layout)
        scanQrIcon = findViewById(R.id.img_scan_qr)
        pasteItem = findViewById(R.id.text_paste)
        header = findViewById(R.id.layout_header)

        attrs?.let { applyAttributes(context, it) }
        bindViews()
        applyLineConfiguration()
        applyImeOptions()
        applyInputType()
    }

    /**
     * Wires static text values and clipboard paste behaviour to the view.
     */
    private fun bindViews() {
        labelText.setText(labelResId)
        pasteItem.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            try {
                val textToPaste = clipboard
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                if (!textToPaste.isNullOrEmpty()) {
                    editText.setText(textToPaste)
                    editText.setSelection(textToPaste.length)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to paste from clipboard")
            }
        }
    }

    /**
     * Reads XML attributes to configure labels, visibility and input behaviour.
     */
    private fun applyAttributes(context: Context, attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.InputView, 0, 0).use { typedArray ->
            labelResId = typedArray.getResourceId(R.styleable.InputView_label, R.string.empty)
            lines = typedArray.getInt(R.styleable.InputView_lines, 1)
            inputType = typedArray.getString(R.styleable.InputView_inputType)
            imeOptions = typedArray.getString(R.styleable.InputView_imeOptions)
            val noCam = typedArray.getBoolean(R.styleable.InputView_nocam, true)
            val showHeader = typedArray.getBoolean(R.styleable.InputView_show_header, false)
            val showPaste = typedArray.getBoolean(R.styleable.InputView_show_paste, false)
            val headerTextId =
                typedArray.getResourceId(R.styleable.InputView_label, R.string.token_name)

            header.visibility = if (showHeader) View.VISIBLE else View.GONE
            header.setText(headerTextId)
            scanQrIcon.visibility = if (noCam) View.GONE else View.VISIBLE
            pasteItem.visibility = if (showPaste) View.VISIBLE else View.GONE

            if (!noCam) {
                scanQrIcon.setOnClickListener {
                    if (context is Activity) {
                        val intent = Intent(context, QRScannerActivity::class.java).apply {
                            putExtra(QrScanSource.KEY, QrScanSource.ADDRESS_TEXT_FIELD.value)
                            putExtra(QrScanResultType.KEY, QrScanResultType.ADDRESS.value)
                        }
                        context.startActivityForResult(intent, C.BARCODE_READER_REQUEST_CODE)
                    } else {
                        Timber.w("QR scan icon ignored because context is not an Activity")
                    }
                }
            }
        }
    }

    /**
     * Applies the chosen input type to the EditText.
     */
    private fun applyInputType() {
        when (inputType) {
            "number" -> editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
        }
    }

    /**
     * Applies IME options such as Next or Done on the keyboard.
     */
    private fun applyImeOptions() {
        when (imeOptions) {
            "actionNext" -> editText.imeOptions = EditorInfo.IME_ACTION_NEXT
            "actionDone" -> editText.imeOptions = EditorInfo.IME_ACTION_DONE
        }
    }

    /**
     * Applies line count and top gravity for multi-line inputs.
     */
    private fun applyLineConfiguration() {
        if (lines > 1) {
            editText.gravity = Gravity.TOP
            editText.setPadding(
                Utils.dp2px(context, 15),
                Utils.dp2px(context, 10),
                Utils.dp2px(context, 15),
                Utils.dp2px(context, 10)
            )
        }
        editText.minLines = lines
        editText.setLines(lines)
    }

    /**
     * Exposes the underlying EditText for additional configuration.
     */
    fun getEditText(): EditText = editText

    /**
     * Returns the current user input.
     */
    fun getText(): CharSequence = editText.text

    /**
     * Replaces the current text content and moves the cursor to the end.
     */
    fun setText(text: CharSequence?) {
        editText.setText(text)
        if (!text.isNullOrEmpty()) {
            editText.setSelection(text.length)
        }
    }

    /**
     * Displays or clears an error message using a string resource id.
     */
    fun setError(resId: Int) {
        if (resId == R.string.empty) {
            errorText.setText(resId)
            errorText.visibility = View.GONE
        } else {
            errorText.setText(resId)
            errorText.visibility = View.VISIBLE
        }
    }

    /**
     * Displays or hides an error message using text content.
     */
    fun setError(message: CharSequence?) {
        when {
            message == null -> errorText.visibility = View.GONE
            message.isEmpty() -> {
                errorText.text = message
                errorText.visibility = View.GONE
            }
            else -> {
                errorText.text = message
                errorText.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Updates the status label beneath the input and resets error styling if needed.
     */
    fun setStatus(statusTxt: CharSequence?) {
        if (TextUtils.isEmpty(statusTxt)) {
            statusText.visibility = View.GONE
            statusText.setText(R.string.empty)
            if (errorText.visibility == View.VISIBLE) {
                setBoxColour(BoxStatus.SELECTED)
            }
        } else {
            statusText.text = statusTxt
            statusText.visibility = View.VISIBLE
        }
    }

    /**
     * Applies box styling based on the current validation state.
     */
    private fun setBoxColour(status: BoxStatus) {
        when (status) {
            BoxStatus.ERROR -> {
                boxLayout.setBackgroundResource(R.drawable.background_input_error)
                labelText.setTextColor(ContextCompat.getColor(context, R.color.error))
            }
            BoxStatus.UNSELECTED -> {
                boxLayout.setBackgroundResource(R.drawable.background_password_entry)
                labelText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                errorText.visibility = View.GONE
            }
            BoxStatus.SELECTED -> {
                boxLayout.setBackgroundResource(R.drawable.background_input_selected)
                labelText.setTextColor(ContextCompat.getColor(context, R.color.brand))
                errorText.visibility = View.GONE
            }
        }
    }
}
