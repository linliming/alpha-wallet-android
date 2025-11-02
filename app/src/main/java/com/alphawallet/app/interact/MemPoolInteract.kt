package com.alphawallet.app.interact

import com.alphawallet.app.entity.TransferFromEventResponse
import com.alphawallet.app.repository.TokenRepositoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 内存池交互类
 *
 * 负责监听和处理内存池中的事件，主要功能包括：
 * - 监听代币燃烧事件
 * - 处理传输事件响应
 *
 * 主要功能：
 * - 代币燃烧监听器，用于监听特定合约地址的代币燃烧事件
 * - 通过 TokenRepository 获取区块链事件数据
 *
 * 技术特点：
 * - 使用 Kotlin 协程处理异步操作
 * - 使用 Dispatchers.IO 线程执行区块链查询
 * - 支持依赖注入
 * - 提供详细的错误处理机制
 *
 * @param tokenRepository 代币仓库接口，用于访问区块链数据
 * @author Created by James on 1/02/2018, Converted to Kotlin coroutines
 */
class MemPoolInteract @Inject constructor(
    private val tokenRepository: TokenRepositoryType
) {

    /**
     * 创建代币燃烧监听器
     *
     * 监听指定合约地址的代币燃烧事件，返回传输事件响应。
     * 该方法会在 IO 线程执行区块链查询操作，避免阻塞主线程。
     *
     * @param contractAddress 要监听的合约地址，不能为空
     * @return TransferFromEventResponse? 传输事件响应对象，如果没有事件或发生错误则返回 null
     *
     * 使用示例：
     * ```kotlin
     * val memPoolInteract = MemPoolInteract(tokenRepository)
     * val eventResponse = memPoolInteract.burnListener("0x1234...")
     * eventResponse?.let { response ->
     *     // 处理燃烧事件响应
     * }
     * ```
     *
     * @throws IllegalArgumentException 当合约地址格式无效时
     * @throws Exception 当区块链查询失败时
     */
    suspend fun burnListener(contractAddress: String): TransferFromEventResponse? {
        return try {
            withContext(Dispatchers.IO) {
                // 验证合约地址
                require(contractAddress.isNotBlank()) {
                    "Contract address cannot be blank"
                }
                
                // 调用代币仓库获取燃烧监听数据
                tokenRepository.burnListenerObservable(contractAddress)
            }
        } catch (e: Exception) {
            // 记录错误并返回 null
            // 这里可以添加日志记录
            println("Error in burnListener for contract $contractAddress: ${e.message}")
            null
        }
    }

    companion object {
        // 常量定义
        private const val TAG = "MemPoolInteract"
    }
}