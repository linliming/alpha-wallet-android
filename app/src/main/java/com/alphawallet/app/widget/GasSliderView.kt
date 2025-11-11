package com.alphawallet.app.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.EIP1559FeeOracleResult
import com.alphawallet.app.ui.widget.entity.GasSettingsCallback
import com.alphawallet.app.ui.widget.entity.GasSpeed
import com.alphawallet.app.util.BalanceUtils
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Gas 设置滑块组件，支持调节 Max Fee、Priority Fee、Gas Limit 以及 nonce。
 */
class GasSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {

    private var maxDefaultPrice = 8.0f * 10.0f
    private var maxPriorityFee = 4.0f * 10.0f

    private val gasPriceValue: EditText
    private val gasLimitValue: EditText
    private val priorityFeeValue: EditText
    private val nonceValue: EditText
    private val gasPriceTitle: StandardHeader
    private val priorityFeeSliderLayout: LinearLayout

    private val gasPriceSlider: AppCompatSeekBar
    private val gasLimitSlider: AppCompatSeekBar
    private val priorityFeeSlider: AppCompatSeekBar

    private var scaleFactor = 1f
    private var priorityFeeScaleFactor = 1f
    private var minimumPrice = BigDecimal.ZERO
    private var minimumGasLimit = C.GAS_LIMIT_MIN
    private var maximumGasLimit = C.GAS_LIMIT_MAX
    private var gasLimitScaleFactor = 1f
    private var limitInit = false
    private val handler = Handler(Looper.getMainLooper())
    private val note: FrameLayout

    private var gasCallback: GasSettingsCallback? = null

    init {
        inflate(context, R.layout.item_gas_slider, this)

        gasPriceSlider = findViewById(R.id.gas_price_slider)
        gasLimitSlider = findViewById(R.id.gas_limit_slider)
        priorityFeeSlider = findViewById(R.id.priority_fee_slider)
        gasLimitValue = findViewById(R.id.gas_limit_entry)
        gasPriceValue = findViewById(R.id.gas_price_entry)
        priorityFeeValue = findViewById(R.id.priority_fee_entry)
        gasPriceTitle = findViewById(R.id.title_gas_price)
        priorityFeeSliderLayout = findViewById(R.id.layout_priority_fee)
        nonceValue = findViewById(R.id.nonce_entry)
        note = findViewById(R.id.layout_resend_note)
        minimumPrice = BalanceUtils.weiToGweiBI(BigInteger.valueOf(C.GAS_PRICE_MIN))
            .multiply(BigDecimal.TEN)
            .toFloat()
            .let { BigDecimal.valueOf(it.toDouble()) }

        calculateStaticScaleFactor()
        bindViews()
    }

