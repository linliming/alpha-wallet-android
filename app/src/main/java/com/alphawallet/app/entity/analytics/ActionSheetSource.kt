package com.alphawallet.app.entity.analytics

enum class ActionSheetSource
    (val value: String) {
    WALLET_CONNECT("WalletConnect"),
    SWAP("Swap"),
    SEND_FUNGIBLE("Send Fungible"),
    SEND_NFT("Send NFT"),
    TOKENSCRIPT("TokenScript"),
    BROWSER("Browser"),
    CLAIM_PAID_MAGIC_LINK("Claim Paid MagicLink"),
    SPEEDUP_TRANSACTION("Speed Up Transaction"),
    CANCEL_TRANSACTION("Cancel Transaction")
}
