package com.alphawallet.app.service

import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.SequenceInputStream
import java.util.Locale

object JsonValidator {
    const val UNAUTHORIZED_ERROR: String = "unauthorized"
    const val INTERNAL_ERROR: String = "internal error"
    const val LIMIT_EXCEEDED: String = "limit exceeded"
    const val JSON_ERROR: String = "error"

    @Throws(IOException::class)
    fun validateAndGetStream(responseBody: ResponseBody): InputStream {
        val originalStream = responseBody.byteStream()
        val peekedData = ByteArrayOutputStream()

        // Create a TeeInputStream: one branch for validation, the other for the caller
        val teeStream = CustomTeeInputStream(originalStream, peekedData)

        // Validate the peeked JSON
        val isValid = isValidJson(teeStream)
        val bais = ByteArrayInputStream(peekedData.toByteArray())
        val combinedStream: InputStream = SequenceInputStream(
            bais, ByteArrayInputStream(
                ByteArray(0)
            )
        )

        if (isValid) {
            // Return combined stream, even though bais now has all the data the original stream had
            // this way we can guarantee it will behave exactly as the original stream
            return combinedStream
        } else {
            // If invalid, handle accordingly (e.g., throw an exception or return an error stream)
            throw IOException("Invalid JSON format")
        }
    }

    private fun isValidJson(inputStream: InputStream): Boolean {
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val content = StringBuilder()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    content.append(line)
                }

                val thisObj = JSONObject(content.toString())
                if (thisObj.has(JSON_ERROR)) {
                    val error = thisObj.getString(JSON_ERROR)
                    if (error.lowercase(Locale.getDefault())
                            .contains(UNAUTHORIZED_ERROR) || error.lowercase(
                            Locale.getDefault()
                        ).contains(
                            INTERNAL_ERROR
                        )
                    ) {
                        return false
                    }
                }
                return !content.toString().lowercase(Locale.getDefault()).contains(
                    LIMIT_EXCEEDED
                )
            }
        } catch (e: JSONException) {
            return false
        } catch (e: IOException) {
            return false
        }
    }

    internal class CustomTeeInputStream(
        `in`: InputStream?,
        private val branch: ByteArrayOutputStream
    ) :
        FilterInputStream(`in`) {
        @Throws(IOException::class)
        override fun read(): Int {
            val ch = super.read()
            if (ch != -1) {
                branch.write(ch)
            }
            return ch
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val result = super.read(b, off, len)
            if (result != -1) {
                branch.write(b, off, result)
            }
            return result
        }
    }
}
