package com.alphawallet.app.entity

import com.alphawallet.app.entity.tokenscript.TokenScriptFile
import com.alphawallet.token.entity.ContractInfo

class TokenLocator : ContractInfo {
    private val tokenScriptFile: TokenScriptFile
    val definitionName: String
    val isError: Boolean
    @JvmField
    val errorMessage: String

    constructor(
        name: String,
        origins: ContractInfo,
        file: TokenScriptFile
    ) : super(origins.contractInterface, origins.addresses) {
        this.definitionName = name
        this.tokenScriptFile = file
        this.isError = false
        this.errorMessage = ""
    }

    constructor(
        name: String,
        origins: ContractInfo,
        file: TokenScriptFile,
        error: Boolean,
        errorMessage: String
    ) : super(origins.contractInterface, origins.addresses) {
        this.definitionName = name
        this.tokenScriptFile = file
        this.isError = error
        this.errorMessage = errorMessage
    }

    val fileName: String
        get() = tokenScriptFile.name
    val fullFileName: String
        get() = tokenScriptFile.absolutePath

    val contracts: ContractInfo
        get() = this

    val isDebug: Boolean
        get() = tokenScriptFile.isDebug
}
