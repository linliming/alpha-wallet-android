package com.alphawallet.app.entity.attestation

import com.alphawallet.app.entity.tokens.TokenCardMeta

interface AttestationImportInterface {
    fun attestationImported(newToken: TokenCardMeta?)
    fun importError(error: String?)
    fun smartPassValidation(validation: SmartPassReturn?)
}
