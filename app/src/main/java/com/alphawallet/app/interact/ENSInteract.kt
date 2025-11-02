package com.alphawallet.app.interact

import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.ui.widget.entity.ENSHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

/**
 * 以太坊域名服务(ENS)交互类 - Kotlin协程版本
 *
 * 负责处理ENS相关的业务逻辑，包括：
 * - 检查ENS地址
 * - 验证返回的地址
 *
 * 技术特点：
 * - 使用Kotlin协程替代RxJava，提供更好的异步操作支持
 * - 支持依赖注入
 * - 统一的错误处理机制
 * - 线程安全的操作
 *
 * @param tokenRepository 代币仓库接口，用于执行ENS解析操作
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class ENSInteract @Inject constructor(
    private val tokenRepository: TokenRepositoryType
) {
    companion object {
        private const val TAG = "ENSInteract"
        private const val ZERO_ADDRESS = "0"
    }

    /**
     * 检查ENS地址
     *
     * 该方法执行以下操作：
     * 1. 验证输入的名称是否可能是ENS名称
     * 2. 解析ENS名称获取对应的以太坊地址
     * 3. 验证返回的地址是否有效
     *
     * @param chainId 区块链ID
     * @param name 要检查的ENS名称
     * @return 解析后的以太坊地址，如果解析失败则返回"0"
     */
    suspend fun checkENSAddress(chainId: Long, name: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始检查ENS地址，名称: $name，链ID: $chainId")
                
                // 1. 验证输入的名称是否可能是ENS名称
                if (!ENSHandler.canBeENSName(name)) {
                    Timber.d("$TAG: 输入的名称不是有效的ENS名称: $name")
                    return@withContext ZERO_ADDRESS
                }
                
                // 2. 解析ENS名称获取对应的以太坊地址
                val resolvedAddress = tokenRepository.resolveENS(chainId, name)
                
                // 3. 验证返回的地址是否有效
                val result = checkAddress(resolvedAddress)
                Timber.d("$TAG: ENS地址检查完成，结果: $result")
                
                result
            } catch (e: Exception) {
                Timber.e(e, "$TAG: ENS地址检查失败")
                ZERO_ADDRESS
            }
        }
    }

    /**
     * 检查地址是否有效
     *
     * 验证返回的地址是否为零地址。
     *
     * @param returnedAddress 要检查的地址
     * @return 如果地址有效则返回原地址，否则返回"0"
     */
    private fun checkAddress(returnedAddress: String?): String {
        if (returnedAddress == null) return ZERO_ADDRESS
        
        return try {
            val test = Numeric.toBigInt(returnedAddress)
            if (test != BigInteger.ZERO) {
                returnedAddress
            } else {
                ZERO_ADDRESS
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 地址检查失败: $returnedAddress")
            ZERO_ADDRESS
        }
    }
}
