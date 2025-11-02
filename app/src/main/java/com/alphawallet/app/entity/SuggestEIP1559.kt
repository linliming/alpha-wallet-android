package com.alphawallet.app.entity

/*
Economical Fee Oracle
Uses code from the KEthereum library: https://github.com/komputing/KEthereum
EIP-1559 transaction fee parameter suggestion algorithm based on the eth_feeHistory API
based on: https://github.com/zsfelfoldi/feehistory
*/

import com.alphawallet.app.service.GasService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.utils.Numeric
import java.lang.Long.parseLong
import java.math.BigDecimal
import java.math.BigInteger
import java.math.BigInteger.ZERO
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor

/**
 * EIP-1559 费用建议算法常量
 */
private object EIP1559Constants {
    const val SAMPLE_MIN_PERCENTILE = 10          // 指数加权 baseFee 历史的采样百分位范围
    const val SAMPLE_MAX_PERCENTILE = 30

    const val REWARD_PERCENTILE = 10             // 从每个单独区块中选择的有效奖励值
    const val REWARD_BLOCK_PERCENTILE = 40        // 从排序的单独区块奖励百分位中选择的建议优先费用

    const val MAX_TIME_FACTOR = 15                // 返回建议列表中最高时间因子索引
    const val EXTRA_PRIORITY_FEE_RATIO = 0.25      // 在预期 baseFee 上升时提供的额外优先费用
    const val FALLBACK_PRIORITY_FEE = 2000000000L // 当没有最近交易时提供的优先费用
    const val MIN_PRIORITY_FEE = 100000000L       // 最小优先费用（Wei），0.1 Gwei
}

/**
 * EIP-1559 费用建议算法
 *
 * 基于 eth_feeHistory API 的 EIP-1559 交易费用参数建议算法。
 * 该算法使用经济费用预言机方法，基于历史费用数据提供智能的费用建议。
 *
 * 主要功能：
 * - 基于历史费用数据计算建议的优先费用
 * - 预测最小基础费用
 * - 处理费用一致性检查
 * - 提供多种时间窗口的费用建议
 *
 * 技术特点：
 * - 使用协程进行异步操作
 * - 基于指数加权历史数据
 * - 支持费用一致性检测
 * - 提供回退机制
 *
 * @param gasService Gas 服务实例
 * @param feeHistory 费用历史数据
 * @return 包含不同时间因子费用建议的映射
 *
 * @author AlphaWallet Team
 * @since 2024
 */
suspend fun suggestEIP1559(gasService: GasService, feeHistory: FeeHistory): Map<Int, EIP1559FeeOracleResult> =
    withContext(Dispatchers.Default) {
        try {
            val firstBlock = parseLong(feeHistory.oldestBlock?.removePrefix("0x") ?: "", 16)
            val priorityFee = suggestPriorityFee(firstBlock, feeHistory, gasService)
            calculateResult(priorityFee, feeHistory)
        } catch (e: Exception) {
            throw Exception("EIP-1559 费用建议计算失败: ${e.message}", e)
        }
    }

/**
 * 计算 EIP-1559 费用结果
 *
 * 基于优先费用和费用历史数据计算完整的 EIP-1559 费用建议。
 * 包括基础费用预测、优先费用调整和一致性检查。
 *
 * @param priorityFee 建议的优先费用
 * @param feeHistory 费用历史数据
 * @return 包含不同时间因子费用建议的映射
 */
