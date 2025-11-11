package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.ui.widget.entity.TokenTransferData
import com.alphawallet.app.ui.widget.holder.TransactionHolder.TRANSACTION_BALANCE_PRECISION

/**
 * 默认事件详情组件，用于展示交易或事件的摘要资料。
 */
class EventDetailWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val title: TextView
    private val symbol: TextView
    private val detail: TextView
    private val holdingView: LinearLayout
    private val tokenView: AssetDetailView
    private val icon: TokenIcon

    init {
        inflate(context, R.layout.item_default_event, this)
        title = findViewById(R.id.text_title)
        detail = findViewById(R.id.text_detail)
        symbol = findViewById(R.id.text_title_symbol)
        holdingView = findViewById(R.id.layout_default_event)
        tokenView = findViewById(R.id.asset_detail)
        icon = findViewById(R.id.token_icon)
        if (isInEditMode) holdingView.visibility = View.VISIBLE
    }

    /**
     * 用于绑定链下事件（例如 TokenScript 事件）信息。
     */
    fun setupView(
        data: RealmAuxData,
        token: Token,
        @Suppress("UNUSED_PARAMETER") assetDefinitionService: AssetDefinitionService,
        eventAmount: String
    ) {
        holdingView.visibility = View.VISIBLE
        icon.bindData(token)
        title.text = data.getTitle(context)
        symbol.text = token.getSymbol()
        val resourceId = when (data.functionId) {
            "sent" -> R.string.default_to
            "received" -> R.string.default_from
            "approvalObtained" -> R.string.default_approved
            "ownerApproved" -> {
                detail.text = context.getString(R.string.default_approve, eventAmount, data.getDetailAddress())
                return
            }
            else -> R.string.default_to
        }
        detail.text = context.getString(resourceId, eventAmount, token.getSymbol(), data.getDetailAddress())
    }

    /**
     * 用于绑定普通链上交易事件。
     */
    fun setupTransactionView(
        transaction: Transaction,
        token: Token,
        @Suppress("UNUSED_PARAMETER") assetDefinitionService: AssetDefinitionService,
        supplementalInfo: String
    ) {
        holdingView.visibility = View.VISIBLE
        icon.bindData(token)
        symbol.text = token.getSymbol()
        title.visibility = View.GONE

        val info = if (supplementalInfo.length > 2) supplementalInfo.substring(2) else ""
        val formatted = if (supplementalInfo.startsWith("-")) {
            context.getString(R.string.default_to, info, "", token.getFullName())
        } else {
            context.getString(R.string.default_from, info, "", token.getFullName())
        }
        detail.text = formatted
    }

    /**
     * 绑定 Token 转账事件，并根据事件名调整文案。
     */
    fun setupTransferData(
        transaction: Transaction,
        token: Token,
        transferData: TokenTransferData
    ) {
        holdingView.visibility = View.VISIBLE
        icon.visibility = View.GONE
        symbol.text = token.getShortSymbol()
        title.visibility = View.GONE

        transaction.getDestination(token)
        val resultMap: Map<String, EventResult> = transferData.eventResultMap
        val amountResult = resultMap["amount"]
        setupERC721TokenView(token, amountResult?.value ?: "", minimise = false)

        val value = amountResult?.let {
            val prefix = if (transferData.eventName == "sent") "- " else "+ "
            token.convertValue(prefix, it, if (token.isNonFungible()) 128 else TRANSACTION_BALANCE_PRECISION + 2)
        } ?: ""

        val addressDetail = transferData.getDetail(context, transaction, "", token, false)
        detail.text = when (transferData.eventName) {
            "received" -> context.getString(R.string.default_from, value, "", addressDetail)
            "sent" -> context.getString(R.string.default_to, value, "", addressDetail)
            else -> detail.text
        }
    }

    /**
     * 针对 ERC-721 资产展示资产详情组件。
     */
    fun setupERC721TokenView(token: Token, tokenId: String?, minimise: Boolean) {
        if (token.isERC721()) {
            tokenView.setupAssetDetail(token, tokenId.orEmpty(), null)
            tokenView.visibility = View.VISIBLE
            tokenView.setFullyExpanded()
        } else {
            tokenView.visibility = View.GONE
        }

        if (minimise) {
            findViewById<View>(R.id.detail_layer).visibility = View.GONE
            holdingView.visibility = View.VISIBLE
            icon.visibility = View.GONE
            title.visibility = View.GONE
            symbol.visibility = View.GONE
        }
    }

    /**
     * 清理内部 AssetDetailView 的资源。
     */
    fun onDestroy() {
        tokenView.onDestroy()
    }
}
