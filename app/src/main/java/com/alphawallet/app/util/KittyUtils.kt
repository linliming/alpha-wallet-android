package com.alphawallet.app.util

object KittyUtils {
    @JvmStatic
    fun parseCooldownIndex(index: String): String {
        return when (index.toInt()) {
            0 -> "Fast"
            1, 2 -> "Swift"
            3, 4 -> "Snappy"
            5, 6 -> "Brisk"
            7, 8 -> "Plodding"
            9, 10 -> "Slow"
            11, 12 -> "Sluggish"
            else -> "Catatonic"
        }
    }
}
