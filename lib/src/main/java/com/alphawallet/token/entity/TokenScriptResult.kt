package com.alphawallet.token.entity

import com.alphawallet.token.tools.TokenDefinition
import com.google.gson.Gson
import java.math.BigInteger

/**
 * Created by James on 14/05/2019.
 * Stormbird in Sydney
 */
class TokenScriptResult {
    class Attribute {
        @JvmField
        val id: String?
        @JvmField
        var name: String?
        @JvmField
        var text: String
        @JvmField
        val value: BigInteger?
        val userInput: Boolean
        val syntax: TokenDefinition.Syntax

        constructor(attributeId: String?, name: String?, value: BigInteger?, text: String) {
            this.id = attributeId
            this.name = name
            this.text = text
            this.value = value
            this.userInput = false
            this.syntax = TokenDefinition.Syntax.IA5String
        }

        constructor(
            attributeId: String?,
            name: String?,
            value: BigInteger?,
            text: String,
            userInput: Boolean
        ) {
            this.id = attributeId
            this.name = name
            this.text = text
            this.value = value
            this.userInput = userInput
            this.syntax = TokenDefinition.Syntax.IA5String
        }

        constructor(
            attr: com.alphawallet.token.entity.Attribute,
            value: BigInteger?,
            text: String
        ) {
            this.id = attr.name
            this.name = attr.label
            this.text = text
            this.value = value
            this.userInput = false
            syntax = attr.syntax
        }

        fun attrValue(): String {
            val sb = StringBuilder()

            when (syntax) {
                TokenDefinition.Syntax.IA5String, TokenDefinition.Syntax.DirectoryString, TokenDefinition.Syntax.BitString, TokenDefinition.Syntax.CountryString, TokenDefinition.Syntax.JPEG -> {
                    if (text.length == 0 || (text[0] != '{')) sb.append("\"")
                    sb.append(text)
                    if (text.length == 0 || (text[0] != '{')) sb.append("\"")
                }

                TokenDefinition.Syntax.Integer -> if (value != null) {
                    sb.append((value).toString(10))
                } else {
                    sb.append("0")
                }

                TokenDefinition.Syntax.GeneralizedTime -> {}
                TokenDefinition.Syntax.Boolean -> if (text.equals("TRUE", ignoreCase = true)) {
                    sb.append("true")
                } else {
                    sb.append("false")
                }

                TokenDefinition.Syntax.NumericString -> sb.append(text)
                else -> {
                    if (text.length == 0 || (text[0] != '{')) sb.append("\"")
                    sb.append(text)
                    if (text.length == 0 || (text[0] != '{')) sb.append("\"")
                }
            }

            return sb.toString()
        }

        fun requiresQuoteWrap(): Boolean {
            return when (syntax) {
                TokenDefinition.Syntax.DirectoryString, TokenDefinition.Syntax.IA5String, TokenDefinition.Syntax.NumericString, TokenDefinition.Syntax.BitString, TokenDefinition.Syntax.JPEG -> true
                else -> false
            }
        }
    }

    private val attrs: MutableMap<String, Attribute> = HashMap()

    fun setAttribute(key: String, attr: Attribute) {
        attrs[key] = attr
    }

    val attributes: Map<String, Attribute>
        get() = attrs

    fun getAttribute(attributeId: String): Attribute? {
        return attrs[attributeId]
    }

    companion object {
        @JvmStatic
        fun addPair(attrs: StringBuilder, attr: Attribute) {
            attrs.append(attr.id)
            attrs.append(": ")
            if (attr.requiresQuoteWrap()) {
                attrs.append("\"")
            }
            attrs.append(attr.attrValue())
            if (attr.requiresQuoteWrap()) {
                attrs.append("\"")
            }
            attrs.append(",\n")
        }

        fun <T> addPair(attrs: StringBuilder, attrId: String?, attrValue: T?) {
            attrs.append(attrId)
            attrs.append(": ")

            if (attrValue == null) {
                attrs.append("\"\"")
            } else if (attrValue is BigInteger) {
                attrs.append((attrValue as BigInteger).toString(10))
            } else if (attrValue is Int) {
                attrs.append(attrValue)
            } else if (attrValue is List<*>) {
                attrs.append("\'")
                attrs.append(Gson().toJson(attrValue))
                attrs.append("\'")
            } else {
                val attrValueStr = attrValue as String
                if (attrValueStr.length == 0 || (attrValueStr[0] != '{')) attrs.append("\"")
                attrs.append(attrValueStr)
                if (attrValueStr.length == 0 || (attrValueStr[0] != '{')) attrs.append("\"")
            }

            attrs.append(",\n")
        }
    }
}
