package com.alphawallet.app.interact

import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.repository.EthereumNetworkRepositoryType

class FindDefaultNetworkInteract(private val ethereumNetworkRepository: EthereumNetworkRepositoryType) {

    fun getNetworkName(chainId: Long): String {
        return ethereumNetworkRepository.getNetworkByChain(chainId).shortName
    }

    fun getNetworkInfo(chainId: Long): NetworkInfo {
        return ethereumNetworkRepository.getNetworkByChain(chainId)
    }
}
