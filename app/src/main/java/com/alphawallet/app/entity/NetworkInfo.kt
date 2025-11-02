package com.alphawallet.app.entity

import android.net.Uri
import android.text.TextUtils
import com.alphawallet.app.entity.transactionAPI.TransferFetchType
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.util.Utils
import com.alphawallet.ethereum.NetworkInfo

class NetworkInfo : NetworkInfo {
    private val ETHERSCAN_API = ".etherscan."
    private val BLOCKSCOUT_API = "blockscout"
    private val MATIC_API = "polygonscan"
    private val OKX_API = "oklink"
    private val ARBISCAN_API = "https://api.arbiscan"
    private val PALM_API = "explorer.palm"

    @JvmField
    var etherscanAPI: String? = null //This is used by the API call to fetch transactions
    @JvmField
    var rpcUrls: Array<String?>

    constructor(
        name: String?,
        symbol: String?,
        rpcServerUrl: Array<String?>,
        etherscanUrl: String?,
        chainId: Long,
        etherscanAPI: String?,
        isCustom: Boolean
    ) : super(
        name, symbol,
        rpcServerUrl[0], etherscanUrl, chainId, isCustom
    ) {
        this.etherscanAPI = etherscanAPI
        this.rpcUrls = rpcServerUrl
    }

    constructor(
        name: String?,
        symbol: String?,
        rpcServerUrl: Array<String?>,
        etherscanUrl: String?,
        chainId: Long,
        etherscanAPI: String?
    ) : super(
        name, symbol,
        rpcServerUrl[0], etherscanUrl, chainId, false
    ) {
        this.etherscanAPI = etherscanAPI
        this.rpcUrls = rpcServerUrl
    }

    val shortName: String
        get() {
            val index = name.indexOf(" (Test)")
            return if (index > 0) name.substring(0, index)
            else if (name.length > 10) symbol
            else name
        }

    val transferQueriesUsed: Array<TransferFetchType?>
        get() {
            return if (etherscanAPI!!.contains(EthereumNetworkBase.COVALENT) || TextUtils.isEmpty(
                    etherscanAPI
                )
            ) {
                arrayOfNulls(0)
            } else if (chainId == com.alphawallet.ethereum.EthereumNetworkBase.GOERLI_ID || etherscanAPI!!.startsWith(
                    ARBISCAN_API
                )
            ) {
                arrayOf(
                    TransferFetchType.ERC_20,
                    TransferFetchType.ERC_721
                )
            } else if (etherscanAPI!!.contains(MATIC_API) || etherscanAPI!!.contains(ETHERSCAN_API) || etherscanAPI!!.contains(
                    OKX_API
                ) || etherscanAPI!!.contains("basescan.org")
            ) {
                arrayOf(
                    TransferFetchType.ERC_20,
                    TransferFetchType.ERC_721,
                    TransferFetchType.ERC_1155
                )
            } else if (etherscanAPI!!.contains(BLOCKSCOUT_API)) {
                arrayOf(TransferFetchType.ERC_20) // assume it only supports tokenTx, eg Blockscout, Palm
            } else  //play it safe, assume other API has ERC20
            {
                arrayOf(
                    TransferFetchType.ERC_20,
                    TransferFetchType.ERC_721
                )
            }
        }

    fun getEtherscanUri(transactionHash: String?): Uri? {
        return if (etherscanUrl != null) {
            Uri.parse(etherscanUrl)
                .buildUpon()
                .appendEncodedPath(transactionHash)
                .build()
        } else {
            Uri.EMPTY
        }
    }

    fun getEtherscanAddressUri(value: String?): Uri {
        if (etherscanUrl != null) {
            var explorer = etherscanUrl
            if (Utils.isAddressValid(value)) {
                explorer = explorer.substring(0, explorer.lastIndexOf("tx/"))
                explorer += "address/"
            } else if (!Utils.isTransactionHash(value)) {
                return Uri.EMPTY
            }

            return Uri.parse(explorer)
                .buildUpon()
                .appendEncodedPath(value)
                .build()
        } else {
            return Uri.EMPTY
        }
    }

    fun hasRealValue(): Boolean {
        return EthereumNetworkRepository.hasRealValue(this.chainId)
    }
}
