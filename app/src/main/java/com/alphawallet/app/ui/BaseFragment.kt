package com.alphawallet.app.ui

import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.alphawallet.app.R
import com.alphawallet.app.entity.BackupTokenCallback
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.FragmentMessenger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class BaseFragment : Fragment(), Toolbar.OnMenuItemClickListener, BackupTokenCallback {
    private var toolbar: Toolbar? = null
    private var toolbarTitle: TextView? = null

    private fun initToolbar(view: View) {
        toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbarTitle = toolbar?.findViewById<TextView?>(R.id.toolbar_title)

        toolbar?.setOnClickListener(View.OnClickListener { view: View? ->
            this.onToolbarClicked(
                view
            )
        })
    }

    protected fun toolbar(view: View?) {
        if (view != null) initToolbar(view)
    }

    protected fun toolbar(view: View, title: Int, menuResId: Int) {
        initToolbar(view)
        setToolbarTitle(title)
        setToolbarMenu(menuResId)
    }

    protected fun toolbar(view: View, menuResId: Int) {
        initToolbar(view)
        setToolbarMenu(menuResId)
    }

    protected fun toolbar(view: View, menuResId: Int, listener: Toolbar.OnMenuItemClickListener?) {
        initToolbar(view)
        setToolbarMenu(menuResId)
        setToolbarMenuItemClickListener(listener)
    }

    protected fun toolbar(
        view: View,
        title: Int,
        menuResId: Int,
        listener: Toolbar.OnMenuItemClickListener?
    ) {
        initToolbar(view)
        setToolbarTitle(title)
        setToolbarMenu(menuResId)
        setToolbarMenuItemClickListener(listener)
    }

    fun setToolbarTitle(title: String?) {
        if (toolbarTitle != null) {
            toolbarTitle!!.setText(title)
        }
    }

    protected fun setToolbarMenuItemClickListener(listener: Toolbar.OnMenuItemClickListener?) {
        toolbar!!.setOnMenuItemClickListener(listener)
    }

    protected fun setToolbarTitle(title: Int) {
        setToolbarTitle(getString(title))
    }

    protected fun setToolbarMenu(menuRes: Int) {
        toolbar!!.inflateMenu(menuRes)
    }

    fun getToolbar(): Toolbar {
        return toolbar!!
    }

    override fun onMenuItemClick(menuItem: MenuItem?): Boolean {
        return false
    }

    open fun comeIntoFocus() {
        //
    }

    open fun leaveFocus() {
        //
    }

    fun softKeyboardVisible() {
    }

    fun softKeyboardGone() {
    }

    fun onItemClick(url: String?) {
    }

    open fun onToolbarClicked(view: View?) {
    }

    open fun signalPlayStoreUpdate(updateVersion: Int) {
    }

    open fun signalExternalUpdate(updateVersion: String?) {
    }

    open fun backupSeedSuccess(hasNoLock: Boolean) {
    }

    open fun storeWalletBackupTime(backedUpKey: String?) {
    }

    open fun resetTokens() {
    }

    open fun resetTransactions() {
    }

    open fun gotCameraAccess(permissions: Array<String>?, grantResults: IntArray?) {
    }

    open fun gotGeoAccess(permissions: Array<String>?, grantResults: IntArray?) {
    }

    open fun gotFileAccess(permissions: Array<String>?, grantResults: IntArray?) {
    }

    open fun handleQRCode(resultCode: Int, data: Intent?, messenger: FragmentMessenger?) {
    }

    open fun pinAuthorisation(gotAuth: Boolean) {
    }

    open fun switchNetworkAndLoadUrl(chainId: Long, url: String?) {
    }

    open fun scrollToTop() {
    }

    open fun addedToken(tokenContracts: MutableList<ContractLocator?>?) {
    }

    open fun setImportFilename(fName: String?) {
    }

    open fun backPressed() {
    }
}
