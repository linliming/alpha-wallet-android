package com.alphawallet.app.service

import android.content.Intent
import android.text.TextUtils
import com.alphawallet.app.api.v1.entity.request.ApiV1Request
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.DeepLinkRequest
import com.alphawallet.app.entity.DeepLinkType
import com.alphawallet.app.entity.EIP681Type
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.attestation.ImportAttestation
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.ui.HomeActivity.AW_MAGICLINK_DIRECT
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.SalesOrderMalformed
import com.alphawallet.token.tools.ParseMagicLink

/**
 * Parses incoming deep links from intents and URLs into actionable [DeepLinkRequest]s.
 */
object DeepLinkService {
    const val AW_APP = "https://aw.app/"
    const val WC_PREFIX = "wc?uri="
    const val WC_COMMAND = "wc:"
    const val AW_PREFIX = "awallet://"
    const val OPEN_URL_PREFIX = "openURL?q="

    /**
     * Parses the supplied [importData] or falls back to extras within [startIntent].
     */
    fun parseIntent(importData: String?, startIntent: Intent?): DeepLinkRequest {
        var isOpenURL = false
        var data = importData

        if (data.isNullOrEmpty()) {
            return checkIntents(startIntent)
        }

        if (data.startsWith(AW_PREFIX)) {
            data = data.substring(AW_PREFIX.length)
        }

        if (data.startsWith(OPEN_URL_PREFIX)) {
            isOpenURL = true
            data = data.substring(OPEN_URL_PREFIX.length)
        }

        data = Utils.universalURLDecode(data)

        if (checkSmartPass(data)) {
            return DeepLinkRequest(DeepLinkType.SMARTPASS, data)
        }

        if (data.startsWith("$AW_APP$WC_PREFIX") || data.startsWith(WC_PREFIX)) {
            val prefixIndex = data.indexOf(WC_PREFIX) + WC_PREFIX.length
            return DeepLinkRequest(DeepLinkType.WALLETCONNECT, data.substring(prefixIndex))
        }

        if (data.startsWith(WC_COMMAND)) {
            return DeepLinkRequest(DeepLinkType.WALLETCONNECT, data)
        }

        if (data.startsWith(NotificationService.AWSTARTUP)) {
            return DeepLinkRequest(
                DeepLinkType.TOKEN_NOTIFICATION,
                data.substring(NotificationService.AWSTARTUP.length),
            )
        }

        if (data.startsWith("wc:")) {
            return DeepLinkRequest(DeepLinkType.WALLETCONNECT, data)
        }

        if (ApiV1Request(data).isValid()) {
            return DeepLinkRequest(DeepLinkType.WALLET_API_DEEPLINK, data)
        }

        val directLinkIndex = data.indexOf(AW_MAGICLINK_DIRECT)
        if (directLinkIndex > 0) {
            val link = data.substring(directLinkIndex + AW_MAGICLINK_DIRECT.length)
            if (Utils.isValidUrl(link)) {
                return DeepLinkRequest(DeepLinkType.URL_REDIRECT, link)
            }
        }

        if (isLegacyMagiclink(data)) {
            return DeepLinkRequest(DeepLinkType.LEGACY_MAGICLINK, data)
        }

        if (startIntent != null &&
            data.startsWith("content://") &&
            startIntent.data != null &&
            !startIntent.data?.path.isNullOrEmpty()
        ) {
            return DeepLinkRequest(DeepLinkType.IMPORT_SCRIPT, null)
        }

        if (isOpenURL && Utils.isValidUrl(data)) {
            return DeepLinkRequest(DeepLinkType.URL_REDIRECT, data)
        }

        return checkIntents(startIntent)
    }

    /**
     * Looks for URL extras on the intent when no direct link string is provided.
     */
    private fun checkIntents(startIntent: Intent?): DeepLinkRequest {
        val startIntentData = startIntent?.getStringExtra("url")
        return if (startIntentData != null) {
            DeepLinkRequest(DeepLinkType.URL_REDIRECT, startIntentData)
        } else {
            DeepLinkRequest(DeepLinkType.INVALID_LINK, null)
        }
    }

    /**
     * Determines whether the supplied payload corresponds to a legacy AlphaWallet magic link.
     */
    private fun isLegacyMagiclink(importData: String): Boolean {
        return try {
            val parser = ParseMagicLink(CryptoFunctions(), EthereumNetworkRepository.extraChainsCompat())
            parser.parseUniversalLink(importData).chainId > 0
        } catch (_: SalesOrderMalformed) {
            false
        }
    }

    /**
     * Detects SmartPass attestations embedded within a QR or deep link payload.
     */
    private fun checkSmartPass(importData: String?): Boolean {
        var data = importData ?: return false
        if (data.startsWith(ImportAttestation.SMART_PASS_URL)) {
            data = data.substring(ImportAttestation.SMART_PASS_URL.length)
        }

        val taglessAttestation = Utils.parseEASAttestation(data)
        if (!taglessAttestation.isNullOrEmpty()) {
            val result = QRResult(data)
            result.type = EIP681Type.EAS_ATTESTATION
            result.functionDetail = Utils.toAttestationJson(taglessAttestation)
            return !TextUtils.isEmpty(result.functionDetail)
        }
        return false
    }
}
