package com.alphawallet.app.analytics

object Analytics {
    // Properties
    const val PROPS_IMPORT_WALLET_TYPE: String = "import_wallet_type"
    const val PROPS_WALLET_TYPE: String = "wallet_type"
    const val PROPS_GAS_SPEED: String = "gas_speed"
    const val PROPS_URL: String = "url"
    const val PROPS_QR_SCAN_SOURCE: String = "qr_scan_source"
    const val PROPS_ACTION_SHEET_SOURCE: String = "action_sheet_source"
    const val PROPS_ACTION_SHEET_MODE: String = "action_sheet_mode"
    const val PROPS_SWAP_FROM_TOKEN: String = "from_token"
    const val PROPS_SWAP_TO_TOKEN: String = "to_token"
    const val PROPS_ERROR_MESSAGE: String = "error_message"
    const val PROPS_CUSTOM_NETWORK_NAME: String = "network_name"
    const val PROPS_CUSTOM_NETWORK_RPC_URL: String = "rpc_url"
    const val PROPS_CUSTOM_NETWORK_CHAIN_ID: String = "chain_id"
    const val PROPS_CUSTOM_NETWORK_SYMBOL: String = "symbol"
    const val PROPS_CUSTOM_NETWORK_IS_TESTNET: String = "is_testnet"
    const val PROPS_TRANSACTION_CHAIN_ID: String = "chain"
    const val PROPS_TRANSACTION_TYPE: String = "transactionType"
    const val PROPS_TRANSACTION_SPEED_TYPE: String = "speedType" // TODO
    const val PROPS_TRANSACTION_IS_ALL_FUNDS: String = "isAllFunds" // TODO

    enum class UserProperties
        (val value: String) {
        TRANSACTION_COUNT("transactionCount"),
        TRANSACTION_COUNT_TESTNET("testnetTransactionCount"),
        APPLICATION_ID("Android Application ID")
    }

    enum class Navigation
        (private val screenName: String) {
        WALLET("Wallet"),
        ACTIVITY("Activity"),
        BROWSER("Browser"),
        SETTINGS("Settings"),
        MY_ADDRESS("My Wallet Address"),
        MY_WALLETS("My Wallets"),
        BACK_UP_WALLET("Back Up Wallet"),
        SHOW_SEED_PHRASE("Show Seed Phrase"),
        NAME_WALLET("Name Wallet"),
        SELECT_NETWORKS("Select Networks"),
        CHANGE_LANGUAGE("Change Language"),
        CHANGE_CURRENCY("Change Currency"),
        SETTINGS_DARK_MODE("Settings - Dark Mode"),
        SETTINGS_ADVANCED("Settings - Advanced"),
        SETTINGS_SUPPORT("Settings - Support"),
        ADD_CUSTOM_NETWORK("Add Custom Network"),
        IMPORT_WALLET("Import Wallet"),
        COINBASE_PAY("Buy with Coinbase Pay"),
        WALLET_CONNECT_SESSION_DETAIL("Wallet Connect Session Detail"),
        WALLET_CONNECT_SESSIONS("Wallet Connect Sessions"),
        ACTION_SHEET("ActionSheet"),
        ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION("ActionSheet - Txn Confirmation"),
        ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION_SUCCESSFUL("Txn Confirmation Successful"),
        ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION_FAILED("Txn Confirmation Failed"),
        TOKEN_SWAP("Token Swap"),
        MY_DAPPS("My Dapps"),
        ADD_DAPP("Add to My Dapps"),
        EDIT_DAPP("Edit Dapp"),
        BROWSER_HISTORY("Dapp Browser History"),
        SCAN_QR_CODE("QR Code Scanner"); //        SIGN_MESSAGE_REQUEST("Sign Message Request"),
        //        ON_RAMP("Fiat On-Ramp"),
        //        ON_UNISWAP("Uniswap"),
        //        ON_XDAI_BRIDGE("xDai Bridge"),
        //        ON_HONEYSWAP("Honeyswap"),
        //        ON_ONEINCH("Oneinch"),
        //        ON_CARTHAGE("Carthage"),
        //        ON_ARBITRUM_BRIDGE("Arbitrum Bridge"),
        //        ON_QUICK_SWAP("QuickSwap"),
        //        FALLBACK("<Fallback>"),
        //        SWITCH_SERVERS("Switch Servers"),
        //        TAP_BROWSER_MORE("Browser More Options"),
        //        EXPLORER("Explorer"),
        //        OPEN_SHORTCUT("Shortcut"),
        //        OPEN_HELP_URL("Help URL"),
        //        BLOCKSCAN_CHAT("Blockscan Chat");

        val value: String
            get() = "Screen: $screenName"
    }

