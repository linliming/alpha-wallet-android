package com.alphawallet.app

object C {
    const val IMPORT_REQUEST_CODE: Int = 1001
    const val EXPORT_REQUEST_CODE: Int = 1002
    const val SHARE_REQUEST_CODE: Int = 1003
    const val REQUEST_SELECT_NETWORK: Int = 1010
    const val REQUEST_BACKUP_WALLET: Int = 1011
    const val REQUEST_TRANSACTION_CALLBACK: Int = 1012
    const val UPDATE_LOCALE: Int = 1013
    const val UPDATE_CURRENCY: Int = 1014
    const val REQUEST_UNIVERSAL_SCAN: Int = 1015
    const val TOKEN_SEND_ACTIVITY: Int = 1016

    const val BARCODE_READER_REQUEST_CODE: Int = 1
    const val SET_GAS_SETTINGS: Int = 2
    const val COMPLETED_TRANSACTION: Int = 3
    const val SEND_INTENT_REQUEST_CODE: Int = 4
    const val TERMINATE_ACTIVITY: Int = 5
    const val ADDED_TOKEN_RETURN: Int = 9
    const val STANDARD_POPUP_INACTIVITY_DISMISS: Int = 15 * 1000 //Standard dismiss after 15 seconds

    const val ETHEREUM_NETWORK_NAME: String = "Ethereum"
    const val CLASSIC_NETWORK_NAME: String = "Ethereum Classic"
    const val XDAI_NETWORK_NAME: String = "Gnosis"
    const val GOERLI_NETWORK_NAME: String = "Görli (Test)"
    const val BINANCE_TEST_NETWORK: String = "BSC TestNet"
    const val BINANCE_MAIN_NETWORK: String = "Binance (BSC)"
    const val FANTOM_NETWORK: String = "Fantom Opera"
    const val FANTOM_TEST_NETWORK: String = "Fantom (Test)"
    const val AVALANCHE_NETWORK: String = "Avalanche"
    const val FUJI_TEST_NETWORK: String = "Avalanche FUJI (Test)"
    const val POLYGON_NETWORK: String = "Polygon"
    const val POLYGON_TEST_NETWORK: String = "Mumbai (Test)"
    const val OPTIMISTIC_NETWORK: String = "Op Mainnet"
    const val CRONOS_MAIN_NETWORK: String = "Cronos"
    const val CRONOS_TEST_NETWORK: String = "Cronos (Test)"
    const val ARBITRUM_ONE_NETWORK: String = "Arbitrum One"
    const val PALM_NAME: String = "PALM"
    const val PALM_TEST_NAME: String = "PALM (Test)"
    const val KLAYTN_NAME: String = "Kaia Mainnet"
    const val KLAYTN_BAOBAB_NAME: String = "Kaia Kairos (Test)"
    const val IOTEX_NAME: String = "IoTeX"
    const val IOTEX_TESTNET_NAME: String = "IoTeX (Test)"
    const val AURORA_MAINNET_NAME: String = "Aurora"
    const val AURORA_TESTNET_NAME: String = "Aurora (Test)"
    const val MILKOMEDA_NAME: String = "Milkomeda Cardano"
    const val MILKOMEDA_TESTNET_NAME: String = "Milkomeda Cardano (Test)"
    const val SEPOLIA_TESTNET_NAME: String = "Sepolia (Test)"
    const val ARBITRUM_TESTNET_NAME: String = "Arbitrum Sepolia (Test)"
    const val OKX_NETWORK_NAME: String = "OKXChain Mainnet"
    const val ROOTSTOCK_NETWORK_NAME: String = "Rootstock"
    const val ROOTSTOCK_TESTNET_NAME: String = "Rootstock (Test)"
    const val LINEA_NAME: String = "Linea"
    const val LINEA_TESTNET_NAME: String = LINEA_NAME + " (Test)"
    const val HOLESKY_TESTNET_NAME: String = "Holesky (Test)"

    const val AMOY_TESTNET_NAME: String = "Amoy (Test)"
    const val BASE_MAINNET_NAME: String = "Base"
    const val BASE_TESTNET_NAME: String = "Base Sepolia (Test)"
    const val MANTLE_MAINNET_NAME: String = "Mantle"
    const val MANTLE_TESTNET_NAME: String = "Mantle Sepolia (Test)"
    const val MINT_MAINNET_NAME: String = "Mint"
    const val MINT_TESTNET_NAME: String = "Mint Sepolia (Test)"

