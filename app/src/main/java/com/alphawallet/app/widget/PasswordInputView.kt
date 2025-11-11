package com.alphawallet.app.widget

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.util.Utils
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent

/**
 * Input view that encapsulates password-specific behaviour, validation state, and keyboard handling.
 */
class PasswordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), TextView.OnEditorActionListener {

    private val label: TextView
    private val error: TextView
    private val status: TextView
    private val editText: EditText
    private val togglePassword: CheckBox
    private val instruction: TextView

    private var labelResId: Int = R.string.empty
    private var lines: Int = 1
    private var inputTypeAttr: String? = null
    private var minHeight: Int = 0
    private var imeOptionsAttr: String? = null
    private var hintText: String? = null
    private var activity: Activity? = null
    private var callbackListener: LayoutCallbackListener? = null

    private val handler = Handler(Looper.getMainLooper())

    init {
        obtainAttributes(context, attrs)

        inflate(context, R.layout.layout_password_input, this)

        label = findViewById(R.id.label)
        error = findViewById(R.id.error)
        status = findViewById(R.id.status_text)
        instruction = findViewById(R.id.instruction)
        editText = findViewById(R.id.edit_text)
        togglePassword = findViewById(R.id.toggle_password)
        findViewById<TextView>(R.id.text_word_count).visibility = View.GONE

        configureViews()
        applyImeOptions()
        applyInputType()
        applyMinHeight()
        applyLines()
    }

    /**
     * Attaches layout callbacks and IME handlers to the provided activity.
     */
    fun setLayoutListener(activity: Activity, callback: LayoutCallbackListener) {
        this.activity = activity
        callbackListener = callback
        editText.setOnEditorActionListener(this)
        addKeyboardListener(callback)
    }

    /**
     * Exposes the inner [EditText] for additional configuration.
     */
    fun getEditText(): EditText = editText

    /**
     * Updates the instructional text shown below the password field.
     */
    fun setInstruction(@StringRes resourceId: Int) {
        instruction.setText(resourceId)
    }

    /**
     * Supplies text to the underlying [EditText].
     */
    fun setText(text: CharSequence?) {
        editText.setText(text)
    }

    /**
     * Retrieves the text currently entered by the user.
     */
    fun getText(): CharSequence = editText.text

    /**
     * Displays an error message sourced from resources and updates visuals accordingly.
     */
    fun setError(@StringRes resId: Int) {
        if (resId == R.string.empty) {
            clearErrorState()
        } else {
            error.setText(resId)
            displayErrorState()
        }
    }

    /**
     * Displays an error message supplied as text and updates visuals accordingly.
     */
    fun setError(message: CharSequence?) {
        when {
            message == null -> clearErrorState()
            message.isEmpty() -> {
                error.text = message
                clearErrorState()
            }
            else -> {
                error.text = message
                displayErrorState()
            }
        }
    }

    /**
     * Updates the status text that supplements password validation feedback.
     */
    fun setStatus(statusText: CharSequence?) {
        if (statusText.isNullOrEmpty()) {
            status.visibility = View.GONE
        } else {
            status.text = statusText
            status.visibility = View.VISIBLE
        }
    }

    /**
     * Indicates whether the view is currently presenting an error state.
     */
    fun isErrorState(): Boolean = error.visibility == View.VISIBLE

    /**
     * Handles editor action callbacks to validate or propagate completion events.
     */
    override fun onEditorAction(view: TextView?, actionId: Int, keyEvent: KeyEvent?): Boolean {
        return when {
            isErrorState() -> {
                flashLayout()
                true
            }
            callbackListener != null -> {
                callbackListener?.onInputDoneClick(view)
                true
            }
            else -> true
        }
    }

    /**
     * Reads and caches custom attributes supplied through XML.
     */
    private fun obtainAttributes(context: Context, attrs: AttributeSet?) {
        if (attrs == null) return
        val typedArray: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.InputView,
            0,
            0
        )

