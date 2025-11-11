package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alphawallet.app.R
import com.google.android.material.snackbar.Snackbar

/**
 * System status container that handles progress, error, and empty states around content lists.
 */
class SystemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), View.OnClickListener {

    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View
    private lateinit var messageTxt: TextView
    private lateinit var tryAgain: View
    private lateinit var emptyBox: FrameLayout

    private var onTryAgainClickListener: OnClickListener? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var recyclerView: RecyclerView? = null

    /**
     * Inflates child views after XML inflation completes.
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        val view = LayoutInflater.from(context).inflate(R.layout.layout_system_view, this, false)
        addView(view)
        progress = view.findViewById(R.id.progress)
        errorBox = view.findViewById(R.id.error_box)
        messageTxt = view.findViewById(R.id.message)
        tryAgain = view.findViewById(R.id.try_again)
        tryAgain.setOnClickListener(this)
        emptyBox = view.findViewById(R.id.empty_box)
    }

    /**
     * Registers the swipe refresh layout used for pull-to-refresh progress indications.
     */
    fun attachSwipeRefreshLayout(swipeRefreshLayout: SwipeRefreshLayout?) {
        this.swipeRefreshLayout = swipeRefreshLayout
    }

    /**
     * Registers the recycler view used to determine whether inline refresh indicators can be shown.
     */
    fun attachRecyclerView(recyclerView: RecyclerView?) {
        this.recyclerView = recyclerView
    }

    /**
     * Hides all state indicators and the container itself.
     */
    fun hide() {
        hideAllComponents()
        visibility = GONE
    }

    /**
     * Resets all child components to a hidden state and stops any refreshing animations.
     */
    private fun hideAllComponents() {
        if (!::progress.isInitialized) {
            return
        }
        if (swipeRefreshLayout?.isRefreshing == true) {
            swipeRefreshLayout?.isRefreshing = false
        }
        emptyBox.visibility = GONE
        errorBox.visibility = GONE
        progress.visibility = GONE
        visibility = VISIBLE
    }

    /**
     * Shows the centered progress spinner regardless of list state.
     */
    fun showCentralSpinner() {
        hideAllComponents()
        progress.visibility = VISIBLE
    }

    /**
     * Toggles progress UI based on whether data is currently being retrieved.
     */
    fun showProgress(shouldShow: Boolean) {
        if (shouldShow && swipeRefreshLayout?.isRefreshing == true) {
            return
        }
        if (shouldShow) {
            val adapterItemCount = recyclerView?.adapter?.itemCount ?: 0
            if (swipeRefreshLayout != null && recyclerView != null && adapterItemCount > 0) {
                hide()
                swipeRefreshLayout?.isRefreshing = true
            } else {
                hideAllComponents()
                progress.visibility = VISIBLE
            }
        } else {
            hide()
        }
    }

    /**
     * Displays the swipe refresh progress indicator.
     */
    fun showSwipe() {
        swipeRefreshLayout?.isRefreshing = true
    }

    /**
     * Makes the primary progress spinner visible without affecting other widgets.
     */
    fun showProgress() {
        progress.visibility = VISIBLE
    }

    /**
     * Presents an error message and optional retry action.
     */
    fun showError(message: String?, onTryAgainClickListener: OnClickListener?) {
        val hasAdapterItems = recyclerView?.adapter?.itemCount ?: 0 > 0
        if (hasAdapterItems) {
            hide()
            Snackbar.make(
                this,
                message.takeUnless { it.isNullOrEmpty() } ?: context.getString(R.string.unknown_error),
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            hideAllComponents()
            errorBox.visibility = VISIBLE
            this.onTryAgainClickListener = onTryAgainClickListener
            messageTxt.text = message
            messageTxt.visibility = if (message.isNullOrEmpty()) GONE else VISIBLE
            tryAgain.visibility = if (this.onTryAgainClickListener == null) GONE else VISIBLE
        }
    }

    /**
     * Shows the empty state with no additional message.
     */
    fun showEmpty() {
        showEmpty("")
    }

    /**
     * Shows the empty state using the supplied text message.
     */
    fun showEmpty(message: String) {
        showError(message, null)
    }

    /**
     * Shows the empty state with a custom layout resource.
     */
    fun showEmpty(@LayoutRes emptyLayout: Int) {
        val view = LayoutInflater.from(context).inflate(emptyLayout, emptyBox, false)
        showEmpty(view)
    }

    /**
     * Shows the empty state with a provided view instance.
     */
    fun showEmpty(view: View) {
        hideAllComponents()
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = lp
        emptyBox.visibility = VISIBLE
        emptyBox.removeAllViews()
        emptyBox.addView(view)
    }

    /**
     * Handles clicks on the retry button by hiding the state view and delegating to the listener.
     */
    override fun onClick(v: View) {
        onTryAgainClickListener?.let {
            hide()
            it.onClick(v)
        }
    }
}
