package com.alphawallet.app.entity.attestation

import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32

class SchemaRecord : DynamicStruct {
    var uid: ByteArray
    var resolver: Address
    var revocable: Boolean
    var schema: String

    constructor(uid: ByteArray, resolver: Address, revocable: Boolean, schema: String) : super(
        Bytes32(uid),
        Address(resolver.value),
        Bool(revocable),
        Utf8String(schema)
    ) {
        this.uid = uid
        this.resolver = resolver
        this.revocable = revocable
        this.schema = schema
    }

    constructor(uid: Bytes32, resolver: Address, revocable: Bool, schema: Utf8String) : super(
        uid,
        resolver,
        revocable,
        schema
    ) {
        this.uid = uid.value
        this.resolver = resolver
        this.revocable = revocable.value
        this.schema = schema.value
    }
}
