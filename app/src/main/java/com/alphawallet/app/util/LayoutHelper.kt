package com.alphawallet.app.util

import android.widget.ListView

object LayoutHelper {
    @JvmStatic
    fun resizeList(listView: ListView) {
        val listAdapter = listView.adapter ?: return
        //set listAdapter in loop for getting final size
        var totalHeight = 0
        for (size in 0..<listAdapter.count) {
            val listItem = listAdapter.getView(size, null, listView)
            listItem.measure(0, 0)
            totalHeight += listItem.measuredHeight
        }
        //setting listview item in adapter
        val params = listView.layoutParams
        params.height = totalHeight + (listView.dividerHeight * (listAdapter.count - 1))
        listView.layoutParams = params
    }
}
