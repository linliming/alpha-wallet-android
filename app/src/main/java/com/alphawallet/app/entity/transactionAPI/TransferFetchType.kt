package com.alphawallet.app.entity.transactionAPI

/**
 * Created by JB on 10/02/2023.
 */
enum class TransferFetchType
    (val value: String) {
    ETHEREUM("eth"),  // dummy type for storing token reads
    ERC_20("tokentx"),
    ERC_721("tokennfttx"),
    ERC_1155("token1155tx")
}