    enum class Action
        (val value: String) {
        FIRST_WALLET_ACTION("First Wallet Action"),
        IMPORT_WALLET("Import Wallet"),
        USE_GAS_WIDGET("Use Gas Widget"),
        LOAD_URL("Load URL"),
        ACTION_SHEET_COMPLETED("ActionSheet Completed"),
        ACTION_SHEET_CANCELLED("ActionSheet Cancelled"),
        SCAN_QR_CODE_SUCCESS("Scan QR Code Completed"),
        SCAN_QR_CODE_CANCELLED("Scan QR Code Cancelled"),
        SCAN_QR_CODE_ERROR("Scan QR Code Error"),
        ADD_CUSTOM_CHAIN("Add Custom Chain"),
        EDIT_CUSTOM_CHAIN("Edit Custom Chain"),
        WALLET_CONNECT_SESSION_REQUEST("WalletConnect - Session Request"),
        WALLET_CONNECT_SESSION_APPROVED("WalletConnect - Session Approved"),
        WALLET_CONNECT_SESSION_REJECTED("WalletConnect - Session Rejected"),
        WALLET_CONNECT_SESSION_ENDED("WalletConnect - Session Ended"),
        WALLET_CONNECT_SIGN_MESSAGE_REQUEST("WalletConnect - Sign Message Request"),
        WALLET_CONNECT_SIGN_TRANSACTION_REQUEST("WalletConnect - Sign Transaction Request"),
        WALLET_CONNECT_SEND_TRANSACTION_REQUEST("WalletConnect - Send Transaction Request"),
        WALLET_CONNECT_SWITCH_NETWORK_REQUEST("WalletConnect - Switch Network Request"),
        WALLET_CONNECT_TRANSACTION_SUCCESS("WalletConnect - Transaction Success"),
        WALLET_CONNECT_TRANSACTION_FAILED("WalletConnect - Transaction Failed"),
        WALLET_CONNECT_TRANSACTION_CANCELLED("WalletConnect - Transaction Cancelled"),
        WALLET_CONNECT_CONNECTION_TIMEOUT("WalletConnect - Connection Timeout"),
        BUY_WITH_RAMP("Buy with Ramp Clicked"),
        CLEAR_BROWSER_CACHE("Clear Browser Cache"),
        SHARE_URL("Share URL"),
        DAPP_ADDED("Dapp Added"),
        DAPP_EDITED("Dapp Edited"),
        RELOAD_BROWSER("Reload Browser"),
        SUPPORT_DISCORD("Clicked Discord Link"),
        SUPPORT_EMAIL("Clicked Email Link"),
        SUPPORT_TWITTER("Clicked Twitter Link"),
        SUPPORT_GITHUB("Clicked Github Link"),
        SUPPORT_FAQ("Clicked FAQ Link"),
        DEEP_LINK("Deep Link Opened"),
        DEEP_LINK_API_V1("Deep Link (API V1) Opened") //        WALLET_CONNECT_CANCEL("WalletConnect Cancel"),
        //        WALLET_CONNECT_CONNECTION_FAILED("WalletConnect Connection Failed"),
        //        SIGN_MESSAGE_REQUEST("Sign Message Request"),
        //        CANCEL_SIGN_MESSAGE_REQUEST("Cancel Sign Message Request"),
        //        SWITCHED_SERVER("Switch Server Completed"),
        //        CANCELS_SWITCH_SERVER("Switch Server Cancelled"),
        //        RECTIFY_SEND_TRANSACTION_ERROR_IN_ACTION_SHEET("Rectify Send Txn Error"),
        //        ENTER_URL("Enter URL"),
        //        PING_INFURA("Ping Infura"),
        //        SUBSCRIBE_TO_EMAIL_NEWSLETTER("Subscribe Email Newsletter"),
        //        TAP_SAFARI_EXTENSION_REWRITTEN_URL("Tap Safari Extension Rewritten URL");
        //        SUPPORT_FACEBOOK("Clicked Facebook Link"),
        //        SUPPORT_REDDIT("Clicked Reddit Link"),
    }

    enum class Error
        (private val source: String) {
        TOKEN_SWAP("Token Swap"),
        TOKEN_SCRIPT("TokenScript"),
        BROWSER("Browser"),
        WALLET_CONNECT("WalletConnect");

        val value: String
            get() = "Error: $source"
    }
}
