/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alphawallet.app.web3j.ens

import android.text.TextUtils
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.IDN
import java.nio.charset.StandardCharsets
import java.util.Arrays

/** ENS name hash implementation.  */
object NameHash {
    private val EMPTY = ByteArray(32)

    @Throws(EnsResolutionException::class)
    fun nameHashAsBytes(ensName: String): ByteArray {
        return Numeric.hexStringToByteArray(nameHash(ensName))
    }

    @JvmStatic
    @Throws(EnsResolutionException::class)
    fun nameHash(ensName: String): String {
        val normalisedEnsName = normalise(ensName)
        return Numeric.toHexString(
            nameHash(
                normalisedEnsName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()))
    }

    private fun nameHash(labels: Array<String>): ByteArray {
        if (labels.size == 0 || labels[0] == "") {
            return EMPTY
        } else {
            val tail = if (labels.size == 1) {
                arrayOf()
            } else {
                Arrays.copyOfRange(labels, 1, labels.size)
            }

            val remainderHash = nameHash(tail)
            val result = remainderHash.copyOf(64)

            val labelHash = Hash.sha3(labels[0].toByteArray(StandardCharsets.UTF_8))
            System.arraycopy(labelHash, 0, result, 32, labelHash.size)

            return Hash.sha3(result)
        }
    }

    /**
     * Normalise ENS name as per the [specification](http://docs.ens.domains/en/latest/implementers.html#normalising-and-validating-names).
     *
     * @param ensName our user input ENS name
     * @return normalised ens name
     * @throws EnsResolutionException if the name cannot be normalised
     */
    fun normalise(ensName: String): String {
        try {
            return IDN.toASCII(ensName, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (e: Exception) {
            throw EnsResolutionException("Invalid ENS name provided: $ensName")
        }
    }

    fun toUtf8Bytes(string: String?): ByteArray? {
        if (string == null || string.isEmpty()) {
            return null
        }
        return string.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Encode Dns name. Reference implementation
     * https://github.com/ethers-io/ethers.js/blob/fc1e006575d59792fa97b4efb9ea2f8cca1944cf/packages/hash/src.ts/namehash.ts#L49
     *
     * @param name Dns name
     * @return Encoded name in Hex format.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dnsEncode(name: String): String {
        val parts = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val outputStream = ByteArrayOutputStream()
        for (part in parts) {
            if (TextUtils.isEmpty(part)) {
                break
            }
            val bytes = toUtf8Bytes(
                "_" + normalise(
                    part
                )
            )
                ?: break
            bytes[0] = (bytes.size - 1).toByte()

            outputStream.write(bytes)
        }

        return Numeric.toHexString(outputStream.toByteArray()) + "00"
    }
}
