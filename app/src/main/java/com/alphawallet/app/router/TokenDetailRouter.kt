package com.alphawallet.app.router

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.AssetDisplayActivity
import com.alphawallet.app.ui.Erc20DetailActivity
import com.alphawallet.app.ui.NFTActivity
import com.alphawallet.app.ui.NFTAssetDetailActivity


class TokenDetailRouter {
    fun makeERC20DetailsIntent(
        context: Context?,
        address: String?,
        symbol: String?,
        decimals: Int,
        isToken: Boolean,
        wallet: Wallet?,
        token: Token,
        hasDefinition: Boolean
    ): Intent {
        val intent = Intent(context, Erc20DetailActivity::class.java)
        intent.putExtra(C.EXTRA_SENDING_TOKENS, isToken)
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address)
        intent.putExtra(C.EXTRA_SYMBOL, symbol)
        intent.putExtra(C.EXTRA_DECIMALS, decimals)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition)
        return intent
    }

    fun open(
        context: Activity,
        address: String?,
        symbol: String?,
        decimals: Int,
        isToken: Boolean,
        wallet: Wallet?,
        token: Token,
        hasDefinition: Boolean
    ) {
        val intent = makeERC20DetailsIntent(
            context,
            address,
            symbol,
            decimals,
            isToken,
            wallet,
            token,
            hasDefinition
        )
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY)
    }

    fun open(context: Activity, token: Token, wallet: Wallet?, hasDefinition: Boolean) {
        val intent = Intent(context, NFTActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY)
    }

    fun open(activity: Activity, token: Token, wallet: Wallet?) {
        val intent = Intent(activity, NFTActivity::class.java)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.Key.WALLET, wallet)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivityForResult(intent, C.TERMINATE_ACTIVITY)
    }

    fun openLegacyToken(context: Activity, token: Token, wallet: Wallet?) {
        val intent = Intent(context, AssetDisplayActivity::class.java)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.Key.WALLET, wallet)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivityForResult(intent, C.TERMINATE_ACTIVITY)
    }

    fun openAttestation(context: Activity, token: Token, wallet: Wallet?, asset: NFTAsset) {
        val intent = Intent(context, NFTAssetDetailActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.tokenInfo.address)
        intent.putExtra(C.EXTRA_TOKEN_ID, token.getUUID().toString())
        intent.putExtra(C.EXTRA_ATTESTATION_ID, asset.getAttestationID())
        intent.putExtra(C.EXTRA_NFTASSET, asset)
        context.startActivityForResult(intent, C.TERMINATE_ACTIVITY)
    }
}
