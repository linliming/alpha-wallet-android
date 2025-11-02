package com.alphawallet.app.repository.entity

import android.content.Context
import android.text.TextUtils
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.TokensRealmSource.Companion.EVENT_CARDS
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.app.ui.widget.entity.StatusType
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import java.math.BigInteger
import java.util.regex.Pattern

open class RealmAuxData : RealmObject() {

    @PrimaryKey
    var instanceKey: String? = null
    var chainId: Long = 0L
    var tokenAddress: String? = null
    var tokenId: String? = null
    var functionId: String? = null
    var result: String? = null
    var resultTime: Long = 0L
    var resultReceivedTime: Long = 0L

    fun getInstanceKey(): String? = instanceKey

    fun getChainId(): Long = chainId

    fun setChainId(chainId: Long) {
        this.chainId = chainId
    }

    fun getTransactionHash(): String {
        val key = instanceKey ?: return ""
        val split = key.split("-")
        return split.firstOrNull().orEmpty()
    }

    fun getEventName(): String {
        val key = instanceKey ?: return ""
        val split = key.split("-")
        return if (split.size > 1) split[1] else ""
    }

    fun getExtendId(): Long {
        val key = instanceKey ?: return 0L
        val split = key.split("-")
        if (split.size > 3) {
            val extendId = split[3]
            if (!extendId.isNullOrEmpty() && extendId[0].isDigit()) {
                return extendId.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    fun getTokenId(): BigInteger {
        val idValue = tokenId ?: return BigInteger.ZERO
        return try {
            BigInteger(idValue, Character.MAX_RADIX)
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    fun setTokenId(tokenId: String?) {
        this.tokenId = tokenId
    }

    fun getFunctionId(): String? = functionId

    fun setFunctionId(functionId: String?) {
        this.functionId = functionId
    }

    fun getResult(): String? = result

    fun setResult(result: String?) {
        this.result = result
    }

    fun getResultTime(): Long = resultTime

    fun setResultTime(resultTime: Long) {
        this.resultTime = resultTime
    }

    fun getAddress(): String {
        val key = instanceKey ?: return ""
        val split = key.split("-")
        return split.firstOrNull().orEmpty()
    }

    fun getTokenAddress(): String? = tokenAddress

    fun setTokenAddress(address: String?) {
        tokenAddress = address
    }

    fun setResultReceivedTime(resultReceivedTime: Long) {
        this.resultReceivedTime = resultReceivedTime
    }

    fun getResultReceivedTime(): Long = resultReceivedTime

    fun getEventStatusType(): StatusType {
        return when (getFunctionId()) {
            "sent" -> StatusType.SENT
            "received", "mint" -> StatusType.RECEIVE
            "burn" -> StatusType.SENT
            "ownerApproved", "approvalObtained" -> StatusType.NONE
            else -> StatusType.NONE
        }
    }

    fun getDetailAddress(): String {
        val resultMap = getEventResultMap()
        return when (getFunctionId()) {
            "sent" -> resultMap["to"]?.value
            "received" -> resultMap["from"]?.value
            "ownerApproved" -> resultMap["spender"]?.value
            "approvalObtained" -> resultMap["owner"]?.value
            else -> null
        } ?: getEventName()
    }

    fun getDetail(ctx: Context, tx: Transaction?, itemView: String?): String? {
        val resultMap = getEventResultMap()
        tx?.addTransactionElements(resultMap)

        if (!itemView.isNullOrEmpty()) {
            var eventDesc = itemView
            val matcher = Pattern.compile("\\$\\{([^}]+)\\}").matcher(itemView)
            while (matcher.find()) {
                val match = matcher.group(1)
                val replacement = resultMap[match]?.value
                if (replacement != null) {
                    val tag = matcher.group(0)
                    val index = eventDesc?.indexOf(tag)
                    if (index != null) {
                        if (index >= 0) {
                            eventDesc = eventDesc?.substring(0, index) + replacement + eventDesc?.substring(index + tag.length)
                        }
                    }
                }
            }
            return eventDesc
        }

        val fallback = listOfNotNull(tokenId, result).joinToString(" ").trim()

        return when (getFunctionId()) {
            "sent" -> resultMap["to"]?.value?.let { ctx.getString(R.string.sent_to, ENSHandler.displayAddressOrENS(ctx, it)) }
            "received" -> resultMap["from"]?.value?.let { ctx.getString(R.string.from, ENSHandler.displayAddressOrENS(ctx, it)) }
            "ownerApproved" -> resultMap["spender"]?.value?.let { ctx.getString(R.string.approval_granted_to, ENSHandler.displayAddressOrENS(ctx, it)) }
            "approvalObtained" -> resultMap["owner"]?.value?.let { ctx.getString(R.string.approval_obtained_from, ENSHandler.displayAddressOrENS(ctx, it)) }
            else -> getEventName()
        } ?: if (fallback.isNotEmpty()) fallback else getEventName()
    }

    fun getTitle(ctx: Context): String {
        return when (getFunctionId()) {
            "sent" -> ctx.getString(R.string.activity_sent)
            "received" -> ctx.getString(R.string.activity_received)
            "ownerApproved" -> ctx.getString(R.string.activity_approved)
            "approvalObtained" -> ctx.getString(R.string.activity_approval_granted)
            else -> getFunctionId().orEmpty()
        }
    }

    private enum class ResultState {
        NAME,
        TYPE,
        RESULT
    }

    fun getEventResultMap(): MutableMap<String, EventResult> {
        val raw = result ?: return mutableMapOf()
        val parts = raw.split(",")
        val resultMap = mutableMapOf<String, EventResult>()
        var state = ResultState.NAME
        var name: String? = null
        var type: String? = null
        for (part in parts) {
            when (state) {
                ResultState.NAME -> {
                    name = part
                    state = ResultState.TYPE
                }
                ResultState.TYPE -> {
                    type = part
                    state = ResultState.RESULT
                }
                ResultState.RESULT -> {
                    if (name != null && type != null) {
                        resultMap[name!!] = EventResult(type!!, part)
                    }
                    name = null
                    type = null
                    state = ResultState.NAME
                }
            }
        }
        return resultMap
    }

    fun setBaseChainBlock(blockRead: Long) {
        this.functionId = blockRead.toString()
    }

    fun getBaseChainBlock(): Long {
        return try {
            if (TextUtils.isEmpty(functionId)) 0L else functionId?.toLong() ?: 0L
        } catch (_: NumberFormatException) {
            0L
        }
    }

    companion object {
        @JvmStatic
        fun getEventListener(
            realm: Realm,
            token: Token,
            tokenId: BigInteger,
            historyCount: Int,
            timeLimit: Long
        ): RealmResults<RealmAuxData> {
            return getEventQuery(realm, token, tokenId, historyCount, timeLimit).findAllAsync()
        }

        @JvmStatic
        fun getEventQuery(
            realm: Realm,
            token: Token,
            tokenId: BigInteger,
            historyCount: Int,
            timeLimit: Long
        ): RealmQuery<RealmAuxData> {
            val tokenIdHex = tokenId.toString(16)
            return realm.where(RealmAuxData::class.java)
                .endsWith("instanceKey", EVENT_CARDS)
                .sort("resultTime", Sort.DESCENDING)
                .greaterThan("resultTime", timeLimit)
                .equalTo("chainId", token.tokenInfo.chainId)
                .beginGroup()
                .equalTo("tokenId", "0")
                .or()
                .equalTo("tokenId", tokenIdHex)
                .endGroup()
                .equalTo("tokenAddress", token.getAddress())
                .limit(historyCount.toLong())
        }
    }
}
