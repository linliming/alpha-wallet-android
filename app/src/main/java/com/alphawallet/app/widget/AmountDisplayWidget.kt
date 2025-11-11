package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmTokenTicker
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.adapter.NFTAssetCountAdapter
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.LocaleUtils
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat

/**
 * AmountDisplayWidget 是一个自定义 View，用于显示交易金额或 NFT 资产列表。
 *
 * 它可以根据输入的数据类型以不同的方式显示金额：
 * - 纯字符串。
 * - 带有代币符号的 BigInteger。
 * - NFT 资产列表。
 * - 带有法币价值等值的代币金额。
 */
class AmountDisplayWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // 获取设备的区域设置，用于格式化数字
    private val deviceSettingsLocale = LocaleUtils.getDeviceLocale(context)

    // 使用 by lazy 延迟初始化视图组件
    private val amount: TextView by lazy { findViewById(R.id.text_amount) }
    private val tokensList: RecyclerView by lazy { findViewById(R.id.tokens_list) }

    init {
        // 在 init 块中加载布局
        inflate(context, R.layout.item_amount_display, this)
    }

    /**
     * 直接从一个字符串设置显示的金额文本。
     * @param displayStr 要显示的文本。
     */
    fun setAmountFromString(displayStr: String) {
        amount.text = displayStr
    }

    /**
     * 根据一个 BigInteger 和代币符号格式化并显示金额。
     * @param txAmount 交易金额的 BigInteger 值。
     * @param tokenSymbol 代币的符号（例如 "ETH"）。
     */
    fun setAmountFromBigInteger(txAmount: BigInteger, tokenSymbol: String) {
        val decimalFormat = NumberFormat.getInstance(deviceSettingsLocale)
        // 使用字符串模板构建显示文本
        setAmountFromString("${decimalFormat.format(txAmount)} $tokenSymbol")
    }

    /**
     * 从一个 NFTAsset 列表设置显示内容。
     * 这会隐藏文本金额，并显示一个包含 NFT 资产计数的 RecyclerView。
     * @param assets 要显示的 NFTAsset 列表。
     */
    fun setAmountFromAssetList(assets: List<NFTAsset>) {
        // 使用 KTX 扩展属性切换可见性
        amount.isVisible = false
        tokensList.isVisible = true

        // 创建并设置 RecyclerView 的适配器
        tokensList.adapter = NFTAssetCountAdapter(assets)
    }

    /**
     * 根据代币的 BigInteger 金额计算其总价值（包括法币等值，如果可用），并返回格式化的字符串。
     * @param amountValue 交易金额的 BigInteger 值。
     * @param token 要计算价值的 Token 对象。
     * @param tokensService 用于获取 Realm 实例和代币信息的服务。
     * @return 格式化后的价值字符串。
     */
    private fun getValueString(amountValue: BigInteger, token: Token, tokensService: TokensService): String {
        val formattedValue = BalanceUtils.getScaledValueMinimal(amountValue, token.tokenInfo.decimals.toLong())
        // 使用 try-with-resources 的 Kotlin 等价物 `use`，确保 Realm 实例被安全关闭
        try {
            tokensService.getTickerRealmInstance().use { realm ->
                val rtt = realm?.where(RealmTokenTicker::class.java)
                    ?.equalTo("contract", TokensRealmSource.databaseKey(token.tokenInfo.chainId, if (token.isEthereum()) "eth" else token.getAddress().lowercase()))
                    ?.findFirst()

                if (rtt != null) {
                    // 计算等值的法币价值
                    val cryptoRate = BigDecimal(rtt.price)
                    val cryptoAmount = BalanceUtils.subunitToBase(amountValue, token.tokenInfo.decimals)
                    return context.getString(
                        R.string.fiat_format, formattedValue, token.getSymbol(),
                        TickerService.getCurrencyString(cryptoAmount.multiply(cryptoRate).toDouble()),
                        rtt.currencySymbol
                    )
                }
            }
        } catch (e: Exception) {
            // 捕获异常，防止应用崩溃，并回退到默认显示
        }

        // 如果没有获取到 Ticker 或发生异常，则返回不带法币价值的字符串
        return context.getString(R.string.total_cost, formattedValue, token.getSymbol())
    }

    /**
     * 使用一个 Token 对象来设置显示的金额，这会尝试包含法币价值。
     * @param amountValue 交易金额的 BigInteger 值。
     * @param token 相关的 Token 对象。
     * @param tokensService 用于获取 Ticker 信息的服务。
     */
    fun setAmountUsingToken(amountValue: BigInteger, token: Token, tokensService: TokensService) {
        amount.text = getValueString(amountValue, token, tokensService)
    }
}
