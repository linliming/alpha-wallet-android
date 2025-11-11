package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.util.Utils
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsItemView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private var layout: RelativeLayout? = null
    private var icon: ImageView? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var toggle: SwitchMaterial? = null
    private var arrow: ImageView? = null
    private var type: Type? = null
    private var iconRes = 0
    private var titleRes = 0
    private var subtitleRes = 0
    private var typeStr: String? = null

    enum class Type {
        DEFAULT,
        TOGGLE
    }

    class Builder(private val context: Context) {
        private var iconResId = -1
        private var titleResId = -1
        private var subtitleResId = -1
        private var type: Type? = Type.DEFAULT
        private var listener: OnSettingsItemClickedListener? = null

        fun withIcon(iconResId: Int): Builder {
            this.iconResId = iconResId
            return this
        }

        fun withTitle(titleResId: Int): Builder {
            this.titleResId = titleResId
            return this
        }

        fun withSubtitle(subtitleResId: Int): Builder {
            this.subtitleResId = subtitleResId
            return this
        }

        fun withType(type: Type?): Builder {
            this.type = type
            return this
        }

        fun withListener(listener: OnSettingsItemClickedListener?): Builder {
            this.listener = listener
            return this
        }

        fun build(): SettingsItemView {
            val view = SettingsItemView(context)
            view.setIcon(iconResId)
            view.setTitle(titleResId)
            view.setSubtitle(subtitleResId)
            view.setSettingsItemType(type)
            view.setListener(listener)
            return view
        }
    }

    interface OnSettingsItemClickedListener {
        fun onSettingsItemClicked()
    }

    init {
        inflate(context, R.layout.layout_settings_item, this)

        setLayoutParams(LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        initializeViews()

        processAttrs(context, attrs)
    }

    private fun processAttrs(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            getAttrs(context, attrs)
            if (iconRes != -1) {
                icon!!.setImageResource(iconRes)
            } else {
                icon!!.setVisibility(GONE)
            }
            if (titleRes != -1) title!!.setText(titleRes)
            if (subtitleRes != -1) setSubtitle(subtitleRes)
            if (typeStr == "toggle") {
                setSettingsItemType(Type.TOGGLE)
            } else {
                setSettingsItemType(Type.DEFAULT)
            }
        }
    }

    private fun initializeViews() {
        layout = findViewById<RelativeLayout>(R.id.layout_setting)
        icon = findViewById<ImageView>(R.id.setting_icon)
        title = findViewById<TextView>(R.id.setting_title)
        subtitle = findViewById<TextView>(R.id.setting_subtitle)
        toggle = findViewById<SwitchMaterial>(R.id.setting_switch)
        arrow = findViewById<ImageView>(R.id.arrow_right)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.SettingsItemView,
            0, 0
        )

        try {
            iconRes = a.getResourceId(R.styleable.SettingsItemView_settingIcon, -1)
            titleRes = a.getResourceId(R.styleable.SettingsItemView_settingTitle, -1)
            subtitleRes = a.getResourceId(R.styleable.SettingsItemView_settingSubtitle, -1)
            typeStr = a.getString(R.styleable.SettingsItemView_settingType)
        } finally {
            a.recycle()
        }
    }

    fun setListener(listener: OnSettingsItemClickedListener?) {
        if (listener != null) {
            if (type == Type.TOGGLE) {
                layout!!.setOnClickListener(OnClickListener { v: View? ->
                    toggle!!.toggle()
                    listener.onSettingsItemClicked()
                })
            } else {
                layout!!.setOnClickListener(OnClickListener { v: View? -> listener.onSettingsItemClicked() })
            }
        }
    }

    fun setTitle(titleText: String?) {
        title!!.setText(titleText)
    }

    fun setSubtitle(subtitleText: String) {
        if (subtitleText.isEmpty()) {
            setLayoutHeight(60)
            subtitle!!.setVisibility(GONE)
        } else {
            setLayoutHeight(80)
            subtitle!!.setVisibility(VISIBLE)
            subtitle!!.setText(subtitleText)
        }
    }

    fun getSubtitle(): String {
        if (subtitle!!.getVisibility() == VISIBLE) {
            return subtitle!!.getText().toString()
        } else {
            return ""
        }
    }

    var toggleState: Boolean
        get() = toggle!!.isChecked()
        set(toggled) {
            toggle!!.setChecked(toggled)
        }

    private fun setIcon(resId: Int) {
        if (resId != -1) {
            icon!!.setImageResource(resId)
        }
    }

    private fun setTitle(resId: Int) {
        if (resId != -1) {
            title!!.setText(resId)
        }
    }

    private fun setSubtitle(resId: Int) {
        if (resId != -1) {
            setSubtitle(getContext().getString(resId))
        }
    }

    private fun setLayoutHeight(dp: Int) {
        layout!!.setLayoutParams(
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                Utils.dp2px(getContext(), dp)
            )
        )
    }

    private fun setSettingsItemType(type: Type?) {
        this.type = type
        if (type == Type.TOGGLE) {
            arrow!!.setVisibility(GONE)
            toggle!!.setVisibility(VISIBLE)
        } else {
            toggle!!.setVisibility(GONE)
            arrow!!.setVisibility(VISIBLE)
        }
    }
}

