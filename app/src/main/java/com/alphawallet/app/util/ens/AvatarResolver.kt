package com.alphawallet.app.util.ens

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.ens.NameHash
import timber.log.Timber

class AvatarResolver(private val ensResolver: EnsResolver) : Resolvable {

    override fun resolve(ensName: String?): String? {
        if (!EnsResolver.isValidEnsName(ensName)) return null
        return try {
            ensResolver.cancelCurrentResolve()
            val resolverAddress = ensName?.let { ensResolver.getResolverAddress(it) }
            if (!resolverAddress.isNullOrEmpty()) {
                val nameHash = NameHash.nameHashAsBytes(ensName)
                ensResolver.getContractData(resolverAddress, getAvatar(nameHash), "")
            } else null
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private fun getAvatar(nameHash: ByteArray): Function = Function(
        "text",
        listOf<Type<*>>(Bytes32(nameHash), Utf8String("avatar")),
        listOf<TypeReference<*>>(object : TypeReference<Utf8String>() {})
    )
}

