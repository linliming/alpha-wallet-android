package com.alphawallet.app.entity.attestation

import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

class AttestationCoreData : DynamicStruct {
    var schema: ByteArray
    var recipient: Address
    var time: Long
    var expirationTime: Long
    var revocable: Boolean
    var refUID: ByteArray
    var data: ByteArray

    constructor(
        schema: ByteArray,
        recipient: Address,
        time: Long,
        expirationTime: Long,
        revocable: Boolean,
        refUID: ByteArray,
        data: ByteArray
    ) : super(
        Bytes32(schema),
        Address(recipient.value),
        Uint64(BigInteger.valueOf(time)),
        Uint64(BigInteger.valueOf(expirationTime)),
        Bool(revocable),
        Bytes32(refUID),
        DynamicBytes(data)
    ) {
        this.recipient = recipient
        this.time = time
        this.expirationTime = expirationTime
        this.revocable = revocable
        this.refUID = refUID
        this.data = data
        this.schema = schema

        Timber.d(
            "Format for struct: " + Numeric.toHexString(schema) + "," + recipient.value + "," + time + "," + expirationTime + "," + revocable + "," + Numeric.toHexString(
                refUID
            ) + ",0," + Numeric.toHexString(data)
        )
    }

    constructor(
        schema: Bytes32,
        recipient: Address,
        time: Uint64,
        expirationTime: Uint64,
        revocable: Bool,
        refUID: Bytes32,
        data: DynamicBytes
    ) : super(schema, recipient, time, expirationTime, revocable, refUID, data) {
        this.recipient = recipient
        this.time = time.value.toLong()
        this.expirationTime = expirationTime.value.toLong()
        this.revocable = revocable.value
        this.refUID = refUID.value
        this.data = data.value
        this.schema = schema.value
    }
}
