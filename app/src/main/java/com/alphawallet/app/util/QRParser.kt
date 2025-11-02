package com.alphawallet.app.util

import com.alphawallet.app.entity.EIP681Type
import com.alphawallet.app.entity.EthereumProtocolParser
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.token.entity.ChainSpec
import com.alphawallet.token.entity.MagicLinkInfo
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger
import java.net.URL
import java.util.regex.Pattern

/**
 * QR 解析工具，负责识别不同类型的钱包协议与支付信息。
 */
class QRParser private constructor() {

    /**
     * 解析二维码字符串，返回 [QRResult]。
     */
    fun parse(rawUrl: String?): QRResult? {
        if (rawUrl.isNullOrEmpty()) return null

        // 优先识别 EAS 魔法链接
        if (Utils.hasEASAttestation(rawUrl)) {
            val result = QRResult(rawUrl)
            result.type = EIP681Type.EAS_ATTESTATION
            val payload = Utils.parseEASAttestation(rawUrl)
            result.functionDetail = Utils.toAttestationJson(payload)
            return result
        }

        if (rawUrl.startsWith(WALLET_CONNECT_PREFIX)) {
            return QRResult(rawUrl, EIP681Type.WALLET_CONNECT)
        }

        if (checkForMagicLink(rawUrl)) {
            return QRResult(rawUrl, EIP681Type.MAGIC_LINK)
        }

        var result: QRResult? = null
        val parts = rawUrl.split(":")
        when (parts.size) {
            1 -> {
                val address = extractAddress(parts[0])
                if (address != null) {
                    result = QRResult(address)
                }
            }
            2 -> {
                val parser = EthereumProtocolParser()
                result = parser.readProtocol(parts[0], parts[1])
            }
        }

        if (result == null) {
            // 尝试剥离地址或 URL
            val address = extractAddress(rawUrl)
            result =
                if (address != null) {
                    QRResult(address)
                } else {
                    runCatching { URL(rawUrl) }
                        .map { QRResult(rawUrl, EIP681Type.URL) }
                        .getOrElse { QRResult(rawUrl, EIP681Type.OTHER) }
                }
        } else if (result.type == EIP681Type.OTHER && validAddress(result.getAddress())) {
            result.type = EIP681Type.OTHER_PROTOCOL
        }

        if (result.type == EIP681Type.OTHER) {
            result = decodeAttestation(rawUrl)
        }

        return result
    }

    /**
     * 仅提取二维码中的地址字符串。
     */
    fun extractAddressFromQrString(url: String): String? {
        val result = parse(url)
        return if (result == null || result.type == EIP681Type.OTHER) {
            null
        } else {
            result.getAddress()
        }
    }

    private fun decodeAttestation(url: String): QRResult {
        var result = QRResult(url, EIP681Type.OTHER)
        try {
            BigInteger(url, 10) // 仅用于校验是否完全由数字组成
            val chainLength = url.substring(0, 1).toInt()
            var index = chainLength + 1
            if (url.length <= index + 49 + 2) {
                return result
            }

            val chainId = url.substring(1, index).toLong()
            val addrBI = BigInteger(url.substring(index, index + 49))
            index += 49
            val address = Numeric.toHexStringWithPrefixZeroPadded(addrBI, 40)
            val attestationNumeric = BigInteger(url.substring(index))
            val attestationHex = attestationNumeric.toString(16)

            if (attestationHex.startsWith("30")) {
                result = QRResult(attestationHex, chainId, address)
            }
        } catch (_: Exception) {
            // 非 attestation 格式，忽略
        }
        return result
    }

    private fun checkForMagicLink(data: String): Boolean =
        try {
            MagicLinkInfo.identifyChainId(data) > 0
        } catch (e: Exception) {
            Timber.e(e)
            false
        }

    private fun extractAddress(str: String?): String? {
        if (str.isNullOrEmpty()) return null
        return try {
            if (Utils.isAddressValid(str)) {
                str
            } else {
                val candidate = str.split("[/&@?=]".toRegex()).firstOrNull()
                when {
                    Utils.isAddressValid(candidate) -> candidate
                    ENSHandler.couldBeENS(candidate) -> candidate
                    else -> if (ENSHandler.canBeENSName(candidate)) candidate else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val WALLET_CONNECT_PREFIX = "wc:"
        private val findAddress = Pattern.compile("(0x)([0-9a-fA-F]{40})($|\\s)")
        private val extraChains: MutableList<ChainSpec> = ArrayList()
        @Volatile
        private var instance: QRParser? = null

        /**
         * 返回单例解析器，并刷新额外链表。
         */
        @JvmStatic
        fun getInstance(chains: List<ChainSpec>?): QRParser {
            if (instance == null) {
                synchronized(QRParser::class.java) {
                    if (instance == null) {
                        instance = QRParser()
                    }
                }
            }
            extraChains.clear()
            if (chains != null) {
                extraChains.addAll(chains)
            }
            return instance!!
        }

        /**
         * 校验地址或 ENS 格式是否有效。
         */
        @JvmStatic
        fun validAddress(address: String?): Boolean =
            !address.isNullOrEmpty() &&
                ((address.startsWith("0x") && address.length > 10) ||
                    (address.contains(".") && address.indexOf('.') <= address.length - 2))
    }
}
