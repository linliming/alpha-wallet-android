package com.alphawallet.app.service

import com.alphawallet.app.entity.CryptoFunctions.Companion.sigFromByteArray
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.util.Utils
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import com.alphawallet.token.entity.Signable
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.WalletFile
import org.web3j.crypto.WalletUtils
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.crypto.transaction.type.TransactionType
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpType
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Coroutine-based keystore implementation that handles import/export, deletion, and signing logic.
 */
class KeystoreAccountService(
    private val keyFolder: File,
    private val databaseFolder: File,
    private val keyService: KeyService,
) : AccountKeystoreService {

    init {
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        if (!keyFolder.exists()) {
            keyFolder.mkdirs()
        }
    }

    /**
     * Generates a new light keystore using the supplied password.
     */
    override suspend fun createAccount(password: String): Wallet = withContext(Dispatchers.IO) {
        val ecKeyPair = Keys.createEcKeyPair()
        val walletFile = org.web3j.crypto.Wallet.createLight(password, ecKeyPair)
        val jsonStore = objectMapper.writeValueAsString(walletFile)
        importKeystore(jsonStore, password, password)
    }

    /**
     * Imports a keystore JSON payload, rewriting it with the new password.
     */
    override suspend fun importKeystore(
        store: String,
        password: String,
        newPassword: String,
    ): Wallet = withContext(Dispatchers.IO) {
        val address = extractAddressFromStore(store)
        deleteAccountFiles(address)

        try {
            val walletFile = objectMapper.readValue(store, WalletFile::class.java)
            val decryptedPair = org.web3j.crypto.Wallet.decrypt(password, walletFile)
            val credentials = Credentials.create(decryptedPair)
            val rewritten =
                org.web3j.crypto.Wallet.createLight(newPassword, credentials.ecKeyPair)

            val formatter =
                SimpleDateFormat("yyyy-MM-dd'T'hh-mm-ss.mmmm'Z'", Locale.ROOT)
            val timestamp = formatter.format(System.currentTimeMillis()).replace(":", "-")
            val fileName =
                "UTC--$timestamp--${Numeric.cleanHexPrefix(credentials.address)}"
            objectMapper.writeValue(File(keyFolder, fileName), rewritten)

            return@withContext Wallet(credentials.address).apply {
                type = WalletType.KEYSTORE
            }
        } catch (ex: Exception) {
            try {
                deleteAccount(address, newPassword)
            } catch (_: Exception) {
            }
            throw ex
        }
    }

    /**
     * Creates a keystore from a raw private key string.
     */
    override suspend fun importPrivateKey(
        privateKey: String,
        newPassword: String,
    ): Wallet = withContext(Dispatchers.IO) {
        val key = BigInteger(privateKey, PRIVATE_KEY_RADIX)
        val keyPair = ECKeyPair.create(key)
        val wFile = org.web3j.crypto.Wallet.createLight(newPassword, keyPair)
        val jsonStore = objectMapper.writeValueAsString(wFile)
        importKeystore(jsonStore, newPassword, newPassword)
    }

    /**
     * Exports the requested account to a light keystore.
     */
    override suspend fun exportAccount(
        wallet: Wallet,
        password: String,
        newPassword: String,
    ): String = withContext(Dispatchers.IO) {
        val credentials = getCredentials(keyFolder, wallet.address ?: "", password)
        val reEncrypted =
            org.web3j.crypto.Wallet.createLight(newPassword, credentials.ecKeyPair)
        objectMapper.writeValueAsString(reEncrypted)
    }

    /**
     * Deletes keystore files, related storage, and secure entries for the supplied address.
     */
    override suspend fun deleteAccount(
        address: String,
        password: String,
    ) = withContext(Dispatchers.IO) {
        val cleaned = Numeric.cleanHexPrefix(address).lowercase(Locale.ROOT)
        deleteAccountFiles(cleaned)

        databaseFolder.listFiles()?.forEach { file ->
            if (file.name.lowercase(Locale.ROOT).contains(cleaned)) {
                deleteRecursive(file)
            }
        }

        keyService.deleteKey(address)
    }

    /**
     * Signs a legacy transaction assembled from the supplied parameters.
     */
    override suspend fun signTransaction(
        signer: Wallet,
        toAddress: String,
        amount: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: Long,
        data: ByteArray,
        chainId: Long,
    ): SignatureFromKey = withContext(Dispatchers.IO) {
        val rawTx = formatRawTransaction(toAddress, amount, gasPrice, gasLimit, nonce, data)
        val signData = TransactionEncoder.encode(rawTx, chainId)
        val signature = keyService.signData(signer, signData)
        val sigData = sigFromByteArray(signature.signature)
        if (sigData == null) {
            signature.sigType = SignatureReturnType.KEY_CIPHER_ERROR
            signature.failMessage = "Incorrect signature length"
        } else {
            val eipSignature = TransactionEncoder.createEip155SignatureData(sigData, chainId)
            signature.signature = encode(rawTx, eipSignature)
        }
        signature
    }

    /**
     * Signs a prepared [RawTransaction], handling both legacy and EIP-1559 types.
     */
    override suspend fun signTransaction(
        signer: Wallet,
        chainId: Long,
        rtx: RawTransaction,
    ): SignatureFromKey = withContext(Dispatchers.IO) {
        if (rtx.transaction is Transaction1559) {
            signTransactionEIP1559Tx(signer, rtx)
        } else {
            signLegacyTx(signer, chainId, rtx)
        }
    }

    /**
     * Signs an EIP-1559 message using the keystore-backed key.
     */
    override suspend fun signMessage(
        signer: Wallet,
        message: Signable,
    ): SignatureFromKey = withContext(Dispatchers.IO) {
        val signature = keyService.signData(signer, message.prehash)
        signature.signature = patchSignatureVComponent(signature.signature)
        signature
    }

    /**
     * Signs an arbitrary message with the stored keystore; used for fast signing flows.
     */
    override suspend fun signMessageFast(
        signer: Wallet,
        password: String,
        message: ByteArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        val credentials = getCredentials(keyFolder, signer.address ?: "", password)
        val sigData = Sign.signMessage(message, credentials.ecKeyPair)
        patchSignatureVComponent(bytesFromSignature(sigData))
    }

    /**
     * Returns true if a keystore file exists for the supplied address.
     */
    override suspend fun hasAccount(address: String): Boolean = withContext(Dispatchers.IO) {
        val cleaned = Numeric.cleanHexPrefix(address)
        val files = keyFolder.listFiles() ?: return@withContext false
        files.any { it.name.contains(cleaned, ignoreCase = true) }
    }

    /**
     * Returns all keystore backed wallets, sorted by creation time.
     */
    override suspend fun fetchAccounts(): Array<Wallet> = withContext(Dispatchers.IO) {
        val contents = keyFolder.listFiles() ?: return@withContext emptyArray()
        val fileDates = mutableListOf<Date>()
        val walletMap = HashMap<Date, String>()

        contents.forEach { file ->
            val name = file.name
            val index = name.lastIndexOf("-")
            if (index > 0) {
                val address = Numeric.prependHexPrefix(name.substring(index + 1))
                if (Utils.isAddressValid(address)) {
                    val rawDate =
                        name.substring(5, index - 1)
                            .replace("T", " ")
                            .substring(0, 23)
                    val formatter =
                        SimpleDateFormat("yyyy-MM-dd HH-mm-ss.SSS", Locale.ROOT)
                    val date = formatter.parse(rawDate)
                    if (date != null) {
                        fileDates.add(date)
                        walletMap[date] = address
                    }
                }
            }
        }

        Collections.sort(fileDates)
        fileDates.mapNotNull { date ->
            walletMap[date]?.let { address ->
                Wallet(address).apply {
                    type = WalletType.KEYSTORE
                    walletCreationTime = date.time
                }
            }
        }.toTypedArray()
    }

    private suspend fun signLegacyTx(
        signer: Wallet,
        chainId: Long,
        rtx: RawTransaction,
    ): SignatureFromKey {
        val signData = TransactionEncoder.encode(rtx, chainId)
        val signature = keyService.signData(signer, signData)
        val sigData = sigFromByteArray(signature.signature)
        if (sigData == null) {
            signature.sigType = SignatureReturnType.KEY_CIPHER_ERROR
            signature.failMessage = "Incorrect signature length"
        }
        return signature
    }

    private suspend fun signTransactionEIP1559Tx(
        signer: Wallet,
        rtx: RawTransaction,
    ): SignatureFromKey {
        val signData = TransactionEncoder.encode(rtx)
        val signature = keyService.signData(signer, signData)
        val sigData = sigFromByteArray(signature.signature)
        if (sigData == null) {
            signature.sigType = SignatureReturnType.KEY_CIPHER_ERROR
            signature.failMessage = "Incorrect signature length"
        }
        return signature
    }

    private fun extractAddressFromStore(store: String): String {
        return try {
            val json = JSONObject(store)
            Numeric.prependHexPrefix(json.getString("address"))
        } catch (e: JSONException) {
            throw Exception("Invalid keystore")
        }
    }

    private fun deleteAccountFiles(address: String) {
        val cleaned = Numeric.cleanHexPrefix(address)
        keyFolder.listFiles()?.forEach { file ->
            if (file.name.contains(cleaned, ignoreCase = true)) {
                file.delete()
            }
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursive(child) }
        }
        file.delete()
    }

    private fun formatRawTransaction(
        toAddress: String?,
        amount: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: Long,
        data: ByteArray?,
    ): RawTransaction {
        val dataStr = data?.let { Numeric.toHexString(it) } ?: ""
        return if (toAddress.isNullOrEmpty()) {
            RawTransaction.createContractTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                gasLimit,
                amount,
                dataStr,
            )
        } else {
            RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                gasLimit,
                toAddress,
                amount,
                dataStr,
            )
        }
    }

    private fun patchSignatureVComponent(signature: ByteArray?): ByteArray {
        if (signature != null && signature.size == 65 && signature[64] < 27) {
            signature[64] = (signature[64] + 0x1b).toByte()
        }
        return signature ?: ByteArray(0)
    }

    companion object {
        const val KEYSTORE_FOLDER = "keystore/keystore"
        private const val PRIVATE_KEY_RADIX = 16
        private val objectMapper = ObjectMapper()

        /**
         * Encodes the signed transaction, adding the type byte when required.
         */
        @JvmStatic
        fun encode(rawTransaction: RawTransaction, signatureData: Sign.SignatureData): ByteArray {
            val values: List<RlpType> = TransactionEncoder.asRlpValues(rawTransaction, signatureData)
            val encoded = RlpEncoder.encode(RlpList(values))
            return if (rawTransaction.type != TransactionType.LEGACY) {
                ByteBuffer.allocate(encoded.size + 1)
                    .put(rawTransaction.type.rlpType)
                    .put(encoded)
                    .array()
            } else {
                encoded
            }
        }

        /**
         * Returns credentials when available, or throws on failure when desired.
         */
        @JvmStatic
        fun getCredentials(
            keyFolder: File,
            address: String,
            password: String,
        ): Credentials {
            return getCredentialsWithThrow(keyFolder, address, password)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun getCredentialsWithThrow(
            keyFolder: File,
            address: String,
            password: String,
        ): Credentials {
            val cleaned = Numeric.cleanHexPrefix(address)
            val contents = keyFolder.listFiles()
                ?: throw IllegalStateException("Keystore folder missing")
            contents.forEach { file ->
                if (file.name.contains(cleaned, ignoreCase = true)) {
                    Timber.tag("RealmDebug").d("gotcredentials + %s", address)
                    return WalletUtils.loadCredentials(password, file)
                }
            }
            throw IllegalArgumentException("Keystore not found for $address")
        }

        /**
         * Converts the signature into a 65-byte array.
         */
        @JvmStatic
        fun bytesFromSignature(signature: Sign.SignatureData): ByteArray {
            val sigBytes = ByteArray(65)
            try {
                System.arraycopy(signature.r, 0, sigBytes, 0, 32)
                System.arraycopy(signature.s, 0, sigBytes, 32, 32)
                System.arraycopy(signature.v, 0, sigBytes, 64, 1)
            } catch (e: IndexOutOfBoundsException) {
                Timber.e(e)
            }
            return sigBytes
        }
    }
}
