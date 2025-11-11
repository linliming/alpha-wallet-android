package com.alphawallet.app.ui

import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import com.alphawallet.app.widget.LayoutCallbackListener

/**
 * Created by JB on 5/01/2022.
 */
abstract class ImportFragment : Fragment(), View.OnClickListener, TextWatcher,
    LayoutCallbackListener {
    open fun comeIntoFocus() {}
    fun leaveFocus() {}
}