    const val ETHEREUM_TICKER_NAME: String = "ethereum"
    const val CLASSIC_TICKER_NAME: String = "ethereum-classic"
    const val XDAI_TICKER_NAME: String = "dai"
    const val BINANCE_TICKER: String = "binance"

    const val ETHEREUM_TICKER: String = "ethereum"

    const val USD_SYMBOL: String = "$"
    const val ETH_SYMBOL: String = "ETH"

    const val MANTLE_SYMBOL: String = "MNT"
    const val xDAI_SYMBOL: String = "xDai"
    const val ETC_SYMBOL: String = "ETC"
    const val GOERLI_SYMBOL: String = "GÖETH"
    const val BINANCE_SYMBOL: String = "BNB"
    const val FANTOM_SYMBOL: String = "FTM"
    const val AVALANCHE_SYMBOL: String = "AVAX"
    const val POLYGON_SYMBOL: String = "MATIC"
    const val CRONOS_SYMBOL: String = "CRO"
    const val CRONOS_TEST_SYMBOL: String = "tCRO"
    const val ARBITRUM_SYMBOL: String = "AETH"
    const val PALM_SYMBOL: String = "PALM"
    const val KLAYTN_SYMBOL: String = "KAIA"
    const val IOTEX_SYMBOL: String = "IOTX"
    const val MILKOMEDA_SYMBOL: String = "milkADA"
    const val MILKOMEDA_TEST_SYMBOL: String = "milktADA"
    const val SEPOLIA_SYMBOL: String = "ETH"
    const val OKX_SYMBOL: String = "OKT"
    const val ROOTSTOCK_SYMBOL: String = "RBTC"
    const val ROOTSTOCK_TEST_SYMBOL: String = "tBTC"
    const val HOLESKY_TEST_SYMBOL: String = "Hol" + ETH_SYMBOL

    const val AMOY_TESTNET_SYMBOL: String = "Am" + ETH_SYMBOL

    const val BURN_ADDRESS: String = "0x0000000000000000000000000000000000000000"

    //some important known contracts - NB must be all lower case for switch statement
    const val DAI_TOKEN: String = "0x6b175474e89094c44da98b954eedeac495271d0f"
    const val SAI_TOKEN: String = "0x89d24a6b4ccb1b6faa2625fe562bdd9a23260359"

    const val ALPHAWALLET_WEB: String = "https://www.alphawallet.com"

    const val XDAI_BRIDGE_DAPP: String = "https://bridge.xdaichain.com/"

    const val QUICKSWAP_EXCHANGE_DAPP: String = "https://quickswap.exchange/#/swap"
    const val ONEINCH_EXCHANGE_DAPP: String =
        "https://app.1inch.io/#/[CHAIN]/swap/[TOKEN1]/[TOKEN2]"

    const val GLIDE_URL_INVALID: String = "com.bumptech.glide.load.HttpException"

    const val GWEI_UNIT: String = "Gwei"

    const val MARKET_SALE: String = "market"

