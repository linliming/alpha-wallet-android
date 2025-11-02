package com.alphawallet.app.entity.tokenscript

interface TokenScriptRenderCallback {
    fun callToJSComplete(function: String?, result: String?)
}
