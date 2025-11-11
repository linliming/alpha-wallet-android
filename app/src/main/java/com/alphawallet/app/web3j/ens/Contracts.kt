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

import com.alphawallet.ethereum.EthereumNetworkBase

/** ENS registry contract addresses.  */
object Contracts {
    const val MAINNET: String = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val GOERLI: String = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val HOLESKY: String = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val SEPOLIA: String = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"

    @JvmStatic
    fun resolveRegistryContract(chainId: Long): String {
        return if (chainId == EthereumNetworkBase.MAINNET_ID) {
            MAINNET
        } else if (chainId == EthereumNetworkBase.GOERLI_ID) {
            GOERLI
        } else if (chainId == EthereumNetworkBase.HOLESKY_ID) {
            HOLESKY
        } else if (chainId == EthereumNetworkBase.SEPOLIA_TESTNET_ID) {
            SEPOLIA
        } else {
            throw EnsResolutionException(
                "Unable to resolve ENS registry contract for network id: $chainId"
            )
        }
    }
}