    const val EXTRA_ADDRESS: String = "ADDRESS"
    const val EXTRA_CONTRACT_ADDRESS: String = "CONTRACT_ADDRESS"
    const val EXTRA_DECIMALS: String = "DECIMALS"
    const val EXTRA_SYMBOL: String = "SYMBOL"
    const val EXTRA_SENDING_TOKENS: String = "SENDING_TOKENS"
    const val EXTRA_TO_ADDRESS: String = "TO_ADDRESS"
    const val EXTRA_AMOUNT: String = "AMOUNT"
    const val EXTRA_GAS_PRICE: String = "GAS_PRICE"
    const val EXTRA_GAS_LIMIT: String = "GAS_LIMIT"
    const val EXTRA_CUSTOM_GAS_LIMIT: String = "CUSTOM_GAS_LIMIT"
    const val EXTRA_GAS_LIMIT_PRESET: String = "GAS_LIMIT_PRESET"
    const val EXTRA_ACTION_NAME: String = "NAME"
    const val EXTRA_TOKEN_ID: String = "TID"
    const val EXTRA_TOKEN_BALANCE: String = "BALANCE"
    const val EXTRA_TOKENID_LIST: String = "TOKENIDLIST"
    const val EXTRA_ATTESTATION_ID: String = "ATTNID"
    const val EXTRA_NFTASSET_LIST: String = "NFTASSET_LIST"
    const val EXTRA_NFTASSET: String = "NFTASSET"
    const val ERC875RANGE: String = "ERC875RANGE"
    const val TOKEN_TYPE: String = "TOKEN_TYPE"
    const val MARKET_INSTANCE: String = "MARKET_INSTANCE"
    const val IMPORT_STRING: String = "TOKEN_IMPORT"
    const val EXTRA_PRICE: String = "TOKEN_PRICE"
    const val EXTRA_STATE: String = "TRANSFER_STATE"
    const val EXTRA_WEB3TRANSACTION: String = "WEB3_TRANSACTION"
    const val EXTRA_NETWORK_NAME: String = "NETWORK_NAME"
    const val EXTRA_NETWORK_MAINNET: String = "NETWORK_MAINNET"
    const val EXTRA_ENS_DETAILS: String = "ENS_DETAILS"
    const val EXTRA_HAS_DEFINITION: String = "HAS_TOKEN_DEF"
    const val EXTRA_SUCCESS: String = "TX_SUCCESS"
    const val EXTRA_HEXDATA: String = "TX_HEX"
    const val EXTRA_NETWORKID: String = "NET_ID"
    const val EXTRA_TRANSACTION_DATA: String = "TS_TRANSACTIONDATA"
    const val EXTRA_FUNCTION_NAME: String = "TS_FUNC_NAME"
    const val EXTRA_SINGLE_ITEM: String = "SINGLE_ITEM"
    const val EXTRA_CHAIN_ID: String = "CHAIN_ID"
    const val EXTRA_CALLBACKID: String = "CALLBACK_ID"
    const val EXTRA_LOCALE: String = "LOCALE_STRING"
    const val EXTRA_PAGE_TITLE: String = "PTITLE"
    const val EXTRA_CURRENCY: String = "CURRENCY_STRING"
    const val EXTRA_MIN_GAS_PRICE: String = "_MINGASPRICE"
    const val EXTRA_QR_CODE: String = "QR_SCAN_CODE"
    const val EXTRA_SCAN_SOURCE: String = "SCAN_SOURCE"
    const val EXTRA_UNIVERSAL_SCAN: String = "UNIVERSAL_SCAN"
    const val EXTRA_NONCE: String = "_NONCE"
    const val EXTRA_TXHASH: String = "_TXHASH"
    const val DAPP_URL_LOAD: String = "DAPP_URL"
    const val EXTRA_PRICE_ALERT: String = "EXTRA_PRICE_ALERT"
    const val EXTRA_SESSION_ID: String = "SESSION_ID"
    const val EXTRA_WC_REQUEST_ID: String = "REQUEST_ID"
    const val EXTRA_APPROVED: String = "APPROVED"
    const val EXTRA_CHAIN_AVAILABLE: String = "CHAIN_AVAILABLE"
    const val EXTRA_NAME: String = "NAME"
    const val EXTRA_CHAIN_OBJ: String = "CHAIN_OBJ"
    const val EXTRA_1559_TX: String = "1559_TX"
    const val EXTRA_FROM_SPLASH: String = "FROM_SPLASH"

    const val PRUNE_ACTIVITY: String = "com.stormbird.wallet.PRUNE_ACTIVITY"

