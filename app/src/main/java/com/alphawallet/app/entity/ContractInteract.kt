package com.alphawallet.app.entity

import android.text.TextUtils
import com.alphawallet.app.C
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.service.IPFSService
import com.alphawallet.app.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * 合约交互类
 *
 * 负责与区块链智能合约进行交互，主要功能包括：
 * 1. 获取合约元数据和脚本URI
 * 2. 获取NFT代币的元数据信息
 * 3. 调用智能合约函数
 * 4. 处理IPFS内容获取
 * 5. 解析合约返回结果
 *
 * 技术特点：
 * - 使用 Kotlin 协程替代 RxJava 进行异步操作
 * - 提供完整的错误处理和异常管理
 * - 支持多种合约标准（ERC721、ERC1155等）
 * - 集成IPFS服务进行元数据获取
 * - 优化网络请求和缓存机制
 *
 * @param token 关联的代币对象
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class ContractInteract(
    /** 关联的代币对象 */
    private val token: Token
) {
    
    companion object {
        /** IPFS服务客户端（静态单例） */
        @Volatile
        private var client: IPFSService? = null
        
        /**
         * 设置IPFS客户端
         * 使用双重检查锁定模式确保线程安全
         */
        @Synchronized
        private fun setupClient() {
            if (client == null) {
                client = IPFSService(
                    OkHttpClient.Builder()
                        .connectTimeout((C.CONNECT_TIMEOUT * 2).toLong(), TimeUnit.SECONDS)
                        .readTimeout((C.READ_TIMEOUT * 2).toLong(), TimeUnit.SECONDS)
                        .writeTimeout((C.WRITE_TIMEOUT * 2).toLong(), TimeUnit.SECONDS)
                        .retryOnConnectionFailure(false)
                        .build()
                )
            }
        }
    }
    
    // ==================== 公共异步方法 ====================
    
    /**
     * 获取脚本文件URI列表（协程版本）
     *
     * 异步调用智能合约获取脚本URI，支持返回多个URI的情况
     *
     * @return 脚本URI列表
     * @throws Exception 网络错误或合约调用失败时抛出异常
     */
    suspend fun getScriptFileURIAsync(): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = callSmartContractFuncAdaptiveArray(
                chainId = token.tokenInfo.chainId,
                function = getScriptURI(),
                contractAddress = token.getAddress(),
                walletAddress = token.getWallet()
            )
            result ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "获取脚本文件URI时发生错误")
            emptyList()
        }
    }
    
    /**
     * 获取合约URI结果（协程版本）
     *
     * 异步调用智能合约获取合约URI，并加载对应的元数据
     *
     * @return 合约元数据JSON字符串
     * @throws Exception 网络错误或合约调用失败时抛出异常
     */
    suspend fun getContractURIResultAsync(): String = withContext(Dispatchers.IO) {
        try {
            val tokenURI = callSmartContractFunction(
                chainId = token.tokenInfo.chainId,
                function = getContractURI(),
                contractAddress = token.getAddress(),
                walletAddress = token.getWallet()
            )
            loadMetaDataAsync(tokenURI ?: "")
        } catch (e: Exception) {
            Timber.e(e, "获取合约URI结果时发生错误")
            ""
        }
    }
    
    /**
     * 获取代币元数据（协程版本）
     *
     * 根据tokenId获取NFT代币的完整元数据信息
     *
     * @param tokenId 代币ID
     * @return NFT资产对象，包含元数据信息
     * @throws Exception 网络错误或合约调用失败时抛出异常
     */
    suspend fun fetchTokenMetadataAsync(tokenId: BigInteger): NFTAsset = withContext(Dispatchers.IO) {
        try {
            // 1. 尝试标准的tokenURI方法
            var responseValue = callSmartContractFunction(
                chainId = token.tokenInfo.chainId,
                function = getTokenURI(tokenId),
                contractAddress = token.getAddress(),
                walletAddress = token.getWallet()
            )
            
            // 2. 如果标准方法失败，尝试uri方法（ERC1155标准）
            if (TextUtils.isEmpty(responseValue)) {
                responseValue = callSmartContractFunction(
                    chainId = token.tokenInfo.chainId,
                    function = getTokenURI2(tokenId),
                    contractAddress = token.getAddress(),
                    walletAddress = token.getWallet()
                )
            }
            
            // 3. 解析响应值，处理ERC1155的{id}占位符
            val parsedValue = Utils.parseResponseValue(responseValue ?: "", tokenId)
            
            // 4. 加载元数据
            val metaData = loadMetaDataAsync(parsedValue)
            
            // 5. 创建NFT资产对象
            if (!TextUtils.isEmpty(metaData)) {
                NFTAsset(metaData)
            } else {
                NFTAsset()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取代币元数据时发生错误: tokenId=$tokenId")
            NFTAsset()
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 加载元数据（协程版本）
     *
     * 从给定的URI加载元数据，支持直接JSON和IPFS链接
     *
     * @param tokenURI 代币URI
     * @return 元数据JSON字符串
     */
    private suspend fun loadMetaDataAsync(tokenURI: String): String = withContext(Dispatchers.IO) {
        when {
            TextUtils.isEmpty(tokenURI) -> {
                Timber.w("代币URI为空")
                ""
            }
            Utils.isJson(tokenURI) -> {
                Timber.d("检测到直接JSON元数据")
                tokenURI
            }
            else -> {
                try {
                    // 设置IPFS客户端
                    setupClient()
                    
                    // 从IPFS或HTTP获取内容
                    val content = client?.getContent(tokenURI) ?: ""
                    Timber.d("成功从URI获取内容: ${tokenURI.take(50)}...")
                    content
                } catch (e: Exception) {
                    Timber.e(e, "从URI加载元数据失败: $tokenURI")
                    ""
                }
            }
        }
    }
    
    // ==================== 智能合约函数定义 ====================
    
    /**
     * 创建tokenURI函数（标准ERC721方法）
     *
     * @param tokenId 代币ID
     * @return Web3j函数对象
     */
    private fun getTokenURI(tokenId: BigInteger): Function {
        return Function(
            "tokenURI",
            listOf(Uint256(tokenId)),
            listOf(object : TypeReference<Utf8String>() {})
        )
    }
    
    /**
     * 创建uri函数（ERC1155标准方法）
     *
     * @param tokenId 代币ID
     * @return Web3j函数对象
     */
    private fun getTokenURI2(tokenId: BigInteger): Function {
        return Function(
            "uri",
            listOf(Uint256(tokenId)),
            listOf(object : TypeReference<Utf8String>() {})
        )
    }
    
    /**
     * 创建scriptURI函数
     *
     * @return Web3j函数对象
     */
    private fun getScriptURI(): Function {
        return Function(
            "scriptURI",
            emptyList(),
            listOf(object : TypeReference<Utf8String>() {})
        )
    }
    
    /**
     * 创建contractURI函数
     *
     * @return Web3j函数对象
     */
    private fun getContractURI(): Function {
        return Function(
            "contractURI",
            emptyList(),
            listOf(object : TypeReference<Utf8String>() {})
        )
    }
    
    // ==================== 智能合约调用方法 ====================
    
    /**
     * 调用智能合约函数
     *
     * 执行智能合约调用并返回单个字符串结果
     *
     * @param chainId 链ID
     * @param function 要调用的函数
     * @param contractAddress 合约地址
     * @param walletAddress 钱包地址
     * @return 合约调用结果，失败时返回null
     */
    private fun callSmartContractFunction(
        chainId: Long,
        function: Function,
        contractAddress: String,
        walletAddress: String
    ): String? {
        return try {
            val web3j = TokenRepository.getWeb3jService(chainId)
            val encodedFunction = FunctionEncoder.encode(function)
            
            val transaction = Transaction.createEthCallTransaction(
                walletAddress,
                contractAddress,
                encodedFunction
            )
            
            val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
            
            if (response.hasError()) {
                Timber.w("智能合约调用返回错误: ${response.error.message}")
                null
            } else {
                val result = response.value
                if (!TextUtils.isEmpty(result) && result != "0x") {
                    // 解码返回值
                    val decodedValues = FunctionReturnDecoder.decode(result, function.outputParameters)
                    if (decodedValues.isNotEmpty()) {
                        val decodedValue = decodedValues[0]
                        when (decodedValue) {
                            is Utf8String -> decodedValue.value
                            else -> decodedValue.value.toString()
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "网络连接错误，调用智能合约失败")
            null
        } catch (e: Exception) {
            Timber.e(e, "调用智能合约时发生未知错误")
            null
        }
    }
    
    /**
     * 调用智能合约函数（自适应数组返回）
     *
     * 执行智能合约调用并返回字符串列表结果，适用于返回数组的函数
     *
     * @param chainId 链ID
     * @param function 要调用的函数
     * @param contractAddress 合约地址
     * @param walletAddress 钱包地址
     * @return 合约调用结果列表，失败时返回null
     */
    private fun callSmartContractFuncAdaptiveArray(
        chainId: Long,
        function: Function,
        contractAddress: String,
        walletAddress: String
    ): List<String>? {
        return try {
            val web3j = TokenRepository.getWeb3jService(chainId)
            val encodedFunction = FunctionEncoder.encode(function)
            
            val transaction = Transaction.createEthCallTransaction(
                walletAddress,
                contractAddress,
                encodedFunction
            )
            
            val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
            
            if (response.hasError()) {
                Timber.w("智能合约调用返回错误: ${response.error.message}")
                null
            } else {
                val result = response.value
                if (!TextUtils.isEmpty(result) && result != "0x") {
                    // 解码返回值
                    val decodedValues = FunctionReturnDecoder.decode(result, function.outputParameters)
                    if (decodedValues.isNotEmpty()) {
                        val decodedValue = decodedValues[0]
                        when (decodedValue) {
                            is Utf8String -> listOf(decodedValue.value)
                            else -> {
                                // 尝试解析为数组
                                val valueStr = decodedValue.value.toString()
                                if (valueStr.contains(",")) {
                                    valueStr.split(",").map { it.trim() }
                                } else {
                                    listOf(valueStr)
                                }
                            }
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "网络连接错误，调用智能合约失败")
            null
        } catch (e: Exception) {
            Timber.e(e, "调用智能合约时发生未知错误")
            null
        }
    }
}
