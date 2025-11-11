package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.alphawallet.app.R

class BottomSheetHandleView(context: Context?, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    init {
        inflate(context, R.layout.layout_bottom_sheet_handle, this)
    }
}
