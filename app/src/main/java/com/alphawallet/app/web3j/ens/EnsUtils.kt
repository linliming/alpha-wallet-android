/*
 * Copyright 2022 Web3 Labs Ltd.
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

import org.web3j.utils.Numeric

object EnsUtils {
    const val EMPTY_ADDRESS: String = "0x0000000000000000000000000000000000000000"

    // Wildcard resolution
    val ENSIP_10_INTERFACE_ID: ByteArray = Numeric.hexStringToByteArray("0x9061b923")
    const val EIP_3668_CCIP_INTERFACE_ID: String = "0x556f1830"

    fun isAddressEmpty(address: String): Boolean {
        return EMPTY_ADDRESS == address
    }

    fun isEIP3668(data: String?): Boolean {
        if (data == null || data.length < 10) {
            return false
        }

        return EIP_3668_CCIP_INTERFACE_ID == data.substring(0, 10)
    }

    fun getParent(url: String?): String? {
        val ensUrl = url?.trim { it <= ' ' } ?: ""

        if (ensUrl == "." || !ensUrl.contains(".")) {
            return null
        }

        return ensUrl.substring(ensUrl.indexOf(".") + 1)
    }
}
