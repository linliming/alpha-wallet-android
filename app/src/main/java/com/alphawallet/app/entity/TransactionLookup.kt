package com.alphawallet.app.entity

import com.alphawallet.app.R

object TransactionLookup {
    private val typeMapping: MutableMap<TransactionType, Int> = HashMap()

    @JvmStatic
    fun typeToName(type: TransactionType): Int {
        setupTypes()
        return if (type.ordinal > typeMapping.size) {
            typeMapping[TransactionType.UNKNOWN]!!
        } else typeMapping[type]!!
    }

    fun typeToEvent(type: TransactionType): String {
        return when (type) {
            TransactionType.TRANSFER_FROM, TransactionType.SEND -> "sent"
            TransactionType.TRANSFER_TO -> "received"
            TransactionType.RECEIVE_FROM -> "received"
            TransactionType.RECEIVED -> "received"
            TransactionType.APPROVE -> "ownerApproved"
            else -> ""
        }
    }

    fun toFromText(type: TransactionType): Int {
        return when (type) {
            TransactionType.MAGICLINK_PURCHASE, TransactionType.TRANSFER_TO -> R.string.to
            TransactionType.RECEIVED, TransactionType.RECEIVE_FROM -> R.string.from_op
            TransactionType.APPROVE -> R.string.approve
            else -> R.string.empty
        }
    }

    private fun setupTypes() {
        if (typeMapping.size == 0) {
            typeMapping[TransactionType.UNKNOWN] =
                R.string.ticket_invalid_op
            typeMapping[TransactionType.LOAD_NEW_TOKENS] =
                R.string.ticket_load_new_tickets
            typeMapping[TransactionType.MAGICLINK_TRANSFER] =
                R.string.ticket_magiclink_transfer
            typeMapping[TransactionType.MAGICLINK_PICKUP] =
                R.string.ticket_magiclink_pickup
            typeMapping[TransactionType.MAGICLINK_SALE] =
                R.string.ticket_magiclink_sale
            typeMapping[TransactionType.MAGICLINK_PURCHASE] =
                R.string.ticket_magiclink_purchase
            typeMapping[TransactionType.PASS_TO] =
                R.string.ticket_pass_to
            typeMapping[TransactionType.PASS_FROM] =
                R.string.ticket_pass_from
            typeMapping[TransactionType.TRANSFER_TO] =
                R.string.ticket_transfer_to
            typeMapping[TransactionType.RECEIVE_FROM] =
                R.string.ticket_receive_from
            typeMapping[TransactionType.REDEEM] =
                R.string.ticket_redeem
            typeMapping[TransactionType.ADMIN_REDEEM] =
                R.string.ticket_admin_redeem
            typeMapping[TransactionType.CONSTRUCTOR] =
                R.string.ticket_contract_constructor
            typeMapping[TransactionType.TERMINATE_CONTRACT] =
                R.string.ticket_terminate_contract
            typeMapping[TransactionType.TRANSFER_FROM] =
                R.string.ticket_transfer_from
            typeMapping[TransactionType.ALLOCATE_TO] =
                R.string.allocate_to
            typeMapping[TransactionType.APPROVE] =
                R.string.approve
            typeMapping[TransactionType.RECEIVED] =
                R.string.received
            typeMapping[TransactionType.SEND] =
                R.string.action_send
            typeMapping[TransactionType.SEND_ETH] =
                R.string.action_send_eth
            typeMapping[TransactionType.TOKEN_SWAP] =
                R.string.action_token_swap
            typeMapping[TransactionType.WITHDRAW] =
                R.string.action_withdraw
            typeMapping[TransactionType.DEPOSIT] =
                R.string.deposit
            typeMapping[TransactionType.CONTRACT_CALL] =
                R.string.contract_call
            typeMapping[TransactionType.REMIX] =
                R.string.remix
            typeMapping[TransactionType.MINT] =
                R.string.token_mint
            typeMapping[TransactionType.BURN] =
                R.string.token_burn
            typeMapping[TransactionType.COMMIT_NFT] =
                R.string.commit_nft
            typeMapping[TransactionType.SAFE_TRANSFER] =
                R.string.safe_transfer
            typeMapping[TransactionType.SAFE_BATCH_TRANSFER] =
                R.string.safe_batch_transfer

            typeMapping[TransactionType.UNKNOWN_FUNCTION] =
                R.string.contract_call
        }
    }
}
