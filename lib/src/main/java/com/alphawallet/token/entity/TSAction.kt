package com.alphawallet.token.entity

/**
 * Created by James on 2/04/2019.
 * Stormbird in Singapore
 */
class TSAction {
    @JvmField
    var order: Int = 0

    @JvmField
    var exclude: String? = null

    @JvmField
    var view: TSTokenView? = null

    @JvmField
    var style: String = ""
    var name: String? = null

    @JvmField
    var attributes: MutableMap<String, Attribute>? = null

    @JvmField
    var function: FunctionDefinition? = null

    @JvmField
    var modifier: ActionModifier? = null
}
