package com.alphawallet.app.entity

/**
 * Created by James on 1/02/2019.
 * Stormbird in Singapore
 */
interface FragmentMessenger {
    fun tokenScriptError(message: String?)

    fun playStoreUpdateReady(versionUpdate: Int)

    fun externalUpdateReady(version: String?)
}
