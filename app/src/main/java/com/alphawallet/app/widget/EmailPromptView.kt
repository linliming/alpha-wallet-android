package com.alphawallet.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.text.InputType
import android.util.Patterns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import com.alphawallet.app.R
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.repository.KeyProviderFactory.get
import com.alphawallet.app.util.KeyboardUtils.hideKeyboard
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mailchimp.sdk.api.model.Contact
import com.mailchimp.sdk.api.model.ContactStatus
import com.mailchimp.sdk.core.MailchimpSdkConfiguration
import com.mailchimp.sdk.main.Mailchimp

@SuppressLint("ViewConstructor")
class EmailPromptView(
    context: Context,
    private val successOverlay: View?,
    private val handler: Handler,
    private val onSuccessRunnable: Runnable
) :
    LinearLayout(context), StandardFunctionInterface {
    private var parentDialog: BottomSheetDialog? = null

    fun setParentDialog(parentDialog: BottomSheetDialog?) {
        this.parentDialog = parentDialog
    }

    private var emailInput: InputView = TODO()

    init {
        init(R.layout.layout_dialog_email_prompt)
    }

    private fun init(@LayoutRes layoutId: Int) {
        LayoutInflater.from(context).inflate(layoutId, this, true)

        val functionBar = findViewById<FunctionButtonBar>(R.id.layoutButtons)
        functionBar.setupFunctions(this, ArrayList(listOf(R.string.action_want_to_receive_email)))
        functionBar.revealButtons()

        emailInput = findViewById(R.id.email_input)
        emailInput.getEditText().inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        emailInput.getEditText().setOnKeyListener(OnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleClick(context.getString(R.string.action_want_to_receive_email), 0)
                return@OnKeyListener true
            }
            false
        })
    }

    override fun handleClick(action: String?, actionId: Int) {
        if (action == context.getString(R.string.action_want_to_receive_email)) {
            // validate email
            val email = emailInput.getText().toString()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.setError(R.string.email_is_invalid)
                return
            }

            val sdkKey = get().getMailchimpKey()
            try {
                hideKeyboard(this)

                val configuration = MailchimpSdkConfiguration.Builder(
                    context, sdkKey
                )
                    .isAutoTaggingEnabled(true)
                    .build()
                val mailchimpSdk = Mailchimp.initialize(configuration)

                val contact = Contact.Builder(email)
                    .setContactStatus(ContactStatus.SUBSCRIBED)
                    .build()

                mailchimpSdk.createOrUpdateContact(contact)
            } catch (ignored: IllegalArgumentException) {
            }

            parentDialog!!.dismiss()

            if (successOverlay != null) successOverlay.visibility = VISIBLE
            handler.postDelayed(onSuccessRunnable, 1000)
        }
    }

    companion object {
        val mailchimpKey: String?
            external get
    }
}
