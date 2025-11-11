package com.alphawallet.app.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.res.ResourcesCompat
import com.alphawallet.app.R
import com.alphawallet.app.entity.WalletPage
import java.util.LinkedHashSet

/**
 * 应用底部导航栏，负责页面切换与设置角标展示。
 */
class AWalletBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dappBrowserLabel: TextView
    private val walletLabel: TextView
    private val settingsBadge: TextView
    private val settingsLabel: TextView
    private val settingsTab: RelativeLayout
    private val activityLabel: TextView

    private val regularTypeface: Typeface?
    private val semiBoldTypeface: Typeface?

    private val settingsBadgeKeys = LinkedHashSet<String>()
    private var listener: OnBottomNavigationItemSelectedListener? = null
    private var selectedItem: WalletPage = WalletPage.WALLET

    init {
        inflate(context, R.layout.layout_bottom_navigation, this)
        walletLabel = findViewById(R.id.nav_wallet_text)
        activityLabel = findViewById(R.id.nav_activity_text)
        dappBrowserLabel = findViewById(R.id.nav_browser_text)
        settingsTab = findViewById(R.id.settings_tab)
        settingsLabel = findViewById(R.id.nav_settings_text)
        settingsBadge = findViewById(R.id.settings_badge)

        walletLabel.setOnClickListener { selectItem(WalletPage.WALLET) }
        activityLabel.setOnClickListener { selectItem(WalletPage.ACTIVITY) }
        dappBrowserLabel.setOnClickListener { selectItem(WalletPage.DAPP_BROWSER) }
        settingsTab.setOnClickListener { selectItem(WalletPage.SETTINGS) }

        regularTypeface = ResourcesCompat.getFont(context, R.font.font_regular)
        semiBoldTypeface = ResourcesCompat.getFont(context, R.font.font_semibold)

        setSelectedItem(WalletPage.WALLET)
    }

    /**
     * 注册底部导航点击监听器。
     */
    fun setListener(listener: OnBottomNavigationItemSelectedListener?) {
        this.listener = listener
    }

    /**
     * 当前选中的导航页签。
     */
    fun getSelectedItem(): WalletPage = selectedItem

    /**
     * 主动触发导航项选择，并通知监听器。
     */
    @MainThread
    private fun selectItem(page: WalletPage) {
        listener?.onBottomNavigationItemSelected(page)
    }

    /**
     * 更新底部导航的选中状态。
     */
    @MainThread
    fun setSelectedItem(page: WalletPage) {
        deselectAll()
        selectedItem = page
        when (page) {
            WalletPage.DAPP_BROWSER -> highlight(dappBrowserLabel)
            WalletPage.WALLET -> highlight(walletLabel)
            WalletPage.SETTINGS -> highlight(settingsLabel)
            WalletPage.ACTIVITY -> highlight(activityLabel)
        }
    }

    /**
     * 显示设置模块角标数量。
     */
    fun setSettingsBadgeCount(count: Int) {
        settingsBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        settingsBadge.text = count.toString()
    }

    /**
     * 新增角标 key，用于统计未读数量。
     */
    fun addSettingsBadgeKey(key: String) {
        if (settingsBadgeKeys.add(key)) {
            showOrHideSettingsBadge()
        }
    }

    /**
     * 删除角标 key。
     */
    fun removeSettingsBadgeKey(key: String) {
        if (settingsBadgeKeys.remove(key)) {
            showOrHideSettingsBadge()
        }
    }

    /**
     * 隐藏浏览器标签页。
     */
    fun hideBrowserTab() {
        dappBrowserLabel.visibility = View.GONE
    }

    private fun deselectAll() {
        resetState(dappBrowserLabel)
        resetState(walletLabel)
        resetState(settingsLabel)
        resetState(activityLabel)
    }

    private fun showOrHideSettingsBadge() {
        val count = settingsBadgeKeys.size
        settingsBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        settingsBadge.text = count.toString()
    }

    private fun highlight(label: TextView) {
        label.isSelected = true
        label.typeface = semiBoldTypeface ?: Typeface.DEFAULT_BOLD
    }

    private fun resetState(label: TextView) {
        label.isSelected = false
        label.typeface = regularTypeface ?: Typeface.DEFAULT
    }

    fun interface OnBottomNavigationItemSelectedListener {
        fun onBottomNavigationItemSelected(index: WalletPage): Boolean
    }
}
