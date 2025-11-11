package com.alphawallet.app.widget

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R


class AWalletConfirmationDialog(activity: Activity) : Dialog(activity) {
    private val container: LinearLayout
    private val title: TextView
    private val smallText: TextView
    private val mediumText: TextView
    private val bigText: TextView
    private val extraText: TextView
    protected var btnPrimary: Button
    protected var btnSecondary: Button
    protected var context: Context = activity

    init {
        setContentView(R.layout.dialog_awallet_confirmation)
        window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCanceledOnTouchOutside(true)
        window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        container = findViewById(R.id.dialog_button1_container)
        title = findViewById(R.id.dialog_main_text)
        smallText = findViewById(R.id.dialog_small_text)
        mediumText = findViewById(R.id.dialog_medium_text)
        bigText = findViewById(R.id.dialog_big_text)
        extraText = findViewById(R.id.dialog_extra_text)
        btnPrimary = findViewById(R.id.dialog_button1)
        btnSecondary = findViewById(R.id.dialog_button2)

        extraText.visibility = View.GONE
    }

    override fun setTitle(resId: Int) {
        title.text = context.resources.getString(resId)
    }

    fun setPrimaryButtonText(resId: Int) {
        btnPrimary.text = context.resources.getString(resId)
    }

    fun setSecondaryButtonText(resId: Int) {
        btnSecondary.text = context.resources.getString(resId)
    }

    fun setPrimaryButtonListener(listener: View.OnClickListener?) {
        btnPrimary.setOnClickListener(listener)
        container.setOnClickListener(listener)
    }

    fun setSecondaryButtonListener(listener: View.OnClickListener?) {
        btnSecondary.setOnClickListener(listener)
    }

    fun setBigText(text: CharSequence?) {
        bigText.visibility = View.VISIBLE
        bigText.text = text
    }

    fun setBigText(resId: Int) {
        bigText.visibility = View.VISIBLE
        bigText.text = context.resources.getString(resId)
    }

    fun setSmallText(resId: Int) {
        smallText.visibility = View.VISIBLE
        smallText.text = context.resources.getString(resId)
    }

    fun setSmallText(text: CharSequence?) {
        smallText.visibility = View.VISIBLE
        smallText.text = text
    }

    fun setMediumText(resId: Int) {
        mediumText.visibility = View.VISIBLE
        mediumText.text = context.resources.getString(resId)
    }

    fun setMediumText(text: CharSequence?) {
        mediumText.visibility = View.VISIBLE
        mediumText.text = text
    }

    fun setExtraText(resId: Int) {
        extraText.visibility = View.VISIBLE
        extraText.text = context.resources.getString(resId)
    }
}
