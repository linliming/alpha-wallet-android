package com.alphawallet.app.util.ens

import android.text.TextUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.ens.NameHash
import timber.log.Timber
import java.math.BigInteger
import java.util.Arrays

class CryptoResolver(private val ensResolver: EnsResolver) : Resolvable {

    override fun resolve(ensName: String?): String? {
        if (!EnsResolver.isValidEnsName(ensName)) return null
       return try {
           val nameHash = NameHash.nameHashAsBytes(ensName)
           val nameId = BigInteger(nameHash)
           val resolverAddress =
               ensResolver.getContractData(CRYPTO_RESOLVER, getResolverOf(nameId), "")
           if (!resolverAddress.isNullOrEmpty()) {
               ensResolver.getContractData(resolverAddress?:"", get(nameId), "")
           } else {
               null
           }
        }catch (e:Exception){
            Timber.e(e)
            null
        }

    }

    private fun get(nameId: BigInteger): Function {
        return Function(
            "get",
            Arrays.asList<Type<*>>(Utf8String(CRYPTO_ETH_KEY), Uint256(nameId)),
            Arrays.asList<TypeReference<*>>(object : TypeReference<Utf8String?>() {
            })
        )
    }

    private fun getResolverOf(nameId: BigInteger): Function {
        return Function(
            "resolverOf",
            Arrays.asList<Type<*>>(Uint(nameId)),
            Arrays.asList<TypeReference<*>>(object : TypeReference<Address?>() {
            })
        )
    }

    companion object {
        private const val CRYPTO_RESOLVER = "0xD1E5b0FF1287aA9f9A268759062E4Ab08b9Dacbe"
        private const val CRYPTO_ETH_KEY = "crypto.ETH.address"
    }
}
