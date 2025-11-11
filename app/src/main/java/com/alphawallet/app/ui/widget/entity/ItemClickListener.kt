package com.alphawallet.app.ui.widget.entity

/**
 * Created by James on 17/11/2018.
 * Stormbird in Singapore
 */
interface ItemClickListener {
    fun onItemClick(url: String?)
    fun onItemLongClick(url: String?) {} //only override this if extra handling is needed
}
