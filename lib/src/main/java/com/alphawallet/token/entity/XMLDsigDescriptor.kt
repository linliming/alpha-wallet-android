package com.alphawallet.token.entity

class XMLDsigDescriptor {
    @JvmField
    var result: String? = null

    @JvmField
    var subject: String? = null

    @JvmField
    var keyName: String? = null

    @JvmField
    var keyType: String? = null

    @JvmField
    var issuer: String? = null

    @JvmField
    var type: SigReturnType? = null

    @JvmField
    var certificateName: String? = null

    constructor()

    constructor(issuerText: String?) {
        issuer = issuerText
        certificateName = EIP5169_CERTIFIER
        keyName = EIP5169_KEY_OWNER
        keyType = "ECDSA"
        result = "Pass"
        subject = ""
        type = SigReturnType.SIGNATURE_PASS
    }

    fun setKeyDetails(
        isDebug: Boolean,
        failKeyName: String,
    ) {
        if (pass()) {
            if (isDebug) {
                this.type = SigReturnType.DEBUG_SIGNATURE_PASS
            } else {
                this.type = SigReturnType.SIGNATURE_PASS
            }

            if (this.certificateName != null && certificateName!!.contains(MATCHES_DEPLOYER)) {
                this.keyName = EIP5169_KEY_OWNER
            }
        } else {
            setFailedIssuer(isDebug, failKeyName)
        }
    }

    fun pass(): Boolean = this.result != null && (this.result == "pass" || this.result == "valid")

    private fun setFailedIssuer(
        isDebug: Boolean,
        failKeyName: String,
    ) {
        this.keyName = failKeyName
        if (this.subject != null && subject!!.contains("Invalid")) {
            if (isDebug) {
                this.type = SigReturnType.DEBUG_SIGNATURE_INVALID
            } else {
                this.type = SigReturnType.SIGNATURE_INVALID
            }
        } else {
            if (isDebug) {
                this.type = SigReturnType.DEBUG_NO_SIGNATURE
            } else {
                this.type = SigReturnType.NO_SIGNATURE
            }
        }
    }

    companion object {
        private const val EIP5169_CERTIFIER = "Smart Token Labs"

        // TODO Source this from the contract via owner()
        private const val EIP5169_KEY_OWNER = "Contract Owner"
        private const val MATCHES_DEPLOYER = "matches the contract deployer"
    }
}
