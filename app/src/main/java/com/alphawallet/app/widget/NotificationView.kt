package com.alphawallet.app.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.google.android.material.card.MaterialCardView

/**
 * Custom view that renders a notification block with configurable text, buttons, and colors.
 */
class NotificationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val layout: MaterialCardView
    private val title: TextView
    private val message: TextView
    private val primaryButton: Button
    private val secondaryButton: Button

    init {
        inflate(context, R.layout.layout_notification_view, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        layout = findViewById(R.id.layout)
        title = findViewById(R.id.title)
        message = findViewById(R.id.message)
        primaryButton = findViewById(R.id.btn_primary)
        secondaryButton = findViewById(R.id.btn_secondary)

        applyAttributes(context, attrs)
    }

    /**
     * Applies XML attributes to configure initial colours when provided.
     */
    private fun applyAttributes(context: Context, attrs: AttributeSet?) {
        if (attrs == null) return
        val typedArray: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.NotificationView,
            0,
            0
        )

        try {
            val backgroundRes =
                typedArray.getResourceId(R.styleable.NotificationView_backgroundColour, COLOR_UNDEFINED)
            val textColorRes =
                typedArray.getResourceId(R.styleable.NotificationView_textColor, COLOR_UNDEFINED)

            if (backgroundRes != COLOR_UNDEFINED) {
                setNotificationBackgroundColorResource(backgroundRes)
            }
            if (textColorRes != COLOR_UNDEFINED) {
                setNotificationTextColorResource(textColorRes)
            }
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Registers a primary button click listener when available.
     */
    fun setPrimaryButtonListener(listener: OnPrimaryButtonClickListener?) {
        if (listener == null) {
            primaryButton.setOnClickListener(null)
            return
        }
        primaryButton.setOnClickListener { listener.onPrimaryButtonClicked() }
    }

    /**
     * Registers a secondary button click listener when available.
     */
    fun setSecondaryButtonListener(listener: OnSecondaryButtonClickListener?) {
        if (listener == null) {
            secondaryButton.setOnClickListener(null)
            return
        }
        secondaryButton.setOnClickListener { listener.onSecondaryButtonClicked() }
    }

    /**
     * Updates the title text shown in the notification block.
     */
    fun setTitle(titleText: CharSequence) {
        title.text = titleText
    }

    /**
     * Updates the message text shown in the notification block.
     */
    fun setMessage(messageText: CharSequence) {
        message.text = messageText
    }

    /**
     * Sets the primary button label and toggles visibility to match availability.
     */
    fun setPrimaryButtonText(primaryButtonText: CharSequence?) {
        if (primaryButtonText.isNullOrEmpty()) {
            primaryButton.visibility = View.GONE
        } else {
            primaryButton.visibility = View.VISIBLE
            primaryButton.text = primaryButtonText
        }
    }

    /**
     * Sets the secondary button label and toggles visibility to match availability.
     */
    fun setSecondaryButtonText(secondaryButtonText: CharSequence?) {
        if (secondaryButtonText.isNullOrEmpty()) {
            secondaryButton.visibility = View.GONE
        } else {
            secondaryButton.visibility = View.VISIBLE
            secondaryButton.text = secondaryButtonText
        }
    }

    /**
     * Assigns the background colour using either a resolved colour or resource identifier.
     */
    fun setNotificationBackgroundColor(@ColorInt color: Int) {
        layout.setCardBackgroundColor(color)
    }

    /**
     * Assigns the background colour using a colour resource identifier.
     */
    fun setNotificationBackgroundColorResource(@ColorRes colorRes: Int) {
        setNotificationBackgroundColor(ContextCompat.getColor(context, colorRes))
    }

    /**
     * Assigns the text colour using either a resolved colour or resource identifier.
     */
    fun setNotificationTextColor(@ColorInt color: Int) {
        title.setTextColor(color)
        message.setTextColor(color)
        primaryButton.setTextColor(color)
        secondaryButton.setTextColor(color)
    }

    /**
     * Assigns the text colour using a colour resource identifier.
     */
    fun setNotificationTextColorResource(@ColorRes colorRes: Int) {
        setNotificationTextColor(ContextCompat.getColor(context, colorRes))
    }

    /**
     * Builds configured instances of [NotificationView].
     */
    class Builder(private val context: Context) {
        private var backgroundColorRes: Int = COLOR_UNDEFINED
        private var textColorRes: Int = COLOR_UNDEFINED
        private var titleRes: Int = VALUE_UNDEFINED
        private var messageRes: Int = VALUE_UNDEFINED
        private var primaryButtonTextRes: Int = VALUE_UNDEFINED
        private var secondaryButtonTextRes: Int = VALUE_UNDEFINED
        private var primaryButtonClickListener: OnPrimaryButtonClickListener? = null
        private var secondaryButtonClickListener: OnSecondaryButtonClickListener? = null

        /**
         * Sets the title string resource used by the notification.
         */
        fun setTitle(@StringRes titleRes: Int): Builder = apply {
            this.titleRes = titleRes
        }

        /**
         * Sets the message string resource used by the notification.
         */
        fun setMessage(@StringRes messageRes: Int): Builder = apply {
            this.messageRes = messageRes
        }

        /**
         * Sets the background colour resource identifier.
         */
        fun setBackgroundColor(@ColorRes backgroundColorRes: Int): Builder = apply {
            this.backgroundColorRes = backgroundColorRes
        }

        /**
         * Sets the text colour resource identifier.
         */
        fun setTextColor(@ColorRes textColorRes: Int): Builder = apply {
            this.textColorRes = textColorRes
        }

        /**
         * Sets the primary button label resource identifier.
         */
        fun setPrimaryButtonText(@StringRes primaryButtonTextRes: Int): Builder = apply {
            this.primaryButtonTextRes = primaryButtonTextRes
        }

        /**
         * Sets the secondary button label resource identifier.
         */
        fun setSecondaryButtonText(@StringRes secondaryButtonTextRes: Int): Builder = apply {
            this.secondaryButtonTextRes = secondaryButtonTextRes
        }

        /**
         * Registers a callback invoked when the primary button is pressed.
         */
        fun setPrimaryButtonClickListener(listener: OnPrimaryButtonClickListener?): Builder = apply {
            primaryButtonClickListener = listener
        }

        /**
         * Registers a callback invoked when the secondary button is pressed.
         */
        fun setSecondaryButtonClickListener(listener: OnSecondaryButtonClickListener?): Builder = apply {
            secondaryButtonClickListener = listener
        }

        /**
         * Creates the configured [NotificationView] instance.
         */
        fun build(): NotificationView {
            val view = NotificationView(context)
            if (titleRes != VALUE_UNDEFINED) {
                view.setTitle(context.getString(titleRes))
            }
            if (messageRes != VALUE_UNDEFINED) {
                view.setMessage(context.getString(messageRes))
            }
            if (backgroundColorRes != COLOR_UNDEFINED) {
                view.setNotificationBackgroundColorResource(backgroundColorRes)
            }
            if (textColorRes != COLOR_UNDEFINED) {
                view.setNotificationTextColorResource(textColorRes)
            }
            if (primaryButtonTextRes != VALUE_UNDEFINED) {
                view.setPrimaryButtonText(context.getString(primaryButtonTextRes))
                view.setPrimaryButtonListener(primaryButtonClickListener)
            }
            if (secondaryButtonTextRes != VALUE_UNDEFINED) {
                view.setSecondaryButtonText(context.getString(secondaryButtonTextRes))
                view.setSecondaryButtonListener(secondaryButtonClickListener)
            }
            return view
        }
    }

    /**
     * Listener notified when the primary button is selected.
     */
    fun interface OnPrimaryButtonClickListener {
        /**
         * Invoked when the primary button is pressed.
         */
        fun onPrimaryButtonClicked()
    }

    /**
     * Listener notified when the secondary button is selected.
     */
    fun interface OnSecondaryButtonClickListener {
        /**
         * Invoked when the secondary button is pressed.
         */
        fun onSecondaryButtonClicked()
    }

    private companion object {
        private const val COLOR_UNDEFINED = -1
        private const val VALUE_UNDEFINED = -1
    }
}