    const val RESET_WALLET: String = "com.stormbird.wallet.RESET"
    const val ADDED_TOKEN: String = "com.stormbird.wallet.ADDED"
    const val CHANGED_LOCALE: String = "com.stormbird.wallet.CHANGED_LOCALE"
    const val PAGE_LOADED: String = "com.stormbird.wallet.PAGE_LOADED"
    const val RESET_TOOLBAR: String = "com.stormbird.wallet.RESET_TOOLBAR"
    const val SIGN_DAPP_TRANSACTION: String = "com.stormbird.wallet.SIGN_TRANSACTION"
    const val REQUEST_NOTIFICATION_ACCESS: String = "com.stormbird.wallet.REQUEST_NOTIFICATION"
    const val BACKUP_WALLET_SUCCESS: String = "com.stormbird.wallet.BACKUP_SUCCESS"
    const val CHANGE_CURRENCY: String = "com.stormbird.wallet.CHANGE_CURRENCY"
    const val RESET_TRANSACTIONS: String = "com.stormbird.wallet.RESET_TRANSACTIONS"
    const val WALLET_CONNECT_REQUEST: String = "com.stormbird.wallet.WALLET_CONNECT"
    const val WALLET_CONNECT_NEW_SESSION: String = "com.stormbird.wallet.WC_NEW_SESSION"
    const val WALLET_CONNECT_FAIL: String = "com.stormbird.wallet.WC_FAIL"
    const val WALLET_CONNECT_COUNT_CHANGE: String = "com.stormbird.wallet.WC_CCHANGE"
    const val WALLET_CONNECT_CLIENT_TERMINATE: String = "com.stormbird.wallet.WC_CLIENT_TERMINATE"
    const val WALLET_CONNECT_SWITCH_CHAIN: String = "com.stormbird.wallet.WC_SWITCH_CHAIN"
    const val WALLET_CONNECT_ADD_CHAIN: String = "com.stormbird.wallet.WC_ADD_CHAIN"
    const val SHOW_BACKUP: String = "com.stormbird.wallet.CHECK_BACKUP"
    const val HANDLE_BACKUP: String = "com.stormbird.wallet.HANDLE_BACKUP"
    const val FROM_HOME_ROUTER: String = "HomeRouter"
    const val TOKEN_CLICK: String = "com.stormbird.wallet.TOKEN_CLICK"
    const val SETTINGS_INSTANTIATED: String = "com.stormbird.wallet.SETTINGS_INSTANTIATED"
    const val APP_FOREGROUND_STATE: String = "com.alphawallet.APP_FOREGROUND_STATE"
    const val EXTRA_APP_FOREGROUND: String = "com.alphawallet.IS_FOREGORUND"
    const val QRCODE_SCAN: String = "com.alphawallet.QRSCAN"
    const val AWALLET_CODE: String = "com.alphawallet.AWALLET"
    const val SIGNAL_NFT_SYNC: String = "com.alphawallet.SYNC_NFT"
    const val SYNC_STATUS: String = "com.alphawallet.SYNC_STATUS"

    const val DEFAULT_GAS_PRICE: String = "10000000000"
    const val DEFAULT_XDAI_GAS_PRICE: String = "1000000000"
    const val DEFAULT_GAS_LIMIT_FOR_TOKENS: String = "144000"
    const val DEFAULT_UNKNOWN_FUNCTION_GAS_LIMIT: String =
        "1000000" //if we don't know the specific function, we default to 1 million gas limit
    const val DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS: String =
        "432000" //NFTs typically require more gas
    const val GAS_LIMIT_MIN: Long = 21000L
    const val GAS_LIMIT_DEFAULT: Long = 90000L
    const val GAS_LIMIT_CONTRACT: Long = 1000000L
    const val GAS_PRICE_MIN: Long = 400000000L
    const val ETHER_DECIMALS: Int = 18
    const val GAS_LIMIT_MAX: Long = 4712380L //Max block gas for most chains
    const val GAS_LIMIT_MAX_KLAYTN: Long =
        100000000L //Klaytn gas limit, see https://docs.klaytn.com/klaytn/design/computation/computation-cost
    const val GAS_LIMIT_MAX_AURORA: Long = 6721975L

    //FOR DEMOS ETC
    const val SHOW_NEW_ACCOUNT_PROMPT: Boolean =
        false //this will switch off the splash screen 'please make a key' message

    const val DEFAULT_NETWORK: String = ETHEREUM_NETWORK_NAME

    const val TWITTER_PACKAGE_NAME: String = "com.twitter.android"
    const val FACEBOOK_PACKAGE_NAME: String = "com.facebook.katana"
    const val LINKEDIN_PACKAGE_NAME: String = "com.linkedin.android"
    const val REDDIT_PACKAGE_NAME: String = "com.reddit.frontpage"
    const val INSTAGRAM_PACKAGE_NAME: String = "com.instagram.android"
    const val FROM_NOTIFICATION: String = "from_notification"
    const val SHORT_SYMBOL_LENGTH: Int = 5

