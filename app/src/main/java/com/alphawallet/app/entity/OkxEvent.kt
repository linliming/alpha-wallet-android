package com.alphawallet.app.entity

import com.alphawallet.app.util.BalanceUtils
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class OkxEvent {
    @SerializedName("txId")
    @Expose
    var txId: String? = null

    @SerializedName("methodId")
    @Expose
    var methodId: String? = null

    @SerializedName("blockHash")
    @Expose
    var blockHash: String? = null

    @JvmField
    @SerializedName("height")
    @Expose
    var height: String? = null

    @SerializedName("transactionTime")
    @Expose
    var transactionTime: String? = null

    @SerializedName("from")
    @Expose
    var from: String? = null

    @SerializedName("to")
    @Expose
    var to: String? = null

    @SerializedName("isFromContract")
    @Expose
    var isFromContract: Boolean = false

    @SerializedName("isToContract")
    @Expose
    var isToContract: Boolean = false

    @SerializedName("amount")
    @Expose
    var amount: String? = null

    @SerializedName("transactionSymbol")
    @Expose
    var transactionSymbol: String? = null

    @SerializedName("txFee")
    @Expose
    var txFee: String? = null

    @SerializedName("state")
    @Expose
    var state: String? = null

    @SerializedName("tokenId")
    @Expose
    var tokenId: String? = null

    @SerializedName("tokenContractAddress")
    @Expose
    var tokenContractAddress: String? = null

    @SerializedName("challengeStatus")
    @Expose
    var challengeStatus: String? = null

    @SerializedName("l1OriginHash")
    @Expose
    var l1OriginHash: String? = null

    @Throws(Exception::class)
    fun getEtherscanTransferEvent(isNft: Boolean): EtherscanEvent {
//        final Web3j web3j = TokenRepository.getWeb3jService(OKX_ID);
//        final EthTransaction eTx = web3j.ethGetTransactionByHash(txId.trim()).send();

        val ev = EtherscanEvent()
        //ev.tokenDecimal = String.valueOf(logEvent.sender_contract_decimals); // TODO:
        ev.timeStamp = transactionTime!!.toLong() / 1000
        ev.hash = txId
        ev.nonce = 0 // TODO:
        ev.tokenName = ""
        ev.tokenSymbol = transactionSymbol
        ev.contractAddress = tokenContractAddress
        ev.blockNumber = height.toString()
        ev.from = from
        ev.to = to
        ev.tokenID = tokenId.toString()

        if (!isNft) {
            val decimals = 18 // TODO: decimals can be found via OkLinkService.getTokenDetails();
            val bi = BigDecimal(amount)
            ev.value = BalanceUtils.getScaledValueMinimal(
                bi,
                decimals.toLong(), 5
            )
            ev.tokenDecimal = "18"
        } else {
            ev.value = amount
            ev.tokenDecimal = ""
            ev.tokenValue = amount
        }

        ev.gasUsed = "0" // TODO:
        ev.gasPrice = "0" // TODO:
        ev.gas = "0" // TODO:

        return ev
    }
}
