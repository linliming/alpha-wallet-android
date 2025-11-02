package com.alphawallet.app.entity.tokenscript

import android.content.Context
import com.alphawallet.app.R
import com.alphawallet.token.entity.XMLDsigDescriptor
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Random

class TokenScriptFile : File {
    private var context: Context? = null
    private var active = false
    private var resourceFile = false
    private var fileName: String? = null
    var scriptUrl: String? = null

    constructor(ctx: Context?) : super("") {
        context = ctx
        active = false
        resourceFile = false
    }

    constructor(ctx: Context, pathname: String, name: String) : super(pathname, name) {
        InitFile(ctx, "$pathname/$name")
    }

    constructor(ctx: Context, pathname: String) : super(pathname) {
        InitFile(ctx, pathname)
    }

    private fun InitFile(
        ctx: Context,
        pathname: String,
    ) {
        var pathname = pathname
        context = ctx
        fileName = pathname

        if (exists() && canRead()) {
            active = true
        } else {
            try {
                if (!pathname.isEmpty() && pathname.startsWith("/")) {
                    pathname = pathname.substring(1) // .getAbsolute() adds a '/' to the filename
                }

                val fPathName = File(pathname)
                if (fPathName.exists() && fPathName.isFile) {
                    val isName = context!!.resources.assets.open(pathname)
                    if (isName.available() > 0) resourceFile = true
                    isName.close()
                    fileName = pathname // correct the filename if required
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    val inputStream: InputStream?
        get() {
            try {
                if (active) {
                    return FileInputStream(this)
                } else if (resourceFile) {
                    return context!!.resources.assets.open(fileName!!)
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
            return null
        }

    val isValidTokenScript: Boolean
        get() = active || resourceFile

    fun fileUnchanged(fd: TokenScriptFileData?): Boolean = fd?.sigDescriptor != null && fd.hash == calcMD5() && fd.sigDescriptor!!.result == "pass"

    val isDebug: Boolean
        get() {
            // check for the private data area
            if (context != null) {
                val privateArea = context!!.filesDir.absolutePath
                return !absolutePath.startsWith(privateArea)
            } else {
                return false
            }
        }

    fun determineSignatureType(sigDescriptor: XMLDsigDescriptor) {
        val isDebug = isDebug
        val keyName =
            if (isDebug) {
                context!!.getString(R.string.debug_script)
            } else {
                context!!.getString(R.string.unsigned_script)
            }

        sigDescriptor.setKeyDetails(isDebug, keyName)
    }

    fun fileChanged(fileHash: String?): Boolean = fileHash == null || !isValidTokenScript || (fileHash != calcMD5())

    /**
     * 计算文件的MD5哈希值
     * @return MD5哈希字符串
     */
    fun calcMD5(): String {
        val fis = inputStream ?: return ""
        return calcMD5(fis)
    }

    companion object {
        /**
         * 计算输入流的MD5哈希值
         * @param fis 输入流
         * @return MD5哈希字符串
         */
        fun calcMD5(fis: InputStream): String {
            val sb = StringBuilder()
            try {
                val digest = MessageDigest.getInstance("MD5")

                val byteArray = ByteArray(1024)
                var bytesCount = 0

                while ((fis.read(byteArray).also { bytesCount = it }) != -1) {
                    digest.update(byteArray, 0, bytesCount)
                }

                fis.close()

                val bytes = digest.digest()
                for (aByte in bytes) {
                    sb.append(((aByte.toInt() and 0xff) + 0x100).toString(16).substring(1))
                }
            } catch (e: IOException) {
                val rand = Random(System.currentTimeMillis()).nextInt().toString()
                sb.append(rand) // never matches
            } catch (e: NoSuchAlgorithmException) {
                val rand = Random(System.currentTimeMillis()).nextInt().toString()
                sb.append(rand)
            } catch (e: Exception) {
                Timber.w(e)
            }

            // return complete hash
            return sb.toString()
        }
    }
}
