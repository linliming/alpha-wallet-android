package com.alphawallet.app.util

import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Base64
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.util.Utils.inflateData
import com.google.gson.Gson
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

private const val ATTESTATION_PREFIX = "#attestation="
private const val SMART_PASS_PREFIX = "ticket="

fun String.parseEASAttestation(): String {
    var attestation = attestationViaParams(this)
    if (attestation.isNotEmpty()) {
        val inflate = wrappedInflateData(attestation)
        if (inflate.isNotEmpty()) {
            return inflate
        }
    }

    //now check via pulling params directly
    attestation = extractParam(this, SMART_PASS_PREFIX)
    var inflate = wrappedInflateData(attestation)
    if (inflate.isNotEmpty()) {
        return inflate
    }

    attestation = extractParam(this, ATTESTATION_PREFIX)
    return wrappedInflateData(attestation)
}

fun String.hasEASAttestation(): Boolean {
    return this.parseEASAttestation().isNotEmpty()
}

fun String.extractRawAttestation(): String {
    var attestation = attestationViaParams(this)
    if (attestation.isNotEmpty()) {
        //try decode without conversion
        var inflate = inflateData(attestation)
        if (inflate.isNotEmpty()) {
            return attestation
        }
        val decoded = attestation.replace("_", "/").replace("-", "+")
        inflate = inflateData(decoded)
        if (inflate.isNotEmpty()) {
            return attestation
        }
    }

    //now check via pulling params directly
    attestation = extractParam(this, SMART_PASS_PREFIX)
    val inflate = inflateData(attestation)
    if (inflate.isNotEmpty()) {
        return attestation
    }
    return extractParam(this, ATTESTATION_PREFIX)
}

private fun extractParam(url: String, param: String): String {
    val paramIndex = url.indexOf(param)
    val decoded: String = try {
        if (paramIndex >= 0) { //EAS style attestations have the magic link style
            var extracted = url.substring(paramIndex + param.length)
            extracted = extracted.universalURLDecode()
            //find end param if there is one
            val endIndex = extracted.indexOf("&")
            if (endIndex > 0) {
                extracted.substring(0, endIndex)
            } else {
                extracted
            }
        } else {
            url
        }
    } catch (e: Exception) {
        url
    }
    Timber.d("decoded url: %s", decoded)
    return decoded
}

private fun attestationViaParams(url: String): String {
    return try {
        val uri = Uri.parse(url)
        var payload = uri.getQueryParameter("ticket")
        if (TextUtils.isEmpty(payload)) {
            payload = uri.getQueryParameter("attestation")
        }
        payload?.universalURLDecode() ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun String.universalURLDecode(): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            URLDecoder.decode(this, StandardCharsets.UTF_8)
        } else {
            @Suppress("DEPRECATION")
            URLDecoder.decode(this, "UTF-8")
        }
    } catch (e: Exception) {
        this
    }
}

fun String.toAttestationJson(): String {
    if (this.isEmpty()) return ""

    // Remove the square brackets
    val jsonString = this.substring(1, this.length - 1)
    val e = jsonString.split(",").map {
        it.trim().replace("\"", "")
    }

    val versionParam: Long = if (e.size == 17) {
        e[16].toLongOrNull() ?: 0L
    } else {
        0L
    }

    if (e.size < 16 || e.size > 17) {
        return ""
    }

    val easAttestation = EasAttestation(
        e[0],
        e[1].toLongOrNull() ?: 0L,
        e[2],
        e[3],
        e[4],
        e[5].toLongOrNull() ?: 0L,
        e[6],
        e[7],
        e[8],
        e[9],
        e[10].toLongOrNull() ?: 0L,
        e[11].toLongOrNull() ?: 0L,
        e[12],
        e[13].toBoolean(),
        e[14],
        e[15].toLongOrNull() ?: 0L,
        versionParam
    )
    return Gson().toJson(easAttestation)
}

private fun wrappedInflateData(deflatedData: String): String {
    var inflatedData = inflateData(deflatedData)
    if (inflatedData.isEmpty()) {
        val decoded = deflatedData.replace("_", "/").replace("-", "+")
        inflatedData = inflateData(decoded)
    }
    return inflatedData
}

fun String.inflateData(): String {
    return try {
        val deflatedBytes = Base64.decode(this, Base64.DEFAULT)
        val inflater = Inflater()
        inflater.setInput(deflatedBytes)

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val inflatedBytes = inflater.inflate(buffer)
            outputStream.write(buffer, 0, inflatedBytes)
        }
        inflater.end()

        outputStream.toByteArray().toString(StandardCharsets.UTF_8)
    } catch (e: Exception) {
        ""
    }
}
