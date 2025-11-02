package com.alphawallet.app.interact

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.MessagePair
import com.alphawallet.app.entity.SignaturePair
import com.alphawallet.app.repository.WalletRepositoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

/**
 * 生成签名消息交互类
 *
 * 负责根据门票(Ticket)编号、合约地址和合约类型生成签名所需的明文消息。
 * 将原本基于 RxJava Single 的异步接口转换为 Kotlin 协程 suspend 函数。
 *
 * 主要功能：
 * - 生成 721/1155 等类型门票的选择串(selection)
 * - 按合约类型确定时间片(divisor)并计算当前时间片索引
 * - 生成用于签名的明文消息 plainMessage = "$selection,$minsTime,$contract"
 *
 * 技术特点：
 * - 依赖注入：使用 Hilt 注入
 * - 协程：使用 withContext(Dispatchers.Default) 处理 CPU 计算
 * - 完整的中文注释与错误处理
 */
class SignatureGenerateInteract @Inject constructor(
    @Suppress("unused") private val walletRepository: WalletRepositoryType // 预留注入，后续可扩展为从仓库读取配置
) {
    /**
     * 生成签名消息
     *
     * @param tickets 门票编号列表
     * @param contract 合约地址，需小写统一格式
     * @param contractType 合约类型，决定 selection 生成方式与时间片 divisor
     * @return MessagePair(selection, plainMessage)
     * @throws IllegalArgumentException 当参数非法时抛出
     */
    suspend fun getMessage(
        tickets: List<BigInteger>,
        contract: String,
        contractType: ContractType,
    ): MessagePair = withContext(Dispatchers.Default) {
        require(tickets.isNotEmpty()) { "门票列表不能为空" }
        require(contract.isNotBlank()) { "合约地址不能为空" }

        val (selectionStr, divisor) = if (contractType == ContractType.ERC721_TICKET) {
            SignaturePair.generateSelection721Tickets(tickets) to (10 * 60 * 1000L) // 10 分钟粒度
        } else {
            SignaturePair.generateSelection(tickets) to (30 * 1000L) // 30 秒粒度
        }

        val currentTime = System.currentTimeMillis()
        val minsTime = (currentTime / divisor).toInt()

        val plainMessage = "$selectionStr,$minsTime,${contract.lowercase()}"
        Timber.tag("SIG").d(plainMessage)
        MessagePair(selectionStr, plainMessage)
    }
}