    const val DAPP_HOMEPAGE_KEY: String = "dappHomePage"
    const val DAPP_LASTURL_KEY: String = "dappURL"
    const val DAPP_BROWSER_HISTORY: String = "DAPP_BROWSER_HISTORY"
    const val DAPP_BROWSER_BOOKMARKS: String = "dappBrowserBookmarks"
    const val DAPP_DEFAULT_URL: String = "https://www.stateofthedapps.com/"
    const val DAPP_PREFIX_TELEPHONE: String = "tel"
    const val DAPP_PREFIX_MAILTO: String = "mailto"
    const val DAPP_PREFIX_ALPHAWALLET: String = "alphawallet"
    const val DAPP_SUFFIX_RECEIVE: String = "receive"
    const val DAPP_PREFIX_MAPS: String = "maps.google.com/maps?daddr="
    const val DAPP_PREFIX_WALLETCONNECT: String = "wc"
    const val DAPP_PREFIX_AWALLET: String = "awallet"

    const val ENS_SCAN_BLOCK: String = "ens_check_block"
    const val ENS_HISTORY: String = "ensHistory"
    const val ENS_HISTORY_PAIR: String = "ens_history_pair"


    const val INTERNET_SEARCH_PREFIX: String = "https://duckduckgo.com/?q="
    const val HTTPS_PREFIX: String = "https://"

    // Settings Badge Keys
    const val KEY_NEEDS_BACKUP: String = "needsBackup"
    const val KEY_UPDATE_AVAILABLE: String = "updateAvailable"

    const val DEFAULT_CURRENCY_CODE: String = "USD"
    const val ACTION_MY_ADDRESS_SCREEN: String = "my_address_screen"

    //Analytics
    const val PREF_UNIQUE_ID: String = "unique_id"

    const val ALPHAWALLET_LOGO_URI: String =
        "https://alphawallet.com/wp-content/themes/alphawallet/img/logo-horizontal-new.svg"
    const val ALPHAWALLET_WEBSITE: String = "https://alphawallet.com"
    const val WALLET_CONNECT_REACT_APP_RELAY_URL: String = "wss://relay.walletconnect.com"
    const val ALPHA_WALLET_LOGO_URL: String =
        "https://user-images.githubusercontent.com/51817359/158344418-c0f2bd19-38bb-4e64-a1d5-25ceb099688a.png"

    // Theme/Dark Mode
    const val THEME_LIGHT: Int = 0
    const val THEME_DARK: Int = 1
    const val THEME_AUTO: Int = 2

    // OpenSea APIs
    const val OPENSEA_COLLECTION_API_MAINNET: String = "https://api.opensea.io/collection/"
    const val OPENSEA_ASSETS_API_V2: String =
        "https://api.opensea.io/api/v2/chain/{CHAIN}/account/{ADDRESS}/nfts"
    const val OPENSEA_NFT_API_V2: String =
        "https://api.opensea.io/api/v2/chain/{CHAIN}/contract/{ADDRESS}/nfts/{TOKEN_ID}"

    //Timing
    @JvmField
    var CONNECT_TIMEOUT: Long = 10 //Seconds
    @JvmField
    var READ_TIMEOUT: Long = 10
    @JvmField
    var WRITE_TIMEOUT: Long = 10
    var PING_INTERVAL: Long = 10
    const val LONG_WRITE_TIMEOUT: Long = 30

    const val EXTERNAL_APP_DOWNLOAD_LINK: String =
        "https://alphawallet.com/download/AlphaWallet-release-build.apk"

    // shortcuts
    const val ACTION_TOKEN_SHORTCUT: String = "token_shortcut"

    interface ErrorCode {
        companion object {
            const val UNKNOWN: Int = 1
            const val CANT_GET_STORE_PASSWORD: Int = 2
            const val ALREADY_ADDED: Int = 3
            const val EMPTY_COLLECTION: Int = 4

            // Swap Error Codes
            const val INSUFFICIENT_BALANCE: Int = 5
            const val SWAP_CHAIN_ERROR: Int = 6
            const val SWAP_CONNECTIONS_ERROR: Int = 7
            const val SWAP_QUOTE_ERROR: Int = 8
            const val SWAP_TIMEOUT_ERROR: Int = 9
        }
    }

    interface Key {
        companion object {
            const val WALLET: String = "wallet"
            const val TRANSACTION: String = "transaction"
            const val TICKET_RANGE: String = "ticket_range"
            const val MARKETPLACE_EVENT: String = "marketplace_event"
            const val SHOULD_SHOW_SECURITY_WARNING: String = "should_show_security_warning"
            const val FROM_SETTINGS: String = "from_settings"
            const val API_V1_REQUEST_URL: String = "api_v1_request_url"
        }
    }

    enum class TokenStatus {
        DEFAULT, PENDING, INCOMPLETE
    }
}
