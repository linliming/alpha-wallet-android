package com.alphawallet.app.ui.widget.entity

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.ui.widget.holder.TransactionHolder.TRANSACTION_BALANCE_PRECISION
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Represents a token transfer item that can be displayed alongside transactions.
 */
class TokenTransferData(
    hash: String,
    val chainId: Long,
    val tokenAddress: String?,
    val eventName: String?,
    val transferDetail: String?,
    transferTime: Long
) : ActivityMeta(transferTime, hash, true), Parcelable {

    /**
     * Returns the title string resource that best describes the transfer event.
     */
    fun getTitle(): Int {
        return when (eventName) {
            "sent" -> {
                val to = detailAddress
                if (!to.isNullOrEmpty() && to.equals(ZERO_ADDRESS, true)) {
                    R.string.token_burn
                } else {
                    R.string.activity_sent
                }
            }

            "received" -> {
                val from = detailAddress
                if (!from.isNullOrEmpty() && from.equals(ZERO_ADDRESS, true)) {
                    R.string.token_mint
                } else {
                    R.string.activity_received
                }
            }

            "ownerApproved" -> R.string.activity_approved
            "approvalObtained" -> R.string.activity_approval_granted
            else -> 0
        }
    }

    /**
     * Indicates whether this transfer represents a mint operation.
     */
    fun isMintEvent(): Boolean {
        return eventName == "received" && detailAddress.equals(ZERO_ADDRESS, true)
    }

    /**
     * Returns the operation prefix used when displaying amounts.
     */
    fun getOperationPrefix(): String {
        return when (eventName) {
            "sent" -> "-"
            "received" -> "+"
            else -> ""
        }
    }

    /**
     * Builds the contextual detail string for list rendering.
     */
    fun getDetail(
        ctx: Context,
        tx: Transaction?,
        itemView: String?,
        token: Token?,
        shrinkAddress: Boolean = true
    ): String? {
        val resultMap = eventResultMap
        tx?.addTransactionElements(resultMap)

        // 1) 如果有模板（itemView 非空），先按模板渲染并直接返回
        val template = itemView?.takeIf { it.isNotBlank() }
        if (template != null) {
            val m = Pattern.compile("\\$\\{([^}]+)}").matcher(template)
            val out = StringBuffer()
            while (m.find()) {
                val key = m.group(1)                       // 占位符里的 key
                val replacement = resultMap[key]?.value             // 替换值
                    ?: m.group(0)                          // 找不到就保留原占位符
                m.appendReplacement(out, Matcher.quoteReplacement(replacement))
            }
            m.appendTail(out)
            return out.toString()
        }

        // 2) 没有模板时，按事件名返回默认描述
        return when (eventName) {
            "sent" -> {
                val address = resultMap["to"]?.value ?: return eventName
                if (shrinkAddress) {
                    ctx.getString(R.string.sent_to, ENSHandler.displayAddressOrENS(ctx, address))
                } else {
                    ENSHandler.displayAddressOrENS(ctx, address, false)
                }
            }

            "received" -> {
                val from = resultMap["from"]?.value ?: return eventName
                if (from.equals(ZERO_ADDRESS, ignoreCase = true) && token != null && tx != null) {
                    token.getFullName()
                } else if (shrinkAddress) {
                    ctx.getString(R.string.from, ENSHandler.displayAddressOrENS(ctx, from))
                } else {
                    ENSHandler.displayAddressOrENS(ctx, from, false)
                }
            }

            "ownerApproved" -> {
                val spender = resultMap["spender"]?.value ?: return eventName
                ctx.getString(
                    R.string.approval_granted_to,
                    ENSHandler.displayAddressOrENS(ctx, spender, shrinkAddress)
                )
            }

            "approvalObtained" -> {
                val owner = resultMap["owner"]?.value ?: return eventName
                ctx.getString(
                    R.string.approval_obtained_from,
                    ENSHandler.displayAddressOrENS(ctx, owner, shrinkAddress)
                )
            }

            else -> eventName
        }
    }

    /**
     * Parses the embedded transfer detail string into a name-type-value map.
     */
    val eventResultMap: MutableMap<String, EventResult>
        get() {
            val split = transferDetail?.split(",")
            val resultMap = mutableMapOf<String, EventResult>()
            var state = ResultState.NAME
            var name: String? = null
            var type: String? = null
            split?.forEach { item ->
                when (state) {
                    ResultState.NAME -> {
                        name = item
                        state = ResultState.TYPE
                    }

                    ResultState.TYPE -> {
                        type = item
                        state = ResultState.RESULT
                    }

                    ResultState.RESULT -> {
                        if (name != null && type != null) {
                            resultMap[name!!] = EventResult(type!!, item)
                        }
                        name = null
                        type = null
                        state = ResultState.NAME
                    }
                }
            }
            return resultMap
        }

    /**
     * Returns the address associated with the transfer event.
     */
    val detailAddress: String?
        get() {
            val resultMap = eventResultMap
        val resolved = when (eventName) {
            "sent" -> resultMap["to"]?.value
            "received" -> resultMap["from"]?.value
            "ownerApproved" -> resultMap["spender"]?.value
            "approvalObtained" -> resultMap["owner"]?.value
            else -> null
        }
        return resolved ?: eventName
    }

    /**
     * Returns the `to` address when available.
     */
    fun getToAddress(): String? = eventResultMap["to"]?.value

    /**
     * Returns the `from` address when available.
     */
    fun getFromAddress(): String? = eventResultMap["from"]?.value

    /**
     * Provides the status type used to render event icons.
     */
    fun getEventStatusType(): StatusType {
        return when (eventName) {
            "sent" -> StatusType.SENT
            "received" -> StatusType.RECEIVE
            else -> StatusType.NONE
        }
    }

    /**
     * Formats the event amount, converting values via the supplied token and transaction.
     */
    fun getEventAmount(token: Token?, transaction: Transaction?): String {
        if (token == null) {
            return ""
        }
        transaction?.getDestination(token)
        val resultMap = eventResultMap
        return when (eventName) {
            "received" -> {
                val amount = resultMap["amount"] ?: return ""
                token.convertValue("+ ", amount, TRANSACTION_BALANCE_PRECISION)
            }

            "sent" -> {
                val amount = resultMap["amount"] ?: return ""
                token.convertValue("- ", amount, TRANSACTION_BALANCE_PRECISION)
            }

            "approvalObtained", "ownerApproved" -> {
                val value = resultMap["value"] ?: return ""
                token.convertValue("", value, TRANSACTION_BALANCE_PRECISION)
            }

            else -> {
        if (transaction == null) {
            return ""
        }
        return if (token.isEthereum()) {
            token.getTransactionValue(transaction, TRANSACTION_BALANCE_PRECISION)
        } else {
            transaction.getOperationResult(token, TRANSACTION_BALANCE_PRECISION)
        }
    }
        }
    }

    /**
     * Required Parcelable implementation detail; always zero.
     */
    override fun describeContents(): Int = 0

    /**
     * Writes parcelable state for the transfer.
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(timeStamp)
        dest.writeString(hash)
        dest.writeLong(chainId)
        dest.writeString(tokenAddress)
        dest.writeString(eventName)
        dest.writeString(transferDetail)
    }

    private enum class ResultState {
        NAME,
        TYPE,
        RESULT
    }

    companion object CREATOR : Parcelable.Creator<TokenTransferData> {
        override fun createFromParcel(parcel: Parcel): TokenTransferData {
            val timeStamp = parcel.readLong()
            val hash = parcel.readString().orEmpty()
            val chainId = parcel.readLong()
            val tokenAddress = parcel.readString()
            val eventName = parcel.readString().orEmpty()
            val transferDetail = parcel.readString().orEmpty()
            return TokenTransferData(hash, chainId, tokenAddress, eventName, transferDetail, timeStamp)
        }

        override fun newArray(size: Int): Array<TokenTransferData?> = arrayOfNulls(size)
    }
}
