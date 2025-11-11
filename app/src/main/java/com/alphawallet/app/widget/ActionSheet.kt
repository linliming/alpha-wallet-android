package com.alphawallet.app.widget

import android.content.Context
import android.content.DialogInterface
import android.widget.FrameLayout
import com.alphawallet.app.entity.ActionSheetInterface
import com.alphawallet.app.entity.ActionSheetStatus
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Created by JB on 20/11/2022.
 */
abstract class ActionSheet(context: Context) : BottomSheetDialog(context),
    ActionSheetInterface {
    @JvmField
    var actionSheetStatus: ActionSheetStatus = ActionSheetStatus.OK

    fun forceDismiss() {
        setOnDismissListener { v: DialogInterface? -> }
        dismiss()
    }

    override fun fullExpand() {
        val bottomSheet = findViewById<FrameLayout>(R.id.design_bottom_sheet)
        if (bottomSheet != null) BottomSheetBehavior.from(bottomSheet).state =
            BottomSheetBehavior.STATE_EXPANDED
    }

    override fun lockDragging(lock: Boolean) {
        behavior.isDraggable = !lock

        //ensure view fully expanded when locking scroll. Otherwise we may not be able to see our expanded view
        if (lock) {
            val bottomSheet = findViewById<FrameLayout>(R.id.design_bottom_sheet)
            if (bottomSheet != null) BottomSheetBehavior.from(bottomSheet).state =
                BottomSheetBehavior.STATE_EXPANDED
        }
    }

    open fun gotAuthorisation(gotAuth: Boolean) {
    }
}
