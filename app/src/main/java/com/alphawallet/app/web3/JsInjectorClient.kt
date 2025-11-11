package com.alphawallet.app.web3

import android.content.Context
import com.alphawallet.app.R
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3.entity.Address
import org.web3j.crypto.Keys
import java.math.BigInteger
import java.util.regex.Pattern

class JsInjectorClient(context: Context) { // `context` in constructor is unused, but kept for API consistency

    // Use private set for properties that should only be modified internally
    var walletAddress: Address? = null
    var chainId: Long = 0L
        private set

    // This property automatically updates when chainId is set
    private var rpcUrl: String = ""

    // Use const val for true compile-time constants
    companion object {
        private const val DEFAULT_CHARSET = "utf-8"
        private const val DEFAULT_MIME_TYPE = "text/html"
        private val SCRIPT_TAG_PATTERN = Pattern.compile("<script", Pattern.CASE_INSENSITIVE)
    }

    // Merged setChainId and setTSChainId into a single, clearer function
    fun setChainId(chainId: Long) {
        this.chainId = chainId
        this.rpcUrl = EthereumNetworkRepository.getDefaultNodeURL(chainId)
    }

    fun initJs(context: Context): String {
        return loadInitJs(context)
    }

    fun providerJs(context: Context): String {
        return Utils.loadFile(context, R.raw.alphawallet_min)
    }

    fun injectWeb3TokenInit(ctx: Context, view: String, tokenContent: String, tokenId: BigInteger): String {
        val tokenIdStr = tokenId.toString(10)
        val tokenIdWrapperName = "token-card-$tokenIdStr"

        var initSrc = Utils.loadFile(ctx, R.raw.init_token)
        initSrc = String.format(initSrc, tokenContent, walletAddress, rpcUrl, chainId, tokenIdWrapperName)

        // Using Kotlin's multi-line strings for better readability
        val ethersMin = "<script>${Utils.loadFile(ctx, R.raw.ethers_js_min)}</script>"
        val wrapper = """<div id="$tokenIdWrapperName" class="token-card">"""
        val finalJs = """
            $ethersMin
            <script>
            $initSrc
            </script>
            $wrapper
            """.trimIndent()

        return injectJS(view, finalJs)
    }

    fun injectJS(html: String, js: String): String {
        if (html.isEmpty()) return html

        val position = getInjectionPosition(html)
        return if (position >= 0) {
            StringBuilder(html).insert(position, js).toString()
        } else {
            html
        }
    }

    fun injectStyleAndWrap(view: String, style: String?): String {
        val finalStyle = style ?: ""
        // Using multi-line strings for cleaner HTML block
        val headerAndStyle = """
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, shrink-to-fit=no" />
                <style type="text/css">
                    $finalStyle
                    .token-card {
                        padding: 0pt;
                        margin: 0pt;
                    }
                </style>
            </head>
            <body>
        """.trimIndent()
        // The opening of the following </div> is in injectWeb3TokenInit()
        return "$headerAndStyle$view</div></body>"
    }

    private fun getInjectionPosition(body: String): Int {
        val lowerBody = body.lowercase()
        val ieDetectTagIndex = lowerBody.indexOf("<!--[if")
        val scriptTagIndex = lowerBody.indexOf("<script")

        val index = when {
            ieDetectTagIndex >= 0 && scriptTagIndex >= 0 -> minOf(scriptTagIndex, ieDetectTagIndex)
            scriptTagIndex >= 0 -> scriptTagIndex
            ieDetectTagIndex >= 0 -> ieDetectTagIndex
            else -> lowerBody.indexOf("</head")
        }

        return if (index < 0) 0 else index // If no suitable tag found, inject at the beginning
    }

    private fun loadInitJs(context: Context): String {
        val initSrc = Utils.loadFile(context, R.raw.init)
        val address = walletAddress?.toString()?.let { Keys.toChecksumAddress(it) } ?: Address.EMPTY.toString()
        return String.format(initSrc, address, rpcUrl, chainId)
    }
}
