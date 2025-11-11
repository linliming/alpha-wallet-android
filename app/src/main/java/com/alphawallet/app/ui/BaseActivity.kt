package com.alphawallet.app.ui

import android.content.Intent
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.alphawallet.app.R
import com.alphawallet.app.entity.AuthenticationCallback
import com.alphawallet.app.entity.AuthenticationFailType
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.viewmodel.BaseViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.SignTransactionDialog

/**
 * Common base activity for screen-level behaviours such as toolbar configuration and auth handling.
 */
abstract class BaseActivity : AppCompatActivity() {

    private var backPressedCallback: OnBackPressedCallback? = null

    /**
     * Configures and returns the activity toolbar when present.
     */
    protected fun toolbar(): Toolbar? {
        val toolbar: Toolbar? = findViewById(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            toolbar.setTitle(R.string.empty)
        }
        enableDisplayHomeAsUp()
        return toolbar
    }

    /**
     * Updates the activity title, directing output to the custom toolbar title view when available.
     */
    override fun setTitle(title: CharSequence?) {
        val actionBar: ActionBar? = supportActionBar
        val toolbarTitle: TextView? = findViewById(R.id.toolbar_title)
        if (toolbarTitle != null) {
            actionBar?.setTitle(R.string.empty)
            toolbarTitle.text = title
        } else {
            super.setTitle(title)
        }
        setDispatcher()
    }

    /**
     * Updates the activity subtitle when supported by the current action bar.
     */
    protected fun setSubtitle(subtitle: String?) {
        supportActionBar?.subtitle = subtitle
    }

    /**
     * Enables the up/home affordance using the default indicator.
     */
    protected fun enableDisplayHomeAsUp() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Enables the up/home affordance with a custom icon resource.
     */
    protected fun enableDisplayHomeAsUp(@DrawableRes resourceId: Int) {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(resourceId)
        }
    }

    /**
     * Configures the home icon to act as a home button rather than back navigation.
     */
    protected fun enableDisplayHomeAsHome(active: Boolean) {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(active)
            setHomeAsUpIndicator(R.drawable.ic_browser_home)
        }
    }

    /**
     * Disables the up/home affordance entirely.
     */
    protected fun disableDisplayHomeAsUp() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    /**
     * Hides the toolbar when an action bar is present.
     */
    protected fun hideToolbar() {
        supportActionBar?.hide()
    }

    /**
     * Shows the toolbar when an action bar is present.
     */
    protected fun showToolbar() {
        supportActionBar?.show()
    }

    /**
     * Handles home button interactions by delegating to the back press handler.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackPressed()
            finish()
        }
        return true
    }

    /**
     * Displays a short toast message and clears any queued toast state.
     */
    protected fun displayToast(message: String?) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            BaseViewModel.onPushToast(null)
        }
    }

    /**
     * Receives results from external authentication flows (e.g. device credentials).
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = authCallback ?: return

        val lowerBound = SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS
        val upperBound = lowerBound + REQUEST_CODE_RANGE
        if (requestCode in lowerBound..upperBound) {
            val taskIndex = requestCode - lowerBound
            val taskCode = Operation.values()[taskIndex]
            if (resultCode == RESULT_OK) {
                callback.authenticatePass(taskCode)
            } else {
                callback.authenticateFail("", AuthenticationFailType.PIN_FAILED, taskCode)
            }
            authCallback = null
        }
    }

    /**
     * Presents an alert dialog with the supplied error message.
     */
    protected fun displayErrorMessage(message: String) {
        val dialog = AWalletAlertDialog(this)
        dialog.setTitle(R.string.title_dialog_error)
        dialog.setMessage(message)
        dialog.setButtonText(R.string.ok)
        dialog.setButtonListener { dialog.dismiss() }
        dialog.show()
    }

    /**
     * Installs a back-press dispatcher that routes to [handleBackPressed].
     */
    protected fun setDispatcher() {
        backPressedCallback?.remove()
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPressed()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }
    }

    /**
     * Default back press handling that finishes the activity; subclasses may override.
     */
    protected open fun handleBackPressed() {
        finish()
    }

    companion object {
        private const val REQUEST_CODE_RANGE = 10

        /**
         * Static callback used exclusively for transaction signing authentication responses.
         */
        @JvmStatic
        var authCallback: AuthenticationCallback? = null
    }
}
