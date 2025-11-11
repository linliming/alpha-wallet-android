package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActionSheetInterface
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable

/**
 * Created by JB on 8/01/2021.
 */
class SignDataWidget(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val previewText: TextView
    private val messageText: TextView
    private val layoutHolder: LinearLayout
    private val moreArrow: ImageView
    private val scrollView: ScrollView
    private var sheetInterface: ActionSheetInterface? = null
    var signable: Signable? = null
        private set
    private var listener: ScrollListener? = null
    private var isScrollToBottomRequired = false

    init {
        inflate(context, R.layout.item_sign_data, this)
        previewText = findViewById<TextView>(R.id.text_preview)
        messageText = findViewById<TextView>(R.id.text_message)
        layoutHolder = findViewById<LinearLayout>(R.id.layout_holder)
        moreArrow = findViewById<ImageView>(R.id.image_more)
        scrollView = findViewById<ScrollView>(R.id.scroll_view)
        val messageTitle = findViewById<TextView>(R.id.text_message_title)
        val noTitle = getAttribute(context, attrs)
        if (noTitle) {
            messageTitle.setText("")
            messageTitle.setVisibility(GONE)
        }
    }

    private fun requireScroll() {
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (scrollView.canScrollVertically(1) || scrollView.canScrollVertically(-1)) {
                    scrollView.getViewTreeObserver()
                        .addOnScrollChangedListener(OnScrollChangedListener {
                            if (scrollView.getChildAt(0).getBottom()
                                == (scrollView.getHeight() + scrollView.getScrollY())
                            ) {
                                listener!!.hasScrolledToBottom()
                            }
                        })
                } else {
                    listener!!.hasScrolledToBottom()
                }
                scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun getAttribute(context: Context, attrs: AttributeSet?): Boolean {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.SignDataWidget,
            0, 0
        )

        return a.getBoolean(R.styleable.SignDataWidget_noTitle, false)
    }

    fun setupSignData(signable: Signable, listener: ScrollListener) {
        this.listener = listener
        isScrollToBottomRequired = true
        setupSignData(signable)
    }

    fun setupSignData(signable: Signable) {
        this.signable = signable
        var message = signable.getUserMessage().toString()

        if (signable.getMessageType() == SignMessageType.SIGN_MESSAGE)  //Warn user that sign is dangerous
        {
            previewText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_red_warning,
                0,
                0,
                0
            )
            previewText.setText(R.string.sign_message_could_be_a_scam)
            message =
                getContext().getString(R.string.sign_message_could_be_a_scam2) + "\n\n" + message
            messageText.setText(message)
        } else {
            previewText.setText(message)
            messageText.setText(message)
        }

        layoutHolder.setOnClickListener(OnClickListener { v: View? ->
            if (previewText.getVisibility() == VISIBLE) {
                previewText.setVisibility(INVISIBLE)
                scrollView.setVisibility(VISIBLE)
                scrollView.setEnabled(true)
                moreArrow.setImageResource(R.drawable.ic_expand_less_black)
                if (sheetInterface != null) sheetInterface!!.lockDragging(true)

                if (isScrollToBottomRequired) {
                    requireScroll()
                }
            } else {
                previewText.setVisibility(VISIBLE)
                scrollView.setVisibility(GONE)
                scrollView.setEnabled(false)
                moreArrow.setImageResource(R.drawable.ic_expand_more)
                if (sheetInterface != null) sheetInterface!!.lockDragging(false)
            }
        })
    }

    fun setLockCallback(asIf: ActionSheetInterface?) {
        sheetInterface = asIf
    }

    interface ScrollListener {
        fun hasScrolledToBottom()
    }
}
