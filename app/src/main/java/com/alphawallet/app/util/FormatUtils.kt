package com.alphawallet.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.token.entity.ProviderTypedData
import org.web3j.crypto.StructuredDataEncoder
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date

fun getSignMessageTitle(message: String): CharSequence {
    return buildSpannedString {
        append(message)
        if (message.isNotEmpty()) {
            setSpan(
                ForegroundColorSpan(Color.RED),
                0,
                1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(Color.RED),
                message.length - 1,
                message.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

fun formatTypedMessage(rawData: Array<ProviderTypedData>): CharSequence {
    return buildSpannedString {
        rawData.forEachIndexed { index, data ->
            if (index > 0) append("\n")
            inSpans(StyleSpan(Typeface.BOLD)) {
                append(data.name)
                append(":")
            }
            append("\n  ")
            append(data.value.toString())
        }
    }
}

fun formatEIP721Message(messageData: StructuredDataEncoder): CharSequence {
    val messageMap = messageData.jsonMessageObject.message as HashMap<String, Any>
    return buildSpannedString {
        messageMap.forEach { (entry, v) ->
            inSpans(StyleSpan(Typeface.BOLD)) {
                append(entry)
                append(":")
                append("\n")
            }
            if (v is LinkedHashMap<*, *>) {
                val valueMap = v as HashMap<String, Any>
                valueMap.forEach { (paramName, value) ->
                    inSpans(StyleSpan(Typeface.BOLD)) {
                        append(" ")
                        append(paramName)
                        append(": ")
                    }
                    append(value.toString())
                    append("\n")
                }
            } else {
                append(" ")
                append(v.toString())
                append("\n")
            }
        }
    }
}

fun createFormattedValue(operationName: String, token: Token?): CharSequence {
    val symbol = token?.getShortSymbol() ?: ""
    var opName = operationName
    var needsBreak = false

    if ((symbol.length + opName.length) > 16 && symbol.isNotEmpty()) {
        val spaceIndex = opName.lastIndexOf(' ')
        if (spaceIndex > 0) {
            opName = opName.substring(0, spaceIndex) + '\n' + opName.substring(spaceIndex + 1)
        } else {
            needsBreak = true
        }
    }

    return buildSpannedString {
        inSpans(StyleSpan(Typeface.NORMAL)) {
            append(opName)
        }
        if (needsBreak) {
            append("\n")
        } else {
            append(" ")
        }
        inSpans(StyleSpan(Typeface.BOLD)) {
            append(symbol)
        }
    }
}

fun convertTimePeriodInSeconds(pendingTimeInSeconds: Long, ctx: Context): String {
    var secondsRemaining = pendingTimeInSeconds
    val days = secondsRemaining / (60 * 60 * 24)
    secondsRemaining -= (days * 60 * 60 * 24)
    val hours = secondsRemaining / (60 * 60)
    secondsRemaining -= (hours * 60 * 60)
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60

    val sb = StringBuilder()
    var timePoints = 0

    if (days > 0) {
        timePoints = 2
        sb.append(
            if (days == 1L) ctx.getString(R.string.day_single)
            else ctx.getString(R.string.day_plural, days.toString())
        )
    }

    if (hours > 0) {
        if (timePoints == 0) timePoints = 1 else sb.append(", ")
        sb.append(
            if (hours == 1L) ctx.getString(R.string.hour_single)
            else ctx.getString(R.string.hour_plural, hours.toString())
        )
    }

    if (minutes > 0 && timePoints < 2) {
        if (timePoints != 0) sb.append(", ")
        timePoints++
        sb.append(
            if (minutes == 1L) ctx.getString(R.string.minute_single)
            else ctx.getString(R.string.minute_plural, minutes.toString())
        )
    }

    if (seconds > 0 && timePoints < 2) {
        if (timePoints != 0) sb.append(", ")
        sb.append(
            if (seconds == 1L) ctx.getString(R.string.second_single)
            else ctx.getString(R.string.second_plural, seconds.toString())
        )
    }
    return sb.toString()
}

fun shortConvertTimePeriodInSeconds(pendingTimeInSeconds: Long, ctx: Context): String {
    var secondsRemaining = pendingTimeInSeconds
    val days = secondsRemaining / (60 * 60 * 24)
    secondsRemaining -= (days * 60 * 60 * 24)
    val hours = secondsRemaining / (60 * 60)
    secondsRemaining -= (hours * 60 * 60)
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60

    return when {
        pendingTimeInSeconds == -1L -> ctx.getString(R.string.never)
        days > 0 -> ctx.getString(R.string.day_single)
        hours > 0 -> {
            if (hours == 1L && minutes == 0L) {
                ctx.getString(R.string.hour_single)
            } else {
                val hourStr = BigDecimal.valueOf(hours + minutes.toDouble() / 60.0)
                    .setScale(1, RoundingMode.HALF_DOWN) //to 1 dp
                ctx.getString(R.string.hour_plural, hourStr.toString())
            }
        }
        minutes > 0 -> {
            if (minutes == 1L && seconds == 0L) {
                ctx.getString(R.string.minute_single)
            } else {
                val minsStr = BigDecimal.valueOf(minutes + seconds.toDouble() / 60.0)
                    .setScale(1, RoundingMode.HALF_DOWN) //to 1 dp
                ctx.getString(R.string.minute_plural, minsStr.toString())
            }
        }
        else -> {
            if (seconds == 1L) {
                ctx.getString(R.string.second_single)
            } else {
                ctx.getString(R.string.second_plural, seconds.toString())
            }
        }
    }
}

fun localiseUnixTime(ctx: Context, timeStampInSec: Long): String {
    val date = Date(timeStampInSec * DateUtils.SECOND_IN_MILLIS)
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, LocaleUtils.getDeviceLocale(ctx))
    return timeFormat.format(date)
}

fun localiseUnixDate(ctx: Context, timeStampInSec: Long): String {
    val date = Date(timeStampInSec * DateUtils.SECOND_IN_MILLIS)
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, LocaleUtils.getDeviceLocale(ctx))
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, LocaleUtils.getDeviceLocale(ctx))
    return "${timeFormat.format(date)} | ${dateFormat.format(date)}"
}
