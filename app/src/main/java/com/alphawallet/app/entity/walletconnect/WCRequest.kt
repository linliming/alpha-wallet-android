package com.alphawallet.app.entity.walletconnect

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.walletconnect.entity.WCEthereumSignMessage
import com.alphawallet.app.walletconnect.entity.WCEthereumTransaction
import com.alphawallet.app.walletconnect.entity.WCPeerMeta
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID

/**
 * WalletConnect 请求封装。
 *
 * 该实体负责封装一次 WalletConnect 交互的所有上下文信息（签名、交易、会话请求或异常），
 * 以便在 UI 与业务层之间传递，并支持通过 Parcelable 在组件间序列化传输。
 */
class WCRequest : Parcelable {

    /** 请求所属会话 ID */
    val sessionId: String?

    /** 请求 ID，来源于 WalletConnect 原始 payload */
    val id: Long

    /** 交易请求参数（当请求类型为交易相关时存在） */
    val tx: WCEthereumTransaction?

    /** 签名请求参数（当请求类型为消息签名时存在） */
    val sign: WCEthereumSignMessage?

    /** 对端信息（当为会话请求时存在） */
    val peer: WCPeerMeta?

    /** 请求类型枚举 */
    val type: SignType

    /** 请求目标链 ID */
    val chainId: Long

    /** 当请求失败时携带的异常信息 */
    val throwable: Throwable?

    /**
     * 构建消息签名请求。
     */
    constructor(sessionId: String?, id: Long, msg: WCEthereumSignMessage) {
        this.sessionId = sessionId
        this.id = id
        this.sign = msg
        this.type = SignType.MESSAGE
        this.tx = null
        this.peer = null
        this.chainId = MAINNET_ID
        this.throwable = null
    }

    /**
     * 构建交易签名或发送请求。
     *
     * @param signOnly true 为仅签名交易，false 为直接发送交易
     */
    constructor(
        sessionId: String?,
        id: Long,
        tx: WCEthereumTransaction,
        signOnly: Boolean,
        chainId: Long,
    ) {
        this.sessionId = sessionId
        this.id = id
        this.sign = null
        this.type = if (signOnly) SignType.SIGN_TX else SignType.SEND_TX
        this.tx = tx
        this.chainId = chainId
        this.peer = null
        this.throwable = null
    }

    /**
     * 构建会话请求消息。
     */
    constructor(sessionId: String?, id: Long, peerMeta: WCPeerMeta, chainId: Long) {
        this.sessionId = sessionId
        this.id = id
        this.sign = null
        this.chainId = chainId
        this.type = SignType.SESSION_REQUEST
        this.tx = null
        this.peer = peerMeta
        this.throwable = null
    }

    /**
     * 构建失败请求，通常用于异常处理。
     */
    constructor(sessionId: String?, throwable: Throwable, chainId: Long) {
        this.sessionId = sessionId
        this.id = 0
        this.sign = null
        this.chainId = chainId
        this.type = SignType.FAILURE
        this.tx = null
        this.peer = null
        this.throwable = throwable
    }

    /**
     * 通过序列化数据恢复请求实例。
     */
    private constructor(parcel: Parcel) {
        id = parcel.readLong()
        sessionId = parcel.readString()
        type = SignType.values()[parcel.readInt()]
        chainId = parcel.readLong()

        tx =
            if (parcel.readByte().toInt() == 1) {
                val from = parcel.readString().orEmpty()
                val to = parcel.readOptionalString()
                val nonce = parcel.readOptionalString()
                val gasPrice = parcel.readOptionalString()
                val maxFeePerGas = parcel.readOptionalString()
                val maxPriorityFeePerGas = parcel.readOptionalString()
                val gas = parcel.readOptionalString()
                val gasLimit = parcel.readOptionalString()
                val value = parcel.readOptionalString()
                val data = parcel.readOptionalString().orEmpty()

                WCEthereumTransaction(
                    from,
                    to,
                    nonce,
                    gasPrice,
                    maxFeePerGas,
                    maxPriorityFeePerGas,
                    gas,
                    gasLimit,
                    value,
                    data,
                )
            } else {
                null
            }

    sign =
            if (parcel.readByte().toInt() == 1) {
                val raw = mutableListOf<String>()
                parcel.readStringList(raw)
                val signType = WCEthereumSignMessage.WCSignType.values()[parcel.readInt()]
                WCEthereumSignMessage(raw, signType)
            } else {
                null
            }

        peer =
            if (parcel.readByte().toInt() == 1) {
                val name = parcel.readString().orEmpty()
                val url = parcel.readString().orEmpty()
                val description = parcel.readOptionalString()
                val icons = mutableListOf<String>()
                parcel.readStringList(icons)
                WCPeerMeta(name, url, description, icons)
            } else {
                null
            }

        throwable =
            if (parcel.readByte().toInt() == 1) {
                Throwable(parcel.readString())
            } else {
                null
            }
    }

    /**
     * Parcelable 所需描述，恒为 0。
     */
    override fun describeContents(): Int = 0

    /**
     * 将实体写入 Parcel，序列化所有必要字段。
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(sessionId)
        dest.writeInt(type.ordinal)
        dest.writeLong(chainId)

        if (tx != null) {
            dest.writeByte(1)
            dest.writeString(tx.from)
            dest.writeOptionalString(tx.to)
            dest.writeOptionalString(tx.nonce)
            dest.writeOptionalString(tx.gasPrice)
            dest.writeOptionalString(tx.maxFeePerGas)
            dest.writeOptionalString(tx.maxPriorityFeePerGas)
            dest.writeOptionalString(tx.gas)
            dest.writeOptionalString(tx.gasLimit)
            dest.writeOptionalString(tx.value)
            dest.writeOptionalString(tx.data)
        } else {
            dest.writeByte(0)
        }

        if (sign != null) {
            dest.writeByte(1)
            dest.writeStringList(sign.raw)
            dest.writeInt(sign.type.ordinal)
        } else {
            dest.writeByte(0)
        }

        if (peer != null) {
            dest.writeByte(1)
            dest.writeString(peer.name)
            dest.writeString(peer.url)
            dest.writeOptionalString(peer.description)
            dest.writeStringList(peer.icons)
        } else {
            dest.writeByte(0)
        }

        if (throwable != null) {
            dest.writeByte(1)
            dest.writeString(throwable.message)
        } else {
            dest.writeByte(0)
        }
    }

    companion object {
        /**
         * Parcelable 所需的 CREATOR。
         */
        @JvmField
        val CREATOR: Parcelable.Creator<WCRequest> =
            object : Parcelable.Creator<WCRequest> {
                override fun createFromParcel(parcel: Parcel): WCRequest = WCRequest(parcel)

                override fun newArray(size: Int): Array<WCRequest?> = arrayOfNulls(size)
            }
    }
}

/**
 * 读取可选字符串的扩展方法，先读取标识位再转化为结果。
 */
private fun Parcel.readOptionalString(): String? =
    if (readByte().toInt() == 1) readString() else null

/**
 * 写入可选字符串的扩展方法，先写入标识位，再写入具体字符串。
 */
private fun Parcel.writeOptionalString(value: String?) {
    writeByte(if (value != null) 1 else 0)
    if (value != null) writeString(value)
}
