package com.alphawallet.app.util

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.alphawallet.app.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener


object TabUtils {
    fun setSelectedTabFont(tabLayout: TabLayout, tab: TabLayout.Tab, typeface: Typeface?) {
        val layout = (tabLayout.getChildAt(0) as ViewGroup).getChildAt(tab.position) as LinearLayout
        val tabTextView = layout.getChildAt(1) as TextView
        if (tabTextView != null) {
            tabTextView.typeface = typeface
        }
    }

    @JvmStatic
    fun decorateTabLayout(context: Context, tabLayout: TabLayout) {
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                setSelectedTabFont(
                    tabLayout,
                    tab,
                    ResourcesCompat.getFont(context, R.font.font_semibold)
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                setSelectedTabFont(
                    tabLayout,
                    tab,
                    ResourcesCompat.getFont(context, R.font.font_regular)
                )
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        val firstTab = tabLayout.getTabAt(0)
        if (firstTab != null) {
            setSelectedTabFont(
                tabLayout,
                firstTab,
                ResourcesCompat.getFont(context, R.font.font_semibold)
            )
        }
    }

    @JvmStatic
    fun setHighlightedTabColor(context: Context, tabLayout: TabLayout) {
        val tabCount = tabLayout.tabCount

        if (tabCount > 3) {
            val tab = (tabLayout.getChildAt(0) as ViewGroup).getChildAt(tabCount - 1)
            val tabParams = tab.layoutParams as MarginLayoutParams
            tabParams.rightMargin = Utils.dp2px(context, 12)
            tab.requestLayout()
        }
    }
}
