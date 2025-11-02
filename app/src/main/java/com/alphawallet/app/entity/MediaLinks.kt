package com.alphawallet.app.entity

import android.content.Context
import android.text.TextUtils

object MediaLinks {
    // Update these media platform links and ids to target your media groups,
    // then update the MEDIA_TARGET_APPLICATION to match your applicationId
    const val MEDIA_TARGET_APPLICATION: String = "io.stormbird.wallet"
    const val AWALLET_DISCORD_URL: String = "https://discord.gg/nbb9VSF85A"
    const val AWALLET_TWITTER_ID: String = "twitter://user?user_id=938624096123764736"
    const val AWALLET_FACEBOOK_ID: String = "fb://page/1958651857482632"
    const val AWALLET_TWITTER_URL: String = "https://twitter.com/AlphaWallet"
    const val AWALLET_FACEBOOK_URL: String = "https://www.facebook.com/AlphaWallet/"
    @JvmField
    val AWALLET_LINKEDIN_URL: String? = null
    const val AWALLET_REDDIT_URL: String = "https://www.reddit.com/r/AlphaWallet/"
    val AWALLET_INSTAGRAM_URL: String? = null
    val AWALLET_BLOG_URL: String? = null
    const val AWALLET_FAQ_URL: String = "https://alphawallet.com/faq/"
    const val AWALLET_GITHUB: String = "https://github.com/AlphaWallet/alpha-wallet-android/issues"
    const val AWALLET_EMAIL1: String = "feedback+android"
    const val AWALLET_EMAIL2: String = "alphawallet.com"
    const val AWALLET_SUBJECT: String = "AlphaWallet Android Help"

    @JvmStatic
    fun isMediaTargeted(context: Context): Boolean {
        return if (!TextUtils.isEmpty(MEDIA_TARGET_APPLICATION)) {
            context.packageName == MEDIA_TARGET_APPLICATION
        } else {
            false
        }
    }
}
