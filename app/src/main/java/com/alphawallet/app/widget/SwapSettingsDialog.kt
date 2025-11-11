package com.alphawallet.app.widget

import android.app.Activity
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.res.Resources
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.lifi.Chain
import com.alphawallet.app.entity.lifi.SwapProvider
import com.alphawallet.app.ui.SelectSwapProvidersActivity
import com.alphawallet.app.ui.widget.adapter.ChainFilter
import com.alphawallet.app.ui.widget.adapter.SelectChainAdapter
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class SwapSettingsDialog(activity: Activity) : BottomSheetDialog(activity) {
    private val chainList: RecyclerView
    private var adapter: SelectChainAdapter? = null
    private var swapProviders: MutableList<SwapProvider>? = null
    private val slippageWidget: SlippageWidget
    private val preferredExchangesHeader: StandardHeader?
    private val preferredSwapProviders: FlexboxLayout?

    init {
        val view = View.inflate(getContext(), R.layout.dialog_swap_settings, null)
        setContentView(view)

        setOnShowListener(OnShowListener { dialogInterface: DialogInterface? ->
            view.setMinimumHeight(Resources.getSystem().getDisplayMetrics().heightPixels)
            val behavior =
                BottomSheetBehavior.from<View?>((view.getParent() as android.view.View?)!!)
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            behavior.setSkipCollapsed(true)
        })

        chainList = view.findViewById<RecyclerView>(R.id.chain_list)
        slippageWidget = view.findViewById<SlippageWidget>(R.id.slippage_widget)

        val closeBtn = findViewById<ImageView?>(R.id.image_close)
        closeBtn!!.setOnClickListener(View.OnClickListener { v: View? -> dismiss() })

        preferredExchangesHeader = findViewById<StandardHeader?>(R.id.header_exchanges)
        preferredExchangesHeader!!.getTextControl()
            .setOnClickListener(View.OnClickListener { v: View? ->
                val intent = Intent(activity, SelectSwapProvidersActivity::class.java)
                activity.startActivity(intent)
            })

        preferredSwapProviders = findViewById<FlexboxLayout?>(R.id.layout_exchanges)
    }

    constructor(
        activity: Activity,
        chains: MutableList<Chain?>?,
        swapProviders: MutableList<SwapProvider>,
        preferredSwapProviders: MutableSet<String?>,
        swapSettingsInterface: SwapSettingsInterface?
    ) : this(activity) {
        val filter = ChainFilter(chains)
        adapter = SelectChainAdapter(activity, filter.getSupportedChains(), swapSettingsInterface)
        chainList.setLayoutManager(LinearLayoutManager(getContext()))
        chainList.setAdapter(adapter)
        this.swapProviders = swapProviders
        setSwapProviders(preferredSwapProviders)
    }

    private fun createTextView(name: String?): TextView {
        val margin = getContext().getResources().getDimension(R.dimen.tiny_8).toInt()
        val params =
            FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
        params.setMargins(margin, margin, margin, margin)

        val exchange = TextView(getContext(), null)
        exchange.setText(name)
        exchange.setLayoutParams(params)
        return exchange
    }

    fun setSwapProviders(swapProviders: MutableSet<String?>) {
        preferredSwapProviders!!.removeAllViews()
        for (provider in this.swapProviders!!) {
            if (swapProviders.contains(provider.key)) {
                preferredSwapProviders.addView(createTextView(provider.name))
            }
        }
        preferredSwapProviders.invalidate()
    }

    fun setChains(chains: MutableList<Chain?>?) {
        adapter!!.setChains(chains)
    }

    fun setSelectedChain(selectedChainId: Long) {
        adapter!!.setSelectedChain(selectedChainId)
    }

    val selectedChainId: Long
        get() = adapter!!.getSelectedChain()

    val slippage: String
        get() = slippageWidget.slippage

    interface SwapSettingsInterface {
        fun onChainSelected(chain: Chain?)
    }
}
