package com.alphawallet.app.util.ens

import android.content.Context
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.alphawallet.app.C
import com.alphawallet.app.entity.UnableToResolveENS
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.service.OpenSeaService
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3j.ens.EnsResolutionException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.utils.Numeric
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * AWEnsResolver 负责对外提供 ENS 解析能力，并对常见第三方域名（.bit、Unstoppable Domains）做拓展处理。
 * 本实现以协程为核心，同时保留 RxJava 封装以兼容现有调用方。
 */
class AWEnsResolver(
    web3j: Web3j,
    private val context: Context?,
    private val chainId: Long = -1,
) {
    private val client: OkHttpClient = setupClient()
    private val ensResolver = EnsResolver(web3j)
    private val avatarEnsResolver = EnsResolver(web3j)
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val resolvables: Map<String, Resolvable> =
        hashMapOf(
            ".bit" to DASResolver(client),
            ".crypto" to UnstoppableDomainsResolver(client, chainId),
            ".zil" to UnstoppableDomainsResolver(client, chainId),
            ".wallet" to UnstoppableDomainsResolver(client, chainId),
            ".x" to UnstoppableDomainsResolver(client, chainId),
            ".nft" to UnstoppableDomainsResolver(client, chainId),
            ".888" to UnstoppableDomainsResolver(client, chainId),
            ".dao" to UnstoppableDomainsResolver(client, chainId),
            ".blockchain" to UnstoppableDomainsResolver(client, chainId),
            ".bitcoin" to UnstoppableDomainsResolver(client, chainId),
        )

    /**
     * 兼容旧接口：使用 RxJava Single 返回 ENS 反向解析结果。
     */
    fun reverseResolveEns(address: String): Single<String> =
        singleFrom { reverseResolveEnsSuspend(address) }.onErrorReturnItem("")

    /**
     * 兼容旧接口：使用 RxJava Single 获取 ENS 头像地址。
     */
    fun getENSUrl(ensName: String): Single<String> =
        singleFrom { getENSUrlSuspend(ensName) }.onErrorReturnItem("")

    /**
     * 兼容旧接口：转换定位器（ipfs/eip155 等）为实际 URL。
     */
    fun convertLocator(locator: String): Single<String> =
        singleFrom { convertLocatorSuspend(locator) }.onErrorReturnItem("")

    /**
     * 兼容旧接口：解析 ENS 名称对应的地址。
     */
    fun resolveENSAddress(ensName: String): Single<String> =
        singleFrom { resolveENSAddressSuspend(ensName).toString() }.onErrorReturnItem(EnsResolver.CANCELLED_REQUEST)

    /**
     * 协程接口：根据地址尝试获取绑定的 ENS 名称。
     */
    suspend fun reverseResolveEnsSuspend(address: String): String =
        withContext(Dispatchers.IO) {
            var ensName = ""
            try {
                try {
                    ensName = ensResolver.reverseResolve(address)
                    if (!TextUtils.isEmpty(ensName)) {
                        val resolved = resolveSuspend(ensName)
                        if (resolved != EnsResolver.CANCELLED_REQUEST &&
                            !resolved.equals(address, ignoreCase = true)
                        ) {
                            ensName = ""
                        }
                    }
                } catch (e: UnableToResolveENS) {
                    ensName = fetchPreviouslyUsedENS(address)
                } catch (_: EnsResolutionException) {
                    // ENS 名称无效时允许回退历史记录
                }
            } catch (t: Throwable) {
                Timber.e(t)
                ensName = ""
            }
            ensName
        }

    /**
     * 协程接口：根据 ENS 名称获取头像链接或其他 URL。
     */
    suspend fun getENSUrlSuspend(ensName: String): String =
        withContext(Dispatchers.IO) {
            val locator =
                when {
                    TextUtils.isEmpty(ensName) -> ""
                    Utils.isAddressValid(ensName) -> resolveAvatarFromAddressSuspend(ensName)
                    else -> resolveAvatarSuspend(ensName)
                }
            convertLocatorSuspend(locator?:"")
        }

    /**
     * 协程接口：将定位器转换为可用 URL。
     */
    suspend fun convertLocatorSuspend(locator: String): String =
        withContext(Dispatchers.IO) {
            if (locator.isEmpty()) {
                ""
            } else {
                when (getLocatorType(locator)) {
                    LocatorType.EIP155 -> getEip155Url(locator)
                    LocatorType.IPFS -> Utils.parseIPFS(locator)
                    LocatorType.HTTPS -> locator
                    LocatorType.UNKNOWN -> ""
                }
            }
        }

    /**
     * 协程接口：解析 ENS 名称对应的地址。
     */
    suspend fun resolveENSAddressSuspend(ensName: String): String? =
        withContext(Dispatchers.IO) {
            if (!EnsResolver.isValidEnsName(ensName)) {
                ""
            } else {
                try {
                    val address = resolveSuspend(ensName)
                    if (address == EnsResolver.CANCELLED_REQUEST) "" else address
                } catch (t: Throwable) {
                    Timber.d(t, "Resolve ENS failed for %s", ensName)
                    ""
                }
            }
        }

    /**
     * 协程接口：解析 ENS 名称对应的头像。
     */
    suspend fun resolveAvatarSuspend(ensName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                AvatarResolver(avatarEnsResolver).resolve(ensName)
            } catch (t: Throwable) {
                Timber.e(t)
                ""
            }
        }

    /**
     * 协程接口：根据地址解析头像。
     */
    suspend fun resolveAvatarFromAddressSuspend(address: String): String? =
        withContext(Dispatchers.IO) {
            if (!Utils.isAddressValid(address)) {
                ""
            } else {
                try {
                    val ensName = ensResolver.reverseResolve(address)
                    resolveAvatarSuspend(ensName)
                } catch (t: Throwable) {
                    Timber.e(t)
                    ""
                }
            }
        }

    /**
     * 同步接口：解析 ENS 名称并返回地址。
     */
    fun resolve(ensName: String): String? {
        if (ensName.isEmpty()) {
            return ""
        }

        val resolver = resolvables[suffixOf(ensName)] ?: run {
            ensResolver.cancelCurrentResolve()
            ensResolver
        }

        return try {
            resolver.resolve(ensName)
        } catch (t: Throwable) {
            Timber.e(t)
            ""
        }
    }

    /**
     * 同步接口：解析 ENS 名称对应头像。
     */
    fun resolveAvatar(ensName: String): String? =
        try {
            AvatarResolver(avatarEnsResolver).resolve(ensName)
        } catch (t: Throwable) {
            Timber.e(t)
            ""
        }

    /**
     * 同步接口：根据地址解析头像。
     */
    fun resolveAvatarFromAddress(address: String): String? =
        if (!Utils.isAddressValid(address)) {
            ""
        } else {
            try {
                val ensName = ensResolver.reverseResolve(address)
                resolveAvatar(ensName)
            } catch (t: Throwable) {
                Timber.e(t)
                ""
            }
        }

    /**
     * 查询历史记录中缓存的 ENS 名称。
     */
    fun checkENSHistoryForAddress(address: String): String {
        if (context == null) return ""

        return try {
            val historyJson =
                PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getString(C.ENS_HISTORY_PAIR, "")
            if (historyJson.isNullOrEmpty()) {
                ""
            } else {
                val history =
                    Gson().fromJson<HashMap<String, String>>(
                        historyJson,
                        object : TypeToken<HashMap<String, String>>() {}.type,
                    )
                history[address.lowercase(Locale.ENGLISH)] ?: ""
            }
        } catch (t: Throwable) {
            Timber.e(t)
            ""
        }
    }

    /**
     * 协程内部使用：统一进行 ENS 解析并返回地址。
     */
    private suspend fun resolveSuspend(ensName: String): String? =
        withContext(Dispatchers.IO) {
            if (ensName.isEmpty()) {
                ""
            } else {
                val resolver = resolvables[suffixOf(ensName)] ?: run {
                    ensResolver.cancelCurrentResolve()
                    ensResolver
                }

                try {
                    resolver.resolve(ensName)
                } catch (t: Throwable) {
                    Timber.e(t)
                    ""
                }
            }
        }

    /**
     * 读取历史记录，以便在 ENS 解析失败时回退。
     */
    private suspend fun fetchPreviouslyUsedENS(address: String): String {
        if (context == null) return ""

        return withContext(Dispatchers.IO) {
            try {
                val historyJson =
                    PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getString(C.ENS_HISTORY_PAIR, "")

                if (historyJson.isNullOrEmpty()) {
                    ""
                } else {
                    val history =
                        Gson().fromJson<HashMap<String, String>>(
                            historyJson,
                            object : TypeToken<HashMap<String, String>>() {}.type,
                        )

                    val cachedName = history[address.lowercase(Locale.ENGLISH)]
                    if (cachedName.isNullOrEmpty()) {
                        ""
                    } else {
                        val resolved = resolveENSAddressSuspend(cachedName)
                        if (resolved.equals(address, ignoreCase = true)) cachedName else ""
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t)
                ""
            }
        }
    }

    /**
     * 解析 eip155 协议的定位器并返回图片地址。
     */
    private fun getEip155Url(locator: String): String {
        val findKey = Pattern.compile("(eip155:)([0-9]+)(\\/)([0-9a-zA-Z]+)(:)(0?x?[0-9a-fA-F]{40})(\\/)([0-9]+)")
        val matcher = findKey.matcher(locator)

        return try {
            if (matcher.find()) {
                val chain = matcher.group(2)?.toLong() ?: return ""
                val tokenAddress = Numeric.prependHexPrefix(matcher.group(6) ?: return "")
                val tokenId = matcher.group(8) ?: return ""

                val asset = OpenSeaService().fetchAsset(chain, tokenAddress, tokenId)
                val nftAsset = NFTAsset(asset)
                var url = nftAsset.getThumbnail()
                if (!url.isNullOrEmpty() && url.endsWith(".svg")) {
                    url = nftAsset.getImage().takeUnless { it.isNullOrEmpty() } ?: url
                }
                url ?: ""
            } else {
                ""
            }
        } catch (t: Throwable) {
            Timber.e(t)
            ""
        }
    }

    /**
     * 根据定位器内容判断类型。
     */
    private fun getLocatorType(locator: String): LocatorType {
        val parts = locator.split(":")
        return if (parts.size > 1) {
            when (parts[0]) {
                "eip155" -> LocatorType.EIP155
                "ipfs" -> LocatorType.IPFS
                "https" -> LocatorType.HTTPS
                else -> LocatorType.UNKNOWN
            }
        } else {
            LocatorType.UNKNOWN
        }
    }

    /**
     * 获取 ENS 名称的后缀（例如 .eth）。
     */
    private fun suffixOf(ensName: String): String =
        ensName.substring(ensName.lastIndexOf("."))

    /**
     * 构建 OkHttp 客户端，用于第三方域名解析。
     */
    private fun setupClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .writeTimeout(7, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    /**
     * 将协程代码包装成 RxJava Single，兼容旧调用方。
     */
    private fun <T : Any> singleFrom(block: suspend () -> T): Single<T> =
        Single.create { emitter ->
            val job =
                bridgeScope.launch {
                    try {
                        emitter.onSuccess(block())
                    } catch (throwable: Throwable) {
                        emitter.tryOnError(throwable)
                    }
                }
            emitter.setCancellable { job.cancel() }
        }

    /**
     * 定位器类型，区分不同协议地址。
     */
    private enum class LocatorType {
        EIP155,
        IPFS,
        HTTPS,
        UNKNOWN,
    }
}
