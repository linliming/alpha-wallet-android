package com.alphawallet.app.widget

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.alphawallet.app.R
import com.alphawallet.app.api.v1.entity.ApiV1
import com.alphawallet.app.api.v1.entity.Metadata
import com.alphawallet.app.api.v1.entity.Method
import com.alphawallet.app.api.v1.entity.request.ApiV1Request
import com.google.android.material.bottomsheet.BottomSheetDialog

class ApiV1Dialog(context: Context) : BottomSheetDialog(context) {
    private val toolbar: BottomSheetToolbarView
    private val context: Context
    private val functionButtonBar: FunctionButtonBar
    private val infoLayout: LinearLayout

    init {
        val view = View.inflate(getContext(), R.layout.dialog_api_v1, null)
        setContentView(view)
        this.context = context
        toolbar = view.findViewById(R.id.bottom_sheet_toolbar)
        infoLayout = view.findViewById(R.id.layout_info)
        functionButtonBar = view.findViewById(R.id.layoutButtons)
        setCanceledOnTouchOutside(false)
        behavior.isDraggable = false
    }

    constructor(context: Context, request: ApiV1Request) : this(context) {
        setMetadata(request.metadata!!)
        setMethod(request.method!!)
    }

    private fun setMethod(method: Method) {
        if (method.callType == ApiV1.CallType.CONNECT) {
            toolbar.setTitle(R.string.title_api_v1_connect_to)
            functionButtonBar.setPrimaryButtonText(R.string.action_connect)
            functionButtonBar.setSecondaryButtonText(R.string.dialog_reject)
        } else if (method.callType == ApiV1.CallType.SIGN_PERSONAL_MESSAGE) {
            toolbar.setTitle(R.string.dialog_title_sign_transaction)
            functionButtonBar.setPrimaryButtonText(R.string.action_sign)
            functionButtonBar.setSecondaryButtonText(R.string.action_cancel)
        }
    }

    private fun setMetadata(metadata: Metadata) {
        if (metadata.iconUrl != null) {
            toolbar.setLogo(context, metadata.iconUrl)
        }
        addWidget(R.string.label_api_v1_app_name, metadata.name)
        addWidget(R.string.label_api_v1_app_url, metadata.appUrl)
    }

    fun setPrimaryButtonListener(listener: View.OnClickListener?) {
        functionButtonBar.setPrimaryButtonClickListener(listener)
    }

    fun setSecondaryButtonListener(listener: View.OnClickListener?) {
        functionButtonBar.setSecondaryButtonClickListener(listener)
        toolbar.setCloseListener(listener)
    }

    fun addWidget(labelRes: Int, value: String?) {
        if (!TextUtils.isEmpty(value)) {
            val widget = SimpleSheetWidget(getContext(), labelRes)
            widget.setValue(value)
            infoLayout.addView(widget)
        }
    }

    fun addWidget(view: View?) {
        infoLayout.addView(view)
    }

    fun hideFunctionBar() {
        functionButtonBar.visibility = View.GONE
    }
}
