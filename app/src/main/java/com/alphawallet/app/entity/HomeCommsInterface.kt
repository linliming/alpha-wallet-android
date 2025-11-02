package com.alphawallet.app.entity

interface HomeCommsInterface {
    fun requestNotificationPermission()
    fun backupSuccess(keyAddress: String?)
    fun resetTokens()
    fun resetTransactions()
}