        try {
            labelResId = typedArray.getResourceId(R.styleable.InputView_label, R.string.empty)
            lines = typedArray.getInt(R.styleable.InputView_lines, 1)
            inputTypeAttr = typedArray.getString(R.styleable.InputView_inputType)
            imeOptionsAttr = typedArray.getString(R.styleable.InputView_imeOptions)
            minHeight = typedArray.getInteger(R.styleable.InputView_minHeightValue, 0)
            hintText = typedArray.getString(R.styleable.InputView_hint)
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Configures view labels and password toggle behaviour.
     */
    private fun configureViews() {
        label.setText(labelResId)
        if (labelResId != R.string.empty) {
            label.visibility = View.VISIBLE
        }
        togglePassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            editText.setSelection(editText.text?.length ?: 0)
        }
    }

    /**
     * Applies IME actions declared in XML attributes.
     */
    private fun applyImeOptions() {
        when (imeOptionsAttr) {
            "actionNext" -> editText.imeOptions = EditorInfo.IME_ACTION_NEXT
            "actionDone" -> {
                editText.imeOptions = EditorInfo.IME_ACTION_DONE
                editText.setRawInputType(InputType.TYPE_CLASS_TEXT)
            }
        }
    }

    /**
     * Applies input type behaviour declared in XML attributes.
     */
    private fun applyInputType() {
        when (inputTypeAttr) {
            "textPassword" -> {
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword.visibility = View.VISIBLE
                val horizontalPadding = Utils.dp2px(context, 15)
                val verticalPadding = Utils.dp2px(context, 5)
                editText.setPadding(
                    horizontalPadding,
                    verticalPadding,
                    Utils.dp2px(context, 50),
                    verticalPadding
                )
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            "number" -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            "textNoSuggestions" -> editText.inputType =
                editText.inputType or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        editText.typeface = Typeface.DEFAULT
        if (!hintText.isNullOrEmpty()) {
            editText.hint = hintText
        }
    }

    /**
     * Applies a minimum height to the [EditText] when specified.
     */
    private fun applyMinHeight() {
        if (minHeight <= 0) return
        val resources: Resources = resources
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minHeight.toFloat(),
            resources.displayMetrics
        ).toInt()
        editText.minHeight = px
    }

    /**
     * Applies multi-line behaviour to the [EditText] when requested.
     */
    private fun applyLines() {
        if (lines > 1) {
            editText.gravity = Gravity.TOP
            val horizontalPadding = Utils.dp2px(context, 20)
            val verticalPadding = Utils.dp2px(context, 16)
            editText.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
        }
        editText.minLines = lines
    }

    /**
     * Clears all error states and restores default styling.
     */
    private fun clearErrorState() {
        error.setText(R.string.empty)
        error.visibility = View.GONE
        editText.setBackgroundResource(R.drawable.background_password_entry)
        label.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
    }

    /**
     * Shows the error message and applies error styling to the view.
     */
    private fun displayErrorState() {
        error.visibility = View.VISIBLE
        editText.setBackgroundResource(R.drawable.background_password_error)
        label.setTextColor(ContextCompat.getColor(context, R.color.error))
    }

    /**
     * Adds a listener that reports keyboard visibility changes to the callback.
     */
    private fun addKeyboardListener(callback: LayoutCallbackListener) {
        val attachedActivity = activity ?: return
        KeyboardVisibilityEvent.setEventListener(attachedActivity) { isOpen ->
            if (isOpen) {
                callback.onLayoutShrunk()
            } else {
                callback.onLayoutExpand()
            }
        }
    }

    /**
     * Briefly flashes the password field to confirm an error state back to the user.
     */
    private fun flashLayout() {
        editText.setBackgroundResource(R.drawable.background_password_flash)
        handler.postDelayed(
            { editText.setBackgroundResource(R.drawable.background_password_error) },
            FLASH_DELAY_MS
        )
    }

    private companion object {
        private const val FLASH_DELAY_MS = 300L
    }
}
