package com.alphawallet.app.util

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * Convenience StringBuilder that builds styled messages to display data in human readable form to the user
 *
 * Created by JB on 28/08/2020.
 */
class StyledStringBuilder : SpannableStringBuilder() {
    private val spanners: MutableList<SpanType> = ArrayList()
    private var startIndex = 0
    private var startGroup = -1

    override fun append(text: CharSequence): SpannableStringBuilder {
        startIndex = this.length
        val replace = super.append(text)
        return replace
    }

    fun setStyle(style: StyleSpan?): SpannableStringBuilder {
        val useStartIndex = if (startGroup != -1) startGroup else startIndex
        spanners.add(SpanType(useStartIndex, this.length, style))
        startGroup = -1
        return this
    }

    fun setColor(colour: Int): SpannableStringBuilder {
        val useStartIndex = if (startGroup != -1) startGroup else startIndex
        val fcs = ForegroundColorSpan(colour)
        spanners.add(SpanType(useStartIndex, this.length, fcs))
        startGroup = -1
        return this
    }

    fun startStyleGroup(): SpannableStringBuilder {
        startGroup = this.length
        return this
    }

    fun applyStyles() {
        for (s in spanners) {
            setSpan(
                if (s.style != null) s.style else s.styleColour,
                s.begin,
                s.end,
                SPAN_POINT_POINT
            )
        }
    }

    private class SpanType {
        var begin: Int
        var end: Int
        var style: StyleSpan?
        var styleColour: ForegroundColorSpan?

        constructor(begin: Int, end: Int, style: StyleSpan?) {
            this.begin = begin
            this.end = end
            this.style = style
            this.styleColour = null
        }

        constructor(begin: Int, end: Int, colour: ForegroundColorSpan?) {
            this.begin = begin
            this.end = end
            this.style = null
            this.styleColour = colour
        }
    }
}
