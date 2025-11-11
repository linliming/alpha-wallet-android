package com.alphawallet.app.viewmodel

import android.view.View
import com.alphawallet.app.R
import com.alphawallet.app.entity.WalletPage
import com.alphawallet.app.ui.BaseActivity
import com.alphawallet.app.widget.AWalletBottomNavigationView
import com.alphawallet.app.widget.AWalletBottomNavigationView.OnBottomNavigationItemSelectedListener

open class BaseNavigationActivity : BaseActivity(), OnBottomNavigationItemSelectedListener {
    private var nav: AWalletBottomNavigationView? = null

    protected fun initBottomNavigation() {
        nav = findViewById<AWalletBottomNavigationView?>(R.id.nav)
        nav?.setListener(this)
    }

    protected fun selectNavigationItem(position: WalletPage) {
        nav?.setSelectedItem(position)
    }

    override fun onBottomNavigationItemSelected(index: WalletPage): Boolean {
        nav?.setSelectedItem(index)
        return false
    }

    protected val selectedItem: WalletPage?
        get() =  nav?.getSelectedItem()

    fun setSettingsBadgeCount(count: Int) {
        nav?.setSettingsBadgeCount(count)
    }

    fun addSettingsBadgeKey(key: String) {
        nav?.addSettingsBadgeKey(key)
    }

    fun removeSettingsBadgeKey(key: String) {
        nav?.removeSettingsBadgeKey(key)
    }

    fun removeDappBrowser() {
        nav?.hideBrowserTab()
    }

    fun hideNavBar() {
        nav?.visibility = View.GONE
    }

    val navBarHeight: Int?
        get() = nav?.height

    val isNavBarVisible: Boolean
        get() = nav?.visibility == View.VISIBLE

    fun setNavBarVisibility(view: Int) {
        if (nav == null) nav = findViewById<AWalletBottomNavigationView?>(R.id.nav)
        nav?.visibility = view
    }
}
