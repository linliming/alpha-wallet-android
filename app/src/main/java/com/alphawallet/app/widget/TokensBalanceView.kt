package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.widget.adapter.TestNetHorizontalListAdapter

class TokensBalanceView(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    var horizontalListView: RecyclerView

    init {
        inflate(context, R.layout.item_token_with_balance_view, this)
        horizontalListView = findViewById<RecyclerView>(R.id.horizontal_list)
    }

    fun bindTokens(token: Array<Token?>?) {
        val testNetHorizontalListAdapter = TestNetHorizontalListAdapter(token, getContext())
        horizontalListView.setAdapter(testNetHorizontalListAdapter)
    }

    fun blankView() {
        //clear adapter
        bindTokens(arrayOfNulls<Token>(0))
    }
}

