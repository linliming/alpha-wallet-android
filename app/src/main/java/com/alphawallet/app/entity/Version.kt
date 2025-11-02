package com.alphawallet.app.entity

import kotlin.math.max

class Version(version: String?) : Comparable<Version?> {
    private var version: String? = null

    init {
        if (version != null && version.matches("[0-9]+(\\.[0-9]+)*".toRegex())) {
            this.version = version
        } else {
            this.version = ""
        }
    }

    fun get(): String? {
        return this.version
    }

    override fun compareTo(that: Version?): Int {
        if (that == null || version!!.length == 0) {
            return 1
        }
        val thisParts =
            get()!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val thatParts =
            that.get()!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val length = max(thisParts.size.toDouble(), thatParts.size.toDouble()).toInt()
        for (i in 0..<length) {
            val thisPart = if (i < thisParts.size) thisParts[i].toInt() else 0
            val thatPart = if (i < thatParts.size) thatParts[i].toInt() else 0
            if (thisPart < thatPart) return -1
            if (thisPart > thatPart) return 1
        }
        return 0
    }

    override fun equals(that: Any?): Boolean {
        if (this === that) return true
        if (that == null) return false
        if (this.javaClass != that.javaClass) return false
        return this.compareTo(that as Version) == 0
    }
}