    /**
     * 绑定滑块与输入框监听逻辑。
     */
    private fun bindViews() {
        gasPriceSlider.max = 100
        gasPriceSlider.progress = 1
        gasLimitSlider.max = 100
        priorityFeeSlider.max = 100
        priorityFeeSlider.progress = 1

        gasPriceSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val scaledGasPrice = BigDecimal.valueOf((progress * scaleFactor) + minimumPrice.toDouble())
                    .divide(BigDecimal.TEN)
                    .setScale(1, RoundingMode.HALF_DOWN)
                gasPriceValue.setText(scaledGasPrice.toString())
                limitInit = true
                updateGasControl()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        gasLimitSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val scaledGasLimit = BigDecimal.valueOf((progress * gasLimitScaleFactor).toLong() + minimumGasLimit)
                    .setScale(2, RoundingMode.HALF_DOWN)
                gasLimitValue.setText(scaledGasLimit.toBigInteger().toInt())
                limitInit = true
                updateGasControl()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        priorityFeeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val scaledPriority = BigDecimal.valueOf(progress * priorityFeeScaleFactor.toLong())
                    .divide(BigDecimal.TEN)
                    .setScale(1, RoundingMode.HALF_DOWN)
                priorityFeeValue.setText(scaledPriority.toString())
                limitInit = true
                updateGasControl()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (gasLimitValue.hasFocus() || gasPriceValue.hasFocus()) {
                    limitInit = true
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        updateSliderSettingsFromText()
                        updateGasControl()
                    }, 2000)
                }
            }
        }
        gasLimitValue.addTextChangedListener(textWatcher)
        gasPriceValue.addTextChangedListener(textWatcher)
    }

    /**
     * 配置重发交易时的最小 gas 价格与固定 nonce。
     */
    fun setupResendSettings(minPrice: Long) {
        val candidateMin = BalanceUtils.weiToGweiBI(BigInteger.valueOf(minPrice))
            .multiply(BigDecimal.TEN)
            .toFloat()
        if (candidateMin > minimumPrice.toFloat()) {
            minimumPrice = BigDecimal.valueOf(candidateMin.toDouble())
            calculateStaticScaleFactor()
        }
        nonceValue.isEnabled = false
        note.visibility = View.VISIBLE
        bindViews()
    }

    /**
     * 更新回调函数以通知外部 gas 设置的变动。
     */
    private fun updateGasControl() {
        val gasPriceStr = gasPriceValue.text.toString()
        val gasLimitStr = gasLimitValue.text.toString()
        val priorityFeeStr = priorityFeeValue.text.toString()
        if (gasPriceStr.isNotEmpty() && gasLimitStr.isNotEmpty()) {
            runCatching {
                val gasPriceWei = BalanceUtils.gweiToWei(BigDecimal(gasPriceStr))
                val priorityWei = BalanceUtils.gweiToWei(BigDecimal(priorityFeeStr))
                val limit = BigInteger(gasLimitStr)
                gasCallback?.gasSettingsUpdate(gasPriceWei, priorityWei, limit)
            }
        }
    }

    /**
     * 将文本输入同步回滑块位置。
     */
    private fun updateSliderSettingsFromText() {
        runCatching {
            val gweiPrice = BigDecimal(gasPriceValue.text.toString())
            setPriceSlider(gweiPrice)
        }
        runCatching {
            val gasLimit = BigDecimal(gasLimitValue.text.toString())
            val progress = ((gasLimit.toLong() - minimumGasLimit) / gasLimitScaleFactor).toInt()
            gasLimitSlider.progress = progress
        }
    }

    /**
     * 初始化 EIP-1559 gas 价格。
     */
    fun initGasPrice(speed: GasSpeed) {
        if (limitInit) return
        val gweiPrice = BalanceUtils.weiToGweiBI(speed.gasPrice.maxFeePerGas)
        val priority = BalanceUtils.weiToGweiBI(speed.gasPrice.priorityFee)
        gasPriceValue.setText(gweiPrice.setScale(1, RoundingMode.HALF_DOWN).toString())
        setPriceSlider(gweiPrice)
        priorityFeeValue.setText(priority.setScale(2, RoundingMode.HALF_DOWN).toString())
        setFeeSlider(priority)
    }

    /**
     * 根据 Oracle 的最大价格重置滑块区间。
     */
    fun initGasPriceMax(maxPrice: EIP1559FeeOracleResult) {
        val gasPriceStr = gasPriceValue.text.toString()
        if (gasPriceStr.isNotEmpty() && gasPriceStr.all { it.isDigit() || it == '.' }) {
            val gweiPrice = BigDecimal(gasPriceStr)
            val maxDefault = BalanceUtils.weiToGweiBI(maxPrice.maxFeePerGas)
                .multiply(BigDecimal.valueOf(15.0))
            val priority = BalanceUtils.weiToGweiBI(maxPrice.priorityFee)
            if (priority > BigDecimal(4.0)) {
                maxPriorityFee = priority.toFloat()
            }
            maxDefaultPrice = maxDefaultPrice.coerceAtLeast(maxDefault.toFloat())
            calculateStaticScaleFactor()
            setPriceSlider(gweiPrice)
        }
    }

    private fun setFeeSlider(priorityFee: BigDecimal) {
        val progress = ((priorityFee.toFloat() * 10.0f) / priorityFeeScaleFactor).toInt()
        priorityFeeSlider.progress = progress
    }

    private fun setPriceSlider(gweiPrice: BigDecimal) {
        val progress = (((gweiPrice.toFloat() * 10.0f) - minimumPrice.toFloat()) / scaleFactor).toInt()
        gasPriceSlider.progress = progress
    }

    /**
     * 初始化 Gas Limit，上限为推算值的 5 倍。
     */
    fun initGasLimit(limit: BigInteger, presetGas: BigInteger) {
        minimumGasLimit = maxOf((presetGas.toLong() * 5) / 6, C.GAS_LIMIT_MIN)
        maximumGasLimit = minOf(minimumGasLimit * 5, C.GAS_LIMIT_MAX)
        gasLimitScaleFactor = (maximumGasLimit - minimumGasLimit) / 100.0f
        if (limitInit) return
        gasLimitValue.setText(limit.toString())
        val progress = ((limit.toLong() - minimumGasLimit) / gasLimitScaleFactor).toInt()
        gasLimitSlider.progress = progress
    }

    private fun calculateStaticScaleFactor() {
        scaleFactor = (maxDefaultPrice - minimumPrice.toFloat()) / 100.0f
        gasLimitScaleFactor = (maximumGasLimit - minimumGasLimit) / 100.0f
        priorityFeeScaleFactor = maxPriorityFee / 100.0f
    }

    /**
     * 注册回调以在 gas 设置变更时通知外部。
     */
    fun setCallback(callback: GasSettingsCallback?) {
        gasCallback = callback
    }

    /**
     * 读取用户输入的 nonce 值。
     */
    fun getNonce(): Long {
        val nonce = nonceValue.text.toString()
        return nonce.toLongOrNull() ?: -1
    }

    /**
     * 更新 nonce 输入框。
     */
    fun setNonce(nonce: Long) {
        if (nonce >= 0) {
            nonceValue.setText(nonce.toString())
        }
    }

    /**
     * 手动触发一次回调，用于外部刷新。
     */
    fun reportPosition() {
        updateGasControl()
    }

    /**
     * 切换至 legacy gas 模式，隐藏 priority fee。
     */
    fun usingLegacyGas() {
        gasPriceTitle.setText(R.string.label_gas_price_gwei)
        priorityFeeSliderLayout.visibility = View.GONE
    }
}
