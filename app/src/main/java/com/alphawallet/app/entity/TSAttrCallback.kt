package com.alphawallet.app.entity

import com.alphawallet.token.entity.TokenScriptResult

interface TSAttrCallback {
    fun showTSAttributes(attrs: List<TokenScriptResult.Attribute?>?, updateRequired: Boolean)
}
