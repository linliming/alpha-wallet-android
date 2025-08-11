package com.alphawallet.app.repository.entity

import com.alphawallet.token.entity.SigReturnType
import com.alphawallet.token.entity.XMLDsigDescriptor
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by JB on 4/02/2020.
 */
class RealmCertificateData : RealmObject() {
    @PrimaryKey
    private val instanceKey: String? = null // File hash
    var result: String? = null
    var subject: String? = null
    private var keyName: String? = null
    private var keyType: String? = null
    var issuer: String? = null
    private var certificateName: String? = null
    private var type = 0

    fun setFromSig(sig: XMLDsigDescriptor) {
        this.result = sig.result
        this.subject = sig.subject
        this.keyName = sig.keyName
        this.keyType = sig.keyType
        this.issuer = sig.issuer
        this.certificateName = sig.certificateName
        if (sig.type == null) {
            if (sig.result != null && sig.result == "pass") {
                type =
                    SigReturnType.SIGNATURE_PASS.ordinal
            } else {
                this.type = SigReturnType.SIGNATURE_INVALID.ordinal
            }
        } else {
            this.type = sig.type!!.ordinal
        }
    }

    val dsigObject: XMLDsigDescriptor
        get() {
            val sig =
                XMLDsigDescriptor()
            sig.issuer = this.issuer
            sig.certificateName = this.certificateName
            sig.keyName = this.keyName
            sig.keyType = this.keyType
            sig.result = this.result
            sig.subject = this.subject
            sig.type = SigReturnType.entries[type]

            return sig
        }

    fun getType(): SigReturnType = SigReturnType.entries[type]

    fun setType(type: SigReturnType?) {
        if (type == null && this.result == "pass") {
            this.type = SigReturnType.SIGNATURE_PASS.ordinal
        } else if (type != null) {
            this.type = type.ordinal
        }
    }
}
