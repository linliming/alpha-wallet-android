package com.alphawallet.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.DApp
import com.alphawallet.app.ui.widget.OnDappClickListener
import com.alphawallet.app.ui.widget.OnHistoryItemRemovedListener
import com.alphawallet.app.ui.widget.adapter.BrowserHistoryAdapter
import com.alphawallet.app.util.DappBrowserUtils.clearHistory
import com.alphawallet.app.util.DappBrowserUtils.getBrowserHistory
import com.alphawallet.app.util.DappBrowserUtils.removeFromHistory
import com.alphawallet.app.viewmodel.BrowserHistoryViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrowserHistoryFragment : BaseFragment() {
    private var viewModel: BrowserHistoryViewModel? = null
    private var adapter: BrowserHistoryAdapter? = null
    private var dialog: AWalletAlertDialog? = null
    private var clear: TextView? = null
    private var noHistory: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_browser_history, container, false)
        adapter = BrowserHistoryAdapter(
            this.data,
            OnDappClickListener { dapp: DApp? ->
                setFragmentResult(
                    DappBrowserFragment.DAPP_CLICK,
                    dapp
                )
            },
            OnHistoryItemRemovedListener { dapp: DApp? -> this.onHistoryItemRemoved(dapp!!) })
        val list = view.findViewById<RecyclerView>(R.id.my_dapps_list)
        list.setNestedScrollingEnabled(false)
        list.setLayoutManager(LinearLayoutManager(getActivity()))
        list.setAdapter(adapter)

        noHistory = view.findViewById<TextView>(R.id.no_history)
        clear = view.findViewById<TextView>(R.id.clear)
        clear?.setOnClickListener(View.OnClickListener { v: View? ->
            dialog = AWalletAlertDialog(activity)
            dialog?.setTitle(R.string.dialog_title_clear)
            dialog?.setMessage(R.string.dialog_message_clear)
            dialog?.setIcon(AWalletAlertDialog.NONE)
            dialog?.setButtonText(R.string.action_clear)
            dialog?.setButtonListener(View.OnClickListener { v1: View? ->
                clearHistory()
                dialog?.dismiss()
            })
            dialog?.setSecondaryButtonText(R.string.dialog_cancel_back)
            dialog?.show()
        })
        viewModel =
            ViewModelProvider(this).get<BrowserHistoryViewModel>(BrowserHistoryViewModel::class.java)
        showOrHideViews()
        return view
    }

    override fun onResume() {
        super.onResume()
        viewModel!!.track(Analytics.Navigation.BROWSER_HISTORY)
    }

    override fun onDetach() {
        super.onDetach()
        adapter!!.clear()
    }

    private fun showOrHideViews() {
        if (adapter!!.getItemCount() > 0) {
            clear!!.setVisibility(View.VISIBLE)
            noHistory!!.setVisibility(View.GONE)
        } else {
            clear!!.setVisibility(View.GONE)
            noHistory!!.setVisibility(View.VISIBLE)
        }
    }

    private fun clearHistory() {
        clearHistory(getContext())
        adapter!!.setDapps(this.data)
        showOrHideViews()
    }

    private fun onHistoryItemRemoved(dapp: DApp) {
        removeFromHistory(getContext(), dapp.url)
        adapter!!.setDapps(this.data)
        showOrHideViews()
        setFragmentResult(DappBrowserFragment.DAPP_REMOVE_HISTORY, dapp)
    }

    private fun setFragmentResult(key: String?, dapp: DApp?) {
        val result = Bundle()
        result.putParcelable(key, dapp)
        getParentFragmentManager().setFragmentResult(DappBrowserFragment.DAPP_CLICK, result)
    }

    private val data: MutableList<DApp>
        get() = getBrowserHistory(context)
}
