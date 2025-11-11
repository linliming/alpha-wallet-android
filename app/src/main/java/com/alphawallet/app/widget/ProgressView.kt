package com.alphawallet.app.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.alphawallet.app.R

/**
 * Created by James on 6/02/2018.
 */

class ProgressView : RelativeLayout {
    private var progress: ProgressBar? = null
    private var counter: TextView? = null
    private var context: Context? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onFinishInflate() {
        super.onFinishInflate()

        val view =
            LayoutInflater.from(getContext()).inflate(R.layout.layout_progress_view, this, false)
        addView(view)
        progress = view.findViewById<ProgressBar>(R.id.progress_v)
        counter = view.findViewById<TextView>(R.id.textViewProgress)
        context = view.getContext()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            counter!!.setZ(1.0f)
            progress!!.setZ(0.99f)
        }
    }

    fun updateProgress(prog: Int) {
        if (prog < 100) {
            counter!!.setText(prog.toString() + "%")
            progress!!.setVisibility(VISIBLE)
            counter!!.setVisibility(VISIBLE)
            setVisibility(VISIBLE)
        } else {
            hide()
        }
    }

    //    public void displayToast(String msg)
    //    {
    //        hide();
    //        Toast.makeText(context, msg, Toast.LENGTH_SHORT);
    //    }
    fun hide() {
        hideAllComponents()
        setVisibility(GONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setZ(1.0f)
        }
    }

    private fun hideAllComponents() {
        progress!!.setVisibility(GONE)
        counter!!.setVisibility(GONE)
        setVisibility(VISIBLE)
    }

    fun showEmpty(view: View?) {
        hideAllComponents()
    }

    fun setWhiteCircle() {
        val colour = ContextCompat.getColor(context!!, R.color.surface)
        setTint(colour, false)
    }

    private fun setTint(
        @ColorInt color: Int,
        skipIndeterminate: Boolean
    ) {
        val sl = ColorStateList.valueOf(color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progress!!.setProgressTintList(sl)
            progress!!.setSecondaryProgressTintList(sl)
            if (!skipIndeterminate) progress!!.setIndeterminateTintList(sl)
        } else {
            var mode = PorterDuff.Mode.SRC_IN
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
                mode = PorterDuff.Mode.MULTIPLY
            }
            if (!skipIndeterminate && progress!!.getIndeterminateDrawable() != null) progress!!.getIndeterminateDrawable()
                .setColorFilter(color, mode)
            if (progress!!.getProgressDrawable() != null) progress!!.getProgressDrawable()
                .setColorFilter(color, mode)
        }
    }
}
