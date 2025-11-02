package com.alphawallet.app.entity.tokens

/**
 * Lightweight wrapper used to group tokens for sorting.
 * Weight currently derived from the hash of the associated data.
 */
class TokenSortGroup(val data: String) : Comparable<TokenSortGroup> {

    val weight: Int = data.hashCode()

    override fun compareTo(other: TokenSortGroup): Int = weight.compareTo(other.weight)
}
