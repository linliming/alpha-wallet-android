package com.alphawallet.app.router

import android.app.Activity
import android.content.Intent
import com.alphawallet.app.R
import com.alphawallet.app.ui.CoinbasePayActivity

class CoinbasePayRouter {
    /**
     * @param activity    - Calling activity
     * @param tokenSymbol - Token symbol of the asset you wish to purchase, e.g. "ETH", "USDC"
     */
    fun buyAsset(activity: Activity, tokenSymbol: String?) {
        val intent = Intent(activity, CoinbasePayActivity::class.java)
        intent.putExtra("asset", tokenSymbol)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.hold)
    }

    /**
     * @param activity   - Calling activity
     * @param blockchain - Select from supported chains from `CoinbasePayRepository.Blockchains`
     */
    fun buyFromSelectedChain(activity: Activity, blockchain: String?) {
        val intent = Intent(activity, CoinbasePayActivity::class.java)
        intent.putExtra("blockchain", blockchain)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.hold)
    }

    fun open(activity: Activity) {
        val intent = Intent(activity, CoinbasePayActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.hold)
    }
}
