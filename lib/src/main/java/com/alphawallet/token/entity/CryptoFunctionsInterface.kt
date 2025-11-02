package com.alphawallet.token.entity

import java.math.BigInteger
import java.security.SignatureException

interface CryptoFunctionsInterface {
    fun Base64Decode(message: String?): ByteArray?

    fun Base64Encode(data: ByteArray?): ByteArray?

    @Throws(SignatureException::class)
    fun signedMessageToKey(data: ByteArray?, signature: ByteArray?): BigInteger?

    fun getAddressFromKey(recoveredKey: BigInteger?): String?

    fun keccak256(message: ByteArray?): ByteArray?

    /**
     * See class Utils: Uses Android text formatting
     */
    fun formatTypedMessage(rawData: Array<ProviderTypedData?>?): CharSequence?

    /**
     * See class Utils: Uses web3j: you need to provide this function to decode EIP712.
     * -- Currently web3j uses a different library for Android and Generic Java packages.
     * -- One day web3j could be united, then we can remove these functions
     */
    fun formatEIP721Message(messageData: String?): CharSequence?

    /**
     * See class Utils: Uses web3j
     */
    fun getStructuredData(messageData: String?): ByteArray?

    fun getChainId(messageData: String?): Long
}
