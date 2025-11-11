package com.alphawallet.app.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.alphawallet.app.R
import com.alphawallet.app.ui.widget.entity.SearchToolbarCallback
import com.alphawallet.app.util.KeyboardUtils.hideKeyboard
import com.alphawallet.app.util.KeyboardUtils.showKeyboard

/**
 * Created by JB on 9/12/2021.
 */
class SearchToolbar : FrameLayout, Runnable {
    private val back: View
    val editView: EditText?
    private val clearText: View
    private var searchCallback: SearchToolbarCallback? = null

    private val delayHandler = Handler(Looper.getMainLooper())

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        inflate(getContext(), R.layout.input_toolbar, this)
        back = findViewById<View>(R.id.st_backArrow)
        this.editView = findViewById<EditText?>(R.id.st_editText)
        clearText = findViewById<View>(R.id.st_clear)

        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        inflate(getContext(), R.layout.input_toolbar, this)
        back = findViewById<View>(R.id.st_backArrow)
        this.editView = findViewById<EditText?>(R.id.st_editText)
        clearText = findViewById<View>(R.id.st_clear)

        init()
    }

    fun setSearchCallback(cb: SearchToolbarCallback) {
        searchCallback = cb
        back.setOnClickListener(OnClickListener { v: View? ->
            cb.backPressed()
        })

        editView!!.requestFocus()

        showKeyboard(this.editView)
    }

    private fun init() {
        //draw focus
        editView!!.addTextChangedListener(textWatcher)
        clearText.setOnClickListener(OnClickListener { v: View? ->
            editView.setText("")
        })

        editView.setOnEditorActionListener(OnEditorActionListener { textView: TextView?, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (searchCallback != null) searchCallback!!.searchText(
                    editView.getText().toString()
                )
                textView!!.clearFocus()
                hideKeyboard(textView)
            }
            actionId == EditorInfo.IME_ACTION_SEARCH
        })
    }

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun afterTextChanged(s: Editable?) {
            delayHandler.removeCallbacksAndMessages(null)
            delayHandler.postDelayed(runnable, 250)
        }
    }

    val runnable: Runnable
        get() = this

    override fun run() {
        if (this.editView == null || editView.getText() == null) return
        if (searchCallback != null) searchCallback!!.searchText(editView.getText().toString())
    }
}
