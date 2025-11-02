package com.alphawallet.app.service

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import com.alphawallet.app.R
import com.alphawallet.app.entity.ServiceErrorException
import com.alphawallet.app.entity.ServiceErrorException.ServiceErrorCode
import com.alphawallet.app.entity.ServiceErrorException.ServiceErrorCode.KEY_IS_GONE
import java.io.File
import java.io.FileInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Helper for reading legacy keystore entries that pre-date the current encryption approach.
 */
object LegacyKeystore {
    /**
     * Reads and decrypts the legacy keystore payload identified by [keyName].
     *
     * @throws ServiceErrorException when the key material is missing or cannot be decrypted.
     */
    @JvmStatic
    @Synchronized
    @Throws(ServiceErrorException::class)
    fun getLegacyPassword(
        context: Context,
        keyName: String,
    ): ByteArray {
        val encryptedDataFilePath = KeyService.getFilePath(context, keyName)
        try {
            val keyStore = java.security.KeyStore.getInstance(KeyService.ANDROID_KEY_STORE).apply {
                load(null)
            }
            val secretKey = keyStore.getKey(keyName, null) as? SecretKey
                ?: run {
                    val message = context.getString(R.string.cannot_read_encrypt_file)
                    throw ServiceErrorException(KEY_IS_GONE, message)
                }

            val keyIV = "${keyName}iv"
            val ivExists = File(KeyService.getFilePath(context, keyIV)).exists()
            val aliasExists = File(KeyService.getFilePath(context, keyName)).exists()
            if (!ivExists || !aliasExists) {
                throw ServiceErrorException(
                    ServiceErrorCode.IV_OR_ALIAS_NO_ON_DISK,
                    context.getString(R.string.cannot_read_encrypt_file),
                )
            }

            val iv = KeyService.readBytesFromFile(KeyService.getFilePath(context, keyIV))
            if (iv == null || iv.isEmpty()) {
                throw NullPointerException(context.getString(R.string.cannot_read_encrypt_file))
            }

            val cipher = Cipher.getInstance(KeyService.LEGACY_CIPHER_ALGORITHM).apply {
                init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            }

            FileInputStream(encryptedDataFilePath).use { fis ->
                CipherInputStream(fis, cipher).use { cis ->
                    return KeyService.readBytesFromStream(cis)
                }
            }
        } catch (e: UserNotAuthenticatedException) {
            throw ServiceErrorException(
                ServiceErrorCode.USER_NOT_AUTHENTICATED,
                context.getString(R.string.authentication_error),
            )
        } catch (e: java.security.InvalidKeyException) {
            throw ServiceErrorException(
                ServiceErrorCode.INVALID_KEY,
                context.getString(R.string.invalid_private_key),
            )
        } catch (e: Exception) {
            throw ServiceErrorException(
                ServiceErrorCode.KEY_STORE_ERROR,
                context.getString(R.string.cannot_read_encrypt_file),
            )
        }
    }
}
