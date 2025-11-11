package com.alphawallet.app.util

import com.alphawallet.app.entity.tokens.Token
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 余额工具类
 *
 * 提供加密货币余额处理和格式化的实用方法，包括：
 * - Wei、Eth、Gwei之间的转换
 * - 数值格式化和显示
 * - 货币符号处理
 * - 精度控制和舍入
 *
 * 技术特点：
 * - 使用BigDecimal确保高精度计算
 * - 支持多种显示格式和精度
 * - 本地化数字格式支持
 * - 科学计数法和后缀显示
 */
object BalanceUtils {
    
    // 常量定义
    private const val WEI_IN_ETH = "1000000000000000000" // 1 ETH = 10^18 Wei
    private const val SHOW_DECIMAL_PLACES = 5 // 默认显示小数位数
    private const val SLIDING_DECIMAL_PLACES = 2 // 滑动显示小数位数
    private const val ONE_BILLION = 1000000000.0 // 十亿
    
    // 显示阈值和单位转换
    private val displayThresholdEth = Convert.fromWei(
        Convert.toWei(BigDecimal.valueOf(0.01), Convert.Unit.GWEI), 
        Convert.Unit.ETHER
    )
    private val oneGwei = BigDecimal.ONE.divide(
        Convert.Unit.GWEI.weiFactor, 
        18, 
        RoundingMode.DOWN
    )
    
    // 格式化模式常量
    const val MACRO_PATTERN = "###,###,###,###,##0" // 宏观数值格式
    const val CURRENCY_PATTERN = "$MACRO_PATTERN.00" // 货币格式
    
    /**
     * 获取数字格式模式
     * @param precision 精度位数
     * @return 格式化模式字符串
     */
    private fun getDigitalPattern(precision: Int): String {
        return if (precision == 0) MACRO_PATTERN else "$MACRO_PATTERN.${".0".repeat(precision)}"
    }
    
