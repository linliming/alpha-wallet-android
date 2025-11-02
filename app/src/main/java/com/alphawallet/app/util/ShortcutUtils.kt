package com.alphawallet.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Pair
import android.view.View
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.alphawallet.app.R
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkRepository.Companion.getChainLogo
import com.alphawallet.app.widget.AWalletAlertDialog
import java.math.BigInteger
import java.util.stream.Collectors

object ShortcutUtils {
    fun createShortcut(
        pair: Pair<BigInteger?, NFTAsset>,
        intent: Intent,
        context: Context,
        token: Token
    ) {
        val name = getName(token, pair.second)
        val shortcut = ShortcutInfoCompat.Builder(context, token.getAddress())
            .setShortLabel(name!!)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithResource(context, getChainLogo(token.tokenInfo.chainId)))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private fun getName(token: Token, asset: NFTAsset): String? {
        if (asset.getName() == null) {
            return token.getFullName()
        }
        return asset.getName()
    }

    fun getShortcutIds(
        context: Context,
        token: Token,
        assets: List<Pair<BigInteger?, NFTAsset>>
    ): ArrayList<String> {
        val names = assets.stream()
            .map<String?> { p: Pair<BigInteger?, NFTAsset> -> getName(token, p.second) }.collect(
                Collectors.toList<String?>()
            )
        val ids = ArrayList<String>()
        val dynamicShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        for (dynamicShortcut in dynamicShortcuts) {
            if (names.contains(dynamicShortcut.shortLabel)) {
                ids.add(dynamicShortcut.id)
            }
        }
        return ids
    }

    fun showConfirmationDialog(
        activity: Activity,
        shortcutIds: List<String?>,
        message: String?,
        callback: StandardFunctionInterface
    ) {
        val confirmationDialog = AWalletAlertDialog(activity)
        confirmationDialog.setCancelable(false)
        confirmationDialog.setTitle(R.string.title_remove_shortcut)
        confirmationDialog.setMessage(message)
        confirmationDialog.setButton(R.string.yes_continue) { v: View? ->
            ShortcutManagerCompat.removeDynamicShortcuts(activity, shortcutIds)
            confirmationDialog.dismiss()
            callback.showTransferToken(ArrayList())
        }
        confirmationDialog.setSecondaryButtonText(R.string.dialog_cancel_back)
        confirmationDialog.setSecondaryButtonListener { v: View? ->
            confirmationDialog.dismiss()
            activity.finish()
        }
        confirmationDialog.show()
    }
}