private fun calculateResult(priorityFee: BigInteger, feeHistory: FeeHistory): Map<Int, EIP1559FeeOracleResult> {
    // 转换 baseFee 数据
    val baseFee: Array<BigInteger> = feeHistory.baseFeePerGas.map {
        Numeric.toBigInt(it)
    }.toTypedArray()

    var usePriorityFee = priorityFee
    var consistentBaseFee = false

    // 检查费用一致性
    if (checkConsistentFees(baseFee)) {
        consistentBaseFee = true
        baseFee[baseFee.size - 1] = baseFee[0]
        if (priorityFee.toLong() == EIP1559Constants.FALLBACK_PRIORITY_FEE && priorityFee > baseFee[0]) {
            usePriorityFee = baseFee[0]
        }
    } else {
        // 预测下一个区块的基础费用
        baseFee[baseFee.size - 1] = (baseFee[baseFee.size - 1].toBigDecimal() * BigDecimal(9 / 8.0)).toBigInteger()
    }

    // 处理高 Gas 使用率区块
    for (i in (feeHistory.gasUsedRatio.size - 1) downTo 0) {
        if (feeHistory.gasUsedRatio[i] > 0.9) {
            baseFee[i] = baseFee[i + 1]
        }
    }

    // 按基础费用排序
    val order = (0..feeHistory.gasUsedRatio.size).map { it }.sortedBy { baseFee[it] }

    var maxBaseFee = ZERO
    val result = mutableMapOf<Int, EIP1559FeeOracleResult>()
    
    // 为不同时间因子计算费用建议
    for (timeFactor in EIP1559Constants.MAX_TIME_FACTOR downTo 0) {
        var bf: BigInteger
        bf = if (timeFactor < 1e-6) {
            baseFee.last()
        } else {
            predictMinBaseFee(baseFee, order, timeFactor.toDouble(), consistentBaseFee)
        }
        
        var t = BigDecimal(usePriorityFee)
        if (bf > maxBaseFee) {
            maxBaseFee = bf
        } else {
            // 如果较窄的时间窗口产生较低的基础费用建议，我们可能处于价格下跌中
            // 在这种情况下，低优先费用不能保证被包含；我们使用较高的基础费用建议
            // 并提供额外的优先费用以增加在基础费用下跌中被包含的机会
            t += BigDecimal(maxBaseFee - bf) * BigDecimal.valueOf(EIP1559Constants.EXTRA_PRIORITY_FEE_RATIO)
            bf = maxBaseFee
        }
        
        result[timeFactor] = EIP1559FeeOracleResult(bf + t.toBigInteger(), t.toBigInteger(), bf)
    }

    return result
}

/**
 * 检查费用一致性
 *
 * 检查基础费用数组中的所有值是否一致。
 * 如果所有值都相同，说明网络费用稳定。
 *
 * @param baseFee 基础费用数组
 * @return 如果费用一致返回 true，否则返回 false
 */
fun checkConsistentFees(baseFee: Array<BigInteger>): Boolean {
    if (baseFee.isEmpty()) {
        return false
    }

    val firstVal: BigInteger = baseFee[0]
    return baseFee.all { it == firstVal }
}

/**
 * 预测最小基础费用
 *
 * 基于历史基础费用数据预测最小基础费用。
 * 使用指数加权算法和采样曲线进行计算。
 *
 * @param baseFee 基础费用数组
 * @param order 排序后的索引列表
 * @param timeFactor 时间因子
 * @param consistentBaseFee 费用是否一致
 * @return 预测的最小基础费用
 */
internal fun predictMinBaseFee(
    baseFee: Array<BigInteger>, 
    order: List<Int>, 
    timeFactor: Double, 
    consistentBaseFee: Boolean
): BigInteger {
    val pendingWeight = (1 - exp(-1 / timeFactor)) / (1 - exp(-baseFee.size / timeFactor))
    var sumWeight = 0.0
    var result = ZERO
    var samplingCurveLast = 0.0
    
    if (consistentBaseFee) {
        result = baseFee[0]
    } else {
        order.indices.forEach { i ->
            sumWeight += pendingWeight * exp((order[i] - baseFee.size + 1) / timeFactor)
            val samplingCurveValue = samplingCurve(sumWeight * 100.0)
            result += ((samplingCurveValue - samplingCurveLast) * baseFee[order[i]].toDouble()).toBigDecimal().toBigInteger()
            if (samplingCurveValue >= 1) {
                return result
            }
            samplingCurveLast = samplingCurveValue
        }
    }
    return result
}

/**
 * 建议优先费用（协程版本）
 *
 * 基于费用历史数据建议优先费用。
 * 分析最近的区块数据来确定合适的优先费用。
 *
 * @param firstBlock 第一个区块号
 * @param feeHistory 费用历史数据
 * @param gasService Gas 服务实例
 * @return 建议的优先费用
 */
