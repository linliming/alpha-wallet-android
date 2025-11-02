package com.alphawallet.app.entity

import androidx.annotation.IntDef

/**
 * Base class providing the common contract for token list management states.
 */
abstract class TokenManageType {

    @ManageType
    private var internalType: Int = SHOW_ZERO_BALANCE

    /**
     * Returns the current token-management classification.
     */
    @ManageType
    fun getTokenManageType(): Int = internalType

    /**
     * Updates the token-management classification value.
     *
     * @param type one of the constants defined in [ManageType]
     */
    fun setTokenManageType(@ManageType type: Int) {
        internalType = type
    }

    @Retention(AnnotationRetention.SOURCE)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.PROPERTY,
    )
    @IntDef(
        SHOW_ZERO_BALANCE,
        LABEL_DISPLAY_TOKEN,
        DISPLAY_TOKEN,
        LABEL_HIDDEN_TOKEN,
        HIDDEN_TOKEN,
        LABEL_POPULAR_TOKEN,
        POPULAR_TOKEN,
    )
    annotation class ManageType

    companion object {
        const val SHOW_ZERO_BALANCE = 0
        const val LABEL_DISPLAY_TOKEN = 1
        const val DISPLAY_TOKEN = 2
        const val LABEL_HIDDEN_TOKEN = 3
        const val HIDDEN_TOKEN = 4
        const val LABEL_POPULAR_TOKEN = 5
        const val POPULAR_TOKEN = 6
    }
}
