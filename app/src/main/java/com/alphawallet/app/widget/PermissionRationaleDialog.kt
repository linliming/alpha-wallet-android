package com.alphawallet.app.widget

import android.Manifest
import android.content.Context
import android.view.View
import android.widget.TextView
import com.alphawallet.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Bottom sheet dialog that explains why a runtime permission is being requested,
 * and wires primary/secondary actions to continue or cancel.
 */
class PermissionRationaleDialog(
    context: Context,
    private val permission: String,
    private val okListener: View.OnClickListener,
    private val cancelListener: View.OnClickListener
) : BottomSheetDialog(context) {

    private val titleView: TextView?
    private val bodyView: TextView?
    private val okButton: MaterialButton?
    private val cancelButton: MaterialButton?

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        setContentView(R.layout.dialog_permission_rationale)

        titleView = findViewById(R.id.title)
        bodyView = findViewById(R.id.body)
        okButton = findViewById(R.id.btn_ok)
        cancelButton = findViewById(R.id.btn_cancel)

        bindContent()
        bindListeners()
    }

    /**
     * Populates dialog labels based on the permission being requested.
     */
    private fun bindContent() {
        titleView?.text = getTitleText()
        bodyView?.text = getBodyText()
        okButton?.text = getOkButtonText()
        cancelButton?.text = getCancelButtonText()
    }

    /**
     * Connects button actions to the provided click listeners and auto-dismisses the dialog.
     */
    private fun bindListeners() {
        okButton?.setOnClickListener { view ->
            okListener.onClick(view)
            dismiss()
        }
        cancelButton?.setOnClickListener { view ->
            cancelListener.onClick(view)
            dismiss()
        }
    }

    /**
     * Resolves the OK button label for the supplied permission.
     */
    private fun getOkButtonText(): CharSequence {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS ->
                context.getString(R.string.btn_ok_request_post_notifications_permission)

            else -> ""
        }
    }

    /**
     * Resolves the cancel button label for the supplied permission.
     */
    private fun getCancelButtonText(): CharSequence {
        return context.getString(R.string.btn_skip)
    }

    /**
     * Resolves the explanatory body text for the supplied permission.
     */
    private fun getBodyText(): CharSequence {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS ->
                context.getString(R.string.body_request_post_notifications_permission)

            else -> ""
        }
    }

    /**
     * Resolves the permission rationale title for the supplied permission.
     */
    private fun getTitleText(): CharSequence {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS ->
                context.getString(R.string.title_request_post_notifications_permission)

            else -> ""
        }
    }

    companion object {
        /**
         * Shows the dialog for a given permission and action callbacks.
         */
        @JvmStatic
        fun show(
            context: Context,
            permission: String,
            okListener: View.OnClickListener,
            cancelListener: View.OnClickListener
        ) {
            val dialog = PermissionRationaleDialog(context, permission, okListener, cancelListener)
            dialog.show()
        }
    }
}
