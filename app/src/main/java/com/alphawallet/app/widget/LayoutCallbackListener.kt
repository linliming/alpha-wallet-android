package com.alphawallet.app.widget

import android.view.View

interface LayoutCallbackListener {
    fun onLayoutShrunk()
    fun onLayoutExpand()
    fun onInputDoneClick(view: View?)
}
