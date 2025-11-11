package com.alphawallet.app.widget

import android.app.Activity
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.lifi.Token
import com.alphawallet.app.ui.widget.adapter.SelectTokenAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class SelectTokenDialog(activity: Activity) : BottomSheetDialog(activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val tokenList: RecyclerView
    private var adapter: SelectTokenAdapter? = null
    private val searchLayout: LinearLayout
    private val search: EditText
    private val noResultsText: TextView

    init {
        val view = View.inflate(getContext(), R.layout.dialog_select_token, null)
        setContentView(view)

        setOnShowListener(OnShowListener { dialogInterface: DialogInterface? ->
            view.setMinimumHeight(Resources.getSystem().getDisplayMetrics().heightPixels)
            val behavior =
                BottomSheetBehavior.from<View?>((view.getParent() as android.view.View?)!!)
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            behavior.setSkipCollapsed(true)
        })

        tokenList = view.findViewById<RecyclerView>(R.id.token_list)
        search = view.findViewById<EditText>(R.id.edit_search)
        searchLayout = view.findViewById<LinearLayout>(R.id.layout_search_tokens)
        noResultsText = view.findViewById<TextView>(R.id.no_results)
        val btnClose = view.findViewById<ImageView>(R.id.image_close)
        btnClose.setOnClickListener(View.OnClickListener { v: View? -> dismiss() })
    }

    constructor(
        tokenItems: MutableList<Token?>,
        activity: Activity,
        callback: SelectTokenDialogEventListener?
    ) : this(activity) {
        noResultsText.setVisibility(if (tokenItems.size > 0) View.GONE else View.VISIBLE)

        adapter = SelectTokenAdapter(tokenItems, callback)

        tokenList.setLayoutManager(LinearLayoutManager(getContext()))
        tokenList.setAdapter(adapter)

        searchLayout.setOnClickListener(View.OnClickListener { v: View? -> })

        search.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun afterTextChanged(searchFilter: Editable) {
                handler.postDelayed(Runnable {
                    if (adapter != null) {
                        adapter!!.filter(searchFilter.toString())
                    }
                }, 200)
            }
        })
    }

    fun setSelectedToken(address: String?) {
        adapter!!.setSelectedToken(address)
    }

    interface SelectTokenDialogEventListener {
        fun onChainSelected(tokenItem: Token?)
    }
}
