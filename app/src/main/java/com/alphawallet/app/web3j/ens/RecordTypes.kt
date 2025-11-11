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

import org.web3j.utils.Numeric

/**
 * Record type interfaces supported by resolvers as per [EIP-137](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-137.md#resolver-specification)
 */
object RecordTypes {
    val ADDR: ByteArray = Numeric.hexStringToByteArray("0x3b3b57de")
    val NAME: ByteArray = Numeric.hexStringToByteArray("0x691f3431")
    val ABI: ByteArray = Numeric.hexStringToByteArray("0x2203ab56")
    val PUB_KEY: ByteArray = Numeric.hexStringToByteArray("0xc8690233")
}