internal suspend fun suggestPriorityFee(
    firstBlock: Long, 
    feeHistory: FeeHistory, 
    gasService: GasService
): BigInteger = withContext(Dispatchers.Default) {
    val gasUsedRatio = feeHistory.gasUsedRatio
    var ptr = gasUsedRatio.size - 1
    var needBlocks = 5
    val rewards = mutableListOf<BigInteger>()
    
    while (needBlocks > 0 && ptr >= 0) {
        val blockCount = maxBlockCount(gasUsedRatio, ptr, needBlocks)
        if (blockCount > 0) {
            // feeHistory API 调用指定奖励百分位是昂贵的，因此只为几个非满的最近区块请求
            val feeHistoryFetch = gasService.getChainFeeHistory(
                blockCount,
                Numeric.prependHexPrefix((firstBlock + ptr).toString(16)),
                EIP1559Constants.REWARD_PERCENTILE.toString()
            )

            val rewardSize = feeHistoryFetch.reward?.size ?: 0
            for (index in 0 until rewardSize) {
                rewards.add(
                    BigInteger(
                        Numeric.cleanHexPrefix(feeHistoryFetch.reward[index][0].removePrefix("0x")),
                        16
                    )
                )
            }
            if (rewardSize < blockCount) break
            needBlocks -= blockCount
        }
        ptr -= blockCount + 1
    }

    // 检查是否有有效的奖励数据
    val isEmpty = rewards.all { it == ZERO } || rewards.isEmpty()
    
    if (isEmpty) {
        return@withContext calculatePriorityFee(feeHistory) ?: BigInteger.valueOf(EIP1559Constants.MIN_PRIORITY_FEE)
    }
    
    rewards.sort()
    return@withContext rewards[floor((rewards.size - 1) * EIP1559Constants.REWARD_BLOCK_PERCENTILE / 100.0).toInt()]
}

/**
 * 计算优先费用
 *
 * 基于费用历史数据计算优先费用。
 * 如果没有足够的奖励数据，则基于基础费用计算。
 *
 * @param feeHistory 费用历史数据
 * @return 计算出的优先费用，如果没有有效数据返回 null
 */
fun calculatePriorityFee(feeHistory: FeeHistory): BigInteger? {
    var priorityFee = BigInteger.valueOf(EIP1559Constants.MIN_PRIORITY_FEE)

    feeHistory.baseFeePerGas.forEach { element ->
        val elementVal = Numeric.toBigInt(element)
        if (elementVal > priorityFee && elementVal <= BigInteger.valueOf(EIP1559Constants.FALLBACK_PRIORITY_FEE)) {
            priorityFee = elementVal
        }
    }

    return priorityFee
}

/**
 * 计算最大区块数量
 *
 * 计算在给定条件下可以处理的最大区块数量。
 * 考虑 Gas 使用率和其他限制因素。
 *
 * @param gasUsedRatio Gas 使用率数组
 * @param _ptr 当前指针位置
 * @param _needBlocks 需要的区块数量
 * @return 可以处理的最大区块数量
 */
internal fun maxBlockCount(gasUsedRatio: DoubleArray, _ptr: Int, _needBlocks: Int): Int {
    var blockCount = 0
    var ptr = _ptr
    var needBlocks = _needBlocks
    
    while (needBlocks > 0 && ptr >= 0) {
        if (gasUsedRatio[ptr] == 0.0 || gasUsedRatio[ptr] > 0.9) {
            break
        }
        ptr--
        needBlocks--
        blockCount++
    }
    return blockCount
}

/**
 * 采样曲线函数
 *
 * 计算采样曲线值，用于费用预测算法。
 * 使用余弦函数创建平滑的过渡曲线。
 *
 * @param percentile 百分位值
 * @return 采样曲线值
 */
internal fun samplingCurve(percentile: Double): Double = when {
    percentile <= EIP1559Constants.SAMPLE_MIN_PERCENTILE -> 0.0
    percentile >= EIP1559Constants.SAMPLE_MAX_PERCENTILE -> 1.0
    else -> {
        val range = EIP1559Constants.SAMPLE_MAX_PERCENTILE - EIP1559Constants.SAMPLE_MIN_PERCENTILE
        val normalizedPercentile = (percentile - EIP1559Constants.SAMPLE_MIN_PERCENTILE) * 2 * Math.PI / range
        (1 - cos(normalizedPercentile)) / 2
    }
}
