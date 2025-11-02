package com.alphawallet.app.entity.walletconnect

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import com.walletconnect.web3.wallet.client.Wallet

/**
 * WalletConnect V2 会话条目。
 *
 * 负责在 UI 与业务层之间封装 WalletConnect V2 会话的关键信息，
 * 包括链、钱包地址、方法、事件等元数据，并提供从会话/会话提案构建自身的能力。
 */
class WalletConnectV2SessionItem() : WalletConnectSessionItem(), Parcelable {

    /** 会话是否已完成握手 */
    var settled: Boolean = false

    /** 会话支持的链 ID 列表 */
    val chains: MutableList<String> = mutableListOf()

    /** 会话中允许使用的钱包地址列表 */
    val wallets: MutableList<String> = mutableListOf()

    /** 会话声明的可调用方法列表 */
    val methods: MutableList<String> = mutableListOf()

    /** 会话订阅的事件列表 */
    val events: MutableList<String> = mutableListOf()

    /**
     * 通过 WalletConnect 已建立的会话构造条目。
     *
     * @param session WalletConnect V2 的已定版会话对象
     */
    constructor(session: Wallet.Model.Session) : this() {
        val metadata = session.metaData
        name = metadata?.name?.takeUnless { TextUtils.isEmpty(it) } ?: ""
        url = metadata?.url?.takeUnless { TextUtils.isEmpty(it) } ?: ""
        icon = metadata?.icons?.firstOrNull()
        sessionId = session.topic
        localSessionId = session.topic
        settled = true
        NamespaceParser().apply {
            parseSession(session.namespaces)
            chains.addAll(getChains())
            wallets.addAll(getWallets())
            methods.addAll(getMethods())
            events.addAll(getEvents())
        }
        wcVersion = 2
        expiryTime = convertEpochTime(session.expiry)
    }

    /**
     * 通过序列化数据恢复条目。
     *
     * @param parcel 存储条目字段的序列化容器
     */
    constructor(parcel: Parcel) : this() {
        name = parcel.readString().orEmpty()
        url = parcel.readString().orEmpty()
        icon = parcel.readString()
        sessionId = parcel.readString()
        localSessionId = parcel.readString()
        settled = parcel.readInt() == 1
        parcel.createStringArrayList()?.let { chains.addAll(it) }
        parcel.createStringArrayList()?.let { wallets.addAll(it) }
        parcel.createStringArrayList()?.let { methods.addAll(it) }
        parcel.createStringArrayList()?.let { events.addAll(it) }
        expiryTime = parcel.readLong()
    }

    /**
     * 将提案转换为会话条目。
     *
     * @param sessionProposal WalletConnect V2 会话提案
     */
    constructor(sessionProposal: Wallet.Model.SessionProposal) : this() {
        name = sessionProposal.name
        url = sessionProposal.url
        icon = sessionProposal.icons.firstOrNull()?.toString()
        sessionId = sessionProposal.proposerPublicKey
        settled = false
        NamespaceParser().apply {
            parseProposal(sessionProposal.requiredNamespaces)
            chains.addAll(getChains())
            methods.addAll(getMethods())
            events.addAll(getEvents())
        }
    }

    /**
     * Parcelable 序列化描述。
     */
    override fun describeContents(): Int = 0

    /**
     * 将实体写入 Parcel。
     *
     * @param dest 目标 Parcel
     * @param flags 写入标记
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(url)
        dest.writeString(icon)
        dest.writeString(sessionId)
        dest.writeString(localSessionId)
        dest.writeInt(if (settled) 1 else 0)
        dest.writeStringList(chains)
        dest.writeStringList(wallets)
        dest.writeStringList(methods)
        dest.writeStringList(events)
        dest.writeLong(expiryTime)
    }

    companion object {

        /**
         * Parcelable 所需的 CREATOR 实例。
         */
        @JvmField
        val CREATOR: Parcelable.Creator<WalletConnectV2SessionItem> =
            object : Parcelable.Creator<WalletConnectV2SessionItem> {
                override fun createFromParcel(parcel: Parcel): WalletConnectV2SessionItem =
                    WalletConnectV2SessionItem(parcel)

                override fun newArray(size: Int): Array<WalletConnectV2SessionItem?> =
                    arrayOfNulls(size)
            }

        /**
         * 将会话提案转换为条目实例。
         */
        @JvmStatic
        fun from(sessionProposal: Wallet.Model.SessionProposal): WalletConnectV2SessionItem =
            WalletConnectV2SessionItem(sessionProposal)
    }
}
