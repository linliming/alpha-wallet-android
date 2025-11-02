package com.alphawallet.app.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

object KeyboardUtils {
    @JvmStatic
    fun showKeyboard(view: View?) {
        if (view == null || view.context == null) return
        val inputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager?.showSoftInput(
            view,
            InputMethodManager.SHOW_IMPLICIT
        )
    }

    @JvmStatic
    fun hideKeyboard(view: View?) {
        if (view == null || view.context == null) return
        val inputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(
            view.windowToken, 0
        )
    }
}
