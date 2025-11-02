package com.alphawallet.app.interact

import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.service.AssetDefinitionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

/**
 * FetchTokensInteract - 代币获取交互类
 *
 * 负责处理代币相关的异步操作，使用协程替代RxJava。
 * 主要功能包括：
 * 1. 代币余额更新
 * 2. 合约响应获取
 * 3. 代币元数据获取
 * 4. 以太坊价格获取
 * 5. 代币赎回状态检查
 *
 * @param tokenRepository 代币仓库
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class FetchTokensInteract(
    private val tokenRepository: TokenRepositoryType,
) {
    /**
     * 获取合约响应
     *
     * @param address 合约地址
     * @param chainId 链ID
     * @param method 方法名
     * @return 合约定位器
     */
    suspend fun getContractResponse(
        address: String,
        chainId: Long,
        method: String,
    ): ContractLocator? =
        withContext(Dispatchers.IO) {
            tokenRepository.getTokenResponse(address, chainId, method)
        }

    /**
     * 更新默认余额
     *
     * @param token 代币对象
     * @param wallet 钱包信息
     * @return 更新后的代币对象
     */
    suspend fun updateDefaultBalance(
        token: Token,
        wallet: Wallet,
    ): Token? =
        withContext(Dispatchers.IO) {
            tokenRepository.fetchActiveTokenBalance(wallet.address, token)
        }

    /**
     * 更新余额
     *
     * @param address 钱包地址
     * @param token 代币对象
     * @return 更新后的代币对象
     */
    suspend fun updateBalance(
        address: String,
        token: Token?,
    ): Token? =
        withContext(Dispatchers.IO) {
            if (token == null) {
                Token(TokenInfo(), BigDecimal.ZERO, 0, "", ContractType.NOT_SET)
            } else {
                tokenRepository.fetchActiveTokenBalance(address, token)
            }
        }

    /**
     * 获取以太坊价格
     *
     * @param chainId 链ID
     * @return 代币价格信息
     */
    suspend fun getEthereumTicker(chainId: Long): TokenTicker? =
        withContext(Dispatchers.IO) {
            tokenRepository.getEthTicker(chainId)
        }

    /**
     * 检查赎回状态
     *
     * @param token 代币对象
     * @param tickets 票据列表
     * @return 是否已赎回
     */
    suspend fun checkRedeemed(
        token: Token?,
        tickets: List<BigInteger>?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (token == null || tickets.isNullOrEmpty()) {
                return@withContext true // 早期返回无效输入
            }
            val tokenId = tickets[0]
            tokenRepository.fetchIsRedeemed(token, tokenId) ?: false
        }

    /**
     * 获取代币元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @param svs 资产定义服务
     * @return 代币卡片元数据数组
     */
    suspend fun fetchTokenMetas(
        wallet: Wallet,
        networkFilters: List<Long>,
        svs: AssetDefinitionService,
    ): Array<TokenCardMeta?>? =
        withContext(Dispatchers.IO) {
            tokenRepository.fetchTokenMetas(wallet, networkFilters, svs)
        }

    /**
     * 搜索代币元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @param searchTerm 搜索词
     * @return 代币卡片元数据数组
     */
    suspend fun searchTokenMetas(
        wallet: Wallet,
        networkFilters: List<Long>,
        searchTerm: String,
    ): Array<TokenCardMeta?>? =
        withContext(Dispatchers.IO) {
            tokenRepository.fetchAllTokenMetas(wallet, networkFilters, searchTerm)
        }
}