    /**
     * 获取格式化器
     * @param pattern 格式模式
     * @return 配置好的DecimalFormat实例
     */
    private fun getFormat(pattern: String): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.getDefault())
        symbols.decimalSeparator = '.'
        symbols.groupingSeparator = ','
        return DecimalFormat(pattern, symbols)
    }
    
    /**
     * 显示指定精度的数值
     * @param value 原始数值
     * @param decimalReduction 小数位减少量
     * @param numberOfDigits 显示位数
     * @return 格式化后的字符串
     */
    fun displayDigitPrecisionValue(value: BigDecimal, decimalReduction: Int, numberOfDigits: Int): String {
        val correctedValue = value.divide(BigDecimal.valueOf(Math.pow(10.0, decimalReduction.toDouble())), 18, RoundingMode.DOWN)
        
        return if (correctedValue.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            val zeros = calcZeros(correctedValue.toDouble())
            val df = getFormat(getDigitalPattern(numberOfDigits, zeros))
            df.roundingMode = RoundingMode.DOWN
            convertToLocale(df.format(correctedValue))
        }
    }
    
    /**
     * 计算数值前导零的个数
     * @param value 数值
     * @return 前导零个数
     */
    private fun calcZeros(value: Double): Int {
        return if (value < 1.0) {
            val str = value.toString()
            val decimalIndex = str.indexOf('.')
            if (decimalIndex != -1) str.substring(decimalIndex + 1).takeWhile { it == '0' }.length else 0
        } else {
            0
        }
    }
    
    /**
     * 获取固定精度的数字格式模式
     * @param precision 精度
     * @param fixed 固定位数
     * @return 格式化模式字符串
     */
    private fun getDigitalPattern(precision: Int, fixed: Int): String {
        val pattern = StringBuilder(MACRO_PATTERN)
        if (precision > 0) {
            pattern.append(".")
            repeat(fixed) { pattern.append("0") }
            repeat(precision - fixed) { pattern.append("#") }
        }
        return pattern.toString()
    }
    
    /**
     * 转换为本地化格式
     * @param value 数值字符串
     * @return 本地化后的字符串
     */
    private fun convertToLocale(value: String): String {
        return value // 简化实现，可根据需要扩展本地化逻辑
    }
    
    /**
     * Wei转换为Eth
     * @param wei Wei数量
     * @return Eth数量
     */
    fun weiToEth(wei: BigDecimal): BigDecimal {
        return Convert.fromWei(wei, Convert.Unit.ETHER)
    }
    
    /**
     * Eth转换为USD
     * @param priceUsd USD价格
     * @param ethBalance Eth余额
     * @return USD价值字符串
     */
    fun ethToUsd(priceUsd: String, ethBalance: String): String {
        val usdPrice = BigDecimal(priceUsd)
        val ethBal = BigDecimal(ethBalance)
        return usdPrice.multiply(ethBal).toString()
    }
    
    /**
     * Eth转换为Wei
     * @param eth Eth数量字符串
     * @return Wei数量字符串
     */
    fun ethToWei(eth: String): String {
        val ethValue = BigDecimal(eth)
        return Convert.toWei(ethValue, Convert.Unit.ETHER).toBigInteger().toString()
    }
    
    /**
     * 单位转换为E乘数
     * @param value 数值字符串
     * @param decimalPlaces 小数位数
     * @return 转换后的字符串
     */
    fun unitToEMultiplier(value: String, decimalPlaces: BigDecimal): String {
        val val1 = BigDecimal(value)
        return val1.multiply(decimalPlaces).toString()
    }
    
    /**
     * Wei转换为Gwei (BigInteger版本)
     * @param wei Wei数量
     * @return Gwei数量
     */
    fun weiToGweiBI(wei: BigInteger): BigDecimal {
        return Convert.fromWei(BigDecimal(wei), Convert.Unit.GWEI)
    }
    
    /**
     * Wei转换为Gwei字符串
     * @param wei Wei数量
     * @return Gwei字符串
     */
    fun weiToGwei(wei: BigInteger): String {
        return Convert.fromWei(BigDecimal(wei), Convert.Unit.GWEI).toString()
    }
    
    /**
     * Wei转换为Gwei整数
     * @param wei Wei数量
     * @return Gwei整数字符串
     */
    fun weiToGweiInt(wei: BigDecimal): String {
        return Convert.fromWei(wei, Convert.Unit.GWEI).toBigInteger().toString()
    }
    
    /**
     * Wei转换为Gwei (指定精度)
     * @param wei Wei数量
     * @param precision 精度
     * @return 格式化的Gwei字符串
     */
    fun weiToGwei(wei: BigDecimal, precision: Int): String {
        val gwei = Convert.fromWei(wei, Convert.Unit.GWEI)
        val df = getFormat(getDigitalPattern(precision))
        df.roundingMode = RoundingMode.DOWN
        return convertToLocale(df.format(gwei))
    }
    
    /**
     * Gwei转换为Wei
     * @param gwei Gwei数量
     * @return Wei数量
     */
    fun gweiToWei(gwei: BigDecimal): BigInteger {
        return Convert.toWei(gwei, Convert.Unit.GWEI).toBigInteger()
    }
    
    /**
     * 基础单位转换为子单位
     * @param baseAmountStr 基础金额字符串
     * @param decimals 小数位数
     * @return 子单位数量
     */
    fun baseToSubunit(baseAmountStr: String, decimals: Int): BigInteger {
        return try {
            val baseAmount = BigDecimal(baseAmountStr)
            val subunitAmount = baseAmount.multiply(BigDecimal.TEN.pow(decimals))
            subunitAmount.toBigInteger()
        } catch (e: NumberFormatException) {
            BigInteger.ZERO
        }
    }
    
    /**
     * 子单位转换为基础单位
     * @param subunitAmount 子单位数量
     * @param decimals 小数位数
     * @return 基础单位数量
     */
    @JvmStatic
    fun subunitToBase(subunitAmount: BigInteger, decimals: Int): BigDecimal {
        val divisor = BigDecimal.TEN.pow(decimals)
        return BigDecimal(subunitAmount).divide(divisor, decimals, RoundingMode.DOWN)
    }
    
    /**
     * 检查是否为小数值
     * @param value 数值字符串
     * @return 是否为小数
     */
    fun isDecimalValue(value: String): Boolean {
        return value.contains(".")
    }
    
    /**
     * 获取带限制的缩放值
     * @param value 数值
     * @param decimals 小数位数
     * @return 格式化字符串
     */
    fun getScaledValueWithLimit(value: BigDecimal, decimals: Long): String {
        return getScaledValue(value, decimals, SHOW_DECIMAL_PLACES)
    }
    
    /**
     * 获取固定精度的缩放值
     * @param value 数值
     * @param decimals 小数位数
     * @param precision 精度
     * @return 格式化字符串
     */
    fun getScaledValueFixed(value: BigDecimal, decimals: Long, precision: Int): String {
        return scaledValue(value, getDigitalPattern(precision), decimals, 0)
    }
    
    /**
     * 获取最小缩放值
     * @param value 数值
     * @param decimals 小数位数
     * @return 格式化字符串
     */
    fun getScaledValueMinimal(value: BigInteger, decimals: Long): String {
        return getScaledValueMinimal(BigDecimal(value), decimals, 4)
    }
    
    /**
     * 获取最小缩放值 (指定最大精度)
     * @param value 数值
     * @param decimals 小数位数
     * @param maxPrecision 最大精度
     * @return 格式化字符串
     */
    fun getScaledValueMinimal(value: BigDecimal, decimals: Long, maxPrecision: Int): String {
        return scaledValue(value, getDigitalPattern(maxPrecision), decimals, maxPrecision)
    }
    
    /**
     * 获取科学计数法缩放值
     * @param value 数值
     * @param decimals 小数位数
     * @return 格式化字符串
     */
    fun getScaledValueScientific(value: BigDecimal, decimals: Long): String {
        return getScaledValueScientific(value, decimals, 5)
    }
    
    /**
     * 获取科学计数法缩放值 (指定小数位数)
     * @param value 数值
     * @param decimals 小数位数
     * @param dPlaces 显示小数位数
     * @return 格式化字符串
     */
    fun getScaledValueScientific(value: BigDecimal, decimals: Long, dPlaces: Int): String {
        val correctedValue = value.divide(BigDecimal.valueOf(Math.pow(10.0, decimals.toDouble())), 18, RoundingMode.DOWN)
        
        return when {
            value == BigDecimal.ZERO -> "0"
            requiresSmallValueSuffix(correctedValue) -> smallSuffixValue(correctedValue)
            correctedValue.compareTo(displayThresholdEth) < 0 -> {
                val result = correctedValue.divide(displayThresholdEth, SLIDING_DECIMAL_PLACES, RoundingMode.DOWN)
                "$result m"
            }
            requiresSuffix(correctedValue, dPlaces) -> getSuffixedValue(correctedValue, dPlaces)
            else -> {
                val df = getFormat(getDigitalPattern(dPlaces))
                df.roundingMode = RoundingMode.DOWN
                convertToLocale(df.format(correctedValue))
            }
        }
    }
    
    /**
     * 检查是否需要小值后缀
     * @param correctedValue 修正后的数值
     * @return 是否需要后缀
     */
    fun requiresSmallValueSuffix(correctedValue: BigDecimal): Boolean {
        return correctedValue.compareTo(oneGwei) < 0
    }
    
    /**
     * 检查是否需要小Gwei值后缀
     * @param ethAmount Eth数量
     * @return 是否需要后缀
     */
    fun requiresSmallGweiValueSuffix(ethAmount: BigDecimal): Boolean {
        return ethAmount.compareTo(oneGwei) < 0
    }
    
    /**
     * 检查是否需要后缀
     * @param correctedValue 修正后的数值
     * @param dPlaces 小数位数
     * @return 是否需要后缀
     */
    private fun requiresSuffix(correctedValue: BigDecimal, dPlaces: Int): Boolean {
        val thresholdValue = BigDecimal.valueOf(Math.pow(10.0, (6 + dPlaces).toDouble()))
        return correctedValue.compareTo(thresholdValue) > 0
    }
    
    /**
     * 获取带后缀的值
     * @param correctedValue 修正后的数值
     * @param dPlaces 小数位数
     * @return 带后缀的格式化字符串
     */
    private fun getSuffixedValue(correctedValue: BigDecimal, dPlaces: Int): String {
        val df = getFormat(getDigitalPattern(0))
        df.roundingMode = RoundingMode.DOWN
        var reductionValue = 0
        var suffix = ""
        
        when {
            correctedValue.compareTo(BigDecimal.valueOf(Math.pow(10.0, (12 + dPlaces).toDouble()))) > 0 -> {
                reductionValue = 12
                suffix = "T" // 万亿
            }
            correctedValue.compareTo(BigDecimal.valueOf(Math.pow(10.0, (9 + dPlaces).toDouble()))) > 0 -> {
                reductionValue = 9
                suffix = "G" // 十亿
            }
            correctedValue.compareTo(BigDecimal.valueOf(Math.pow(10.0, (6 + dPlaces).toDouble()))) > 0 -> {
                reductionValue = 6
                suffix = "M" // 百万
            }
        }
        
        val reducedValue = correctedValue.divideToIntegralValue(BigDecimal.valueOf(Math.pow(10.0, reductionValue.toDouble())))
        return convertToLocale(df.format(reducedValue)) + suffix
    }
    
    /**
     * 获取缩放值
     * @param value 数值
     * @param decimals 小数位数
     * @param precision 精度
     * @return 格式化字符串
     */
    fun getScaledValue(value: BigDecimal, decimals: Long, precision: Int): String {
        return try {
            scaledValue(value, getDigitalPattern(precision), decimals, precision)
        } catch (e: NumberFormatException) {
            "~"
        }
    }
    
    /**
     * 缩放值处理
     * @param value 数值
     * @param pattern 格式模式
     * @param decimals 小数位数
     * @param macroPrecision 宏观精度
     * @return 格式化字符串
     */
    private fun scaledValue(value: BigDecimal, pattern: String, decimals: Long, macroPrecision: Int): String {
        var df = getFormat(pattern)
        val scaledValue = value.divide(BigDecimal.valueOf(Math.pow(10.0, decimals.toDouble())), 18, RoundingMode.DOWN)
        
        if (macroPrecision > 0) {
            val displayThreshold = BigDecimal.ONE.multiply(BigDecimal.valueOf(Math.pow(10.0, macroPrecision.toDouble())))
            if (scaledValue.compareTo(displayThreshold) > 0) {
                // 去除小数位
                df = getFormat(MACRO_PATTERN)
            }
        }
        
        df.roundingMode = RoundingMode.DOWN
        return convertToLocale(df.format(scaledValue))
    }
    
    /**
     * 默认精度方法
     * @param valueStr 数值字符串
     * @param decimals 小数位数
     * @return 格式化字符串
     */
    fun getScaledValue(valueStr: String, decimals: Long): String {
        return getScaledValue(valueStr, decimals, Token.TOKEN_BALANCE_PRECISION)
    }
    
    /**
     * 通用缩放值方法
     * @param valueStr 数值字符串
     * @param decimals 小数位数
     * @param precision 精度
     * @return 格式化字符串
     */
    fun getScaledValue(valueStr: String, decimals: Long, precision: Int): String {
        // 执行小数转换
        return when {
            decimals > 1 && !valueStr.isNullOrEmpty() && valueStr[0].isDigit() -> {
                val value = BigDecimal(valueStr)
                getScaledValue(value, decimals, precision) // 根据'decimals'合约指示器属性表示余额转账
            }
            valueStr.isNotEmpty() -> valueStr
            else -> "0"
        }
    }
    
    /**
     * 货币转换
     * @param price 价格
     * @param currencySymbol 货币符号
     * @return 格式化的货币字符串
     */
    fun genCurrencyString(price: Double, currencySymbol: String): String {
        var adjustedPrice = price
        var suffix = ""
        var format = CURRENCY_PATTERN
        
        if (adjustedPrice > ONE_BILLION) {
            format += "0"
            adjustedPrice /= ONE_BILLION
            suffix = "B"
        }
        
        val df = getFormat(format)
        df.roundingMode = RoundingMode.CEILING
        
        return if (adjustedPrice >= 0) {
            currencySymbol + df.format(adjustedPrice) + suffix
        } else {
            "-" + currencySymbol + df.format(Math.abs(adjustedPrice))
        }
    }
    
    /**
     * 获取短格式
     * @param amount 数量字符串
     * @param decimals 小数位数
     * @return 短格式字符串
     */
    fun getShortFormat(amount: String, decimals: Long): String {
        return if (amount == "0") {
            "0"
        } else {
            val a = BigDecimal(amount)
            a.movePointLeft(decimals.toInt()).toString()
        }
    }
    
    /**
     * 获取原始格式
     * @param amount 数量字符串
     * @param decimals 小数位数
     * @return 原始格式字符串
     */
    fun getRawFormat(amount: String, decimals: Long): String {
        val a = BigDecimal(amount)
        return a.movePointRight(decimals.toInt()).toString()
    }
    
    /**
     * 获取滑动基础值
     * @param value 数值
     * @param decimals 小数位数
     * @param dPlaces 显示小数位数
     * @return 格式化字符串
     */
    fun getSlidingBaseValue(value: BigDecimal, decimals: Int, dPlaces: Int): String {
        val correctedValue = value.divide(BigDecimal.valueOf(Math.pow(10.0, decimals.toDouble())), 18, RoundingMode.DOWN)
        
        return when {
            value == BigDecimal.ZERO -> "0" // 零余额
            requiresSmallValueSuffix(correctedValue) -> smallSuffixValue(correctedValue)
            correctedValue.compareTo(displayThresholdEth) < 0 -> {
                val result = correctedValue.divide(displayThresholdEth, SLIDING_DECIMAL_PLACES, RoundingMode.DOWN)
                "$result m"
            }
            requiresSuffix(correctedValue, dPlaces) -> getSuffixedValue(correctedValue, dPlaces)
            else -> {
                // 否则按标准模式显示到dPlaces小数位
                val df = getFormat(getDigitalPattern(dPlaces))
                df.roundingMode = RoundingMode.DOWN
                convertToLocale(df.format(correctedValue))
            }
        }
    }
    
    /**
     * 小后缀值处理
     * @param correctedValue 修正后的数值
     * @return 带后缀的格式化字符串
     */
    private fun smallSuffixValue(correctedValue: BigDecimal): String {
        val displayThresholdMicro = BigDecimal.ONE.divide(BigDecimal.valueOf(100000000L), 18, RoundingMode.DOWN)
        val displayThresholdNano = BigDecimal.ONE.divide(BigDecimal.valueOf(100000000000L), 18, RoundingMode.DOWN)
        
        val weiAmount = Convert.toWei(correctedValue, Convert.Unit.ETHER)
        val df = getFormat("###,###.##")
        df.roundingMode = RoundingMode.DOWN
        
        return when {
            correctedValue.compareTo(displayThresholdNano) < 0 -> {
                "${weiAmount.longValueExact()} wei"
            }
            correctedValue.compareTo(displayThresholdMicro) < 0 -> {
                val kWei = weiAmount.divide(BigDecimal.valueOf(1000), 2, RoundingMode.DOWN)
                "${df.format(kWei)}K wei"
            }
            else -> {
                val mWei = weiAmount.divide(BigDecimal.valueOf(1000000), 2, RoundingMode.DOWN)
                "${df.format(mWei)}M wei"
            }
        }
    }
}
