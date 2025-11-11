package com.alphawallet.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.GitHubRelease
import com.alphawallet.app.widget.WhatsNewView.WhatsNewAdapter.WhatsNewItemViewHolder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class WhatsNewView(
    context: Context,
    items: MutableList<GitHubRelease>,
    onCloseListener: OnClickListener?
) : ConstraintLayout(context) {
    private val recyclerView: RecyclerView
    private var limitToLatest = false

    init {
        init(R.layout.layout_dialog_whats_new)

        findViewById<View?>(R.id.close_action).setOnClickListener(onCloseListener)

        recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.setAdapter(WhatsNewAdapter(items))
        recyclerView.setLayoutManager(LinearLayoutManager(context))
    }

    constructor(
        context: Context,
        items: MutableList<GitHubRelease>,
        onCloseListener: OnClickListener?,
        limitToLatest: Boolean
    ) : this(context, items, onCloseListener) {
        this.limitToLatest = limitToLatest
    }

    private fun init(@LayoutRes layoutId: Int) {
        LayoutInflater.from(getContext()).inflate(layoutId, this, true)
    }

    inner class WhatsNewAdapter(items: MutableList<GitHubRelease>) :
        RecyclerView.Adapter<WhatsNewItemViewHolder?>() {
        val formatterFrom: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.ROOT)
        val formatterTo: SimpleDateFormat = SimpleDateFormat("dd.MM.yy", Locale.ROOT)

        private val items: MutableList<GitHubRelease>

        init {
            this.items = items
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WhatsNewItemViewHolder {
            val itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_whats_new, parent, false)

            return WhatsNewItemViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: WhatsNewItemViewHolder, position: Int) {
            val release: GitHubRelease = items.get(position)
            try {
                val createdAt = formatterFrom.parse(release.getCreatedAt())
                holder.date.setText(formatterTo.format(createdAt))
            } catch (e: ParseException) {
                e.printStackTrace()
            }

            val body: Array<String> = release.getBody().split("\r\n- ")
            holder.details.removeAllViews()
            var index = 0
            for (entry in body) {
                val tv = TextView(getContext(), null, R.attr.whatsNewEntryStyle)
                tv.setText(entry.trim { it <= ' ' })
                if (index++ == 0) {
                    val first = tv.getText().toString()
                    if (first.startsWith("- ")) {
                        tv.setText(first.substring(2).trim { it <= ' ' })
                    }
                }
                holder.details.addView(tv)
            }
        }

        override fun getItemCount(): Int {
            if (limitToLatest) {
                return 1
            } else {
                return items.size
            }
        }

        internal inner class WhatsNewItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var date: TextView
            var details: LinearLayout

            init {
                date = view.findViewById<TextView>(R.id.date)
                details = view.findViewById<LinearLayout>(R.id.details)
            }
        }
    }
}
