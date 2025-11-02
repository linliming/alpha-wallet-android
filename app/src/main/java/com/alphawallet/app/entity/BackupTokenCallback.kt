package com.alphawallet.app.entity

/**
 * Created by James on 18/07/2019.
 * Stormbird in Sydney
 */
interface BackupTokenCallback {
    fun backUpClick(wallet: Wallet?) {}
    fun remindMeLater(wallet: Wallet?) {}
}
