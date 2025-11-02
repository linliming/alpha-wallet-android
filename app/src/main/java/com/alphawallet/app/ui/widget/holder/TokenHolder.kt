package com.alphawallet.app.ui.widget.holder

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.app.widget.TokenIcon
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.token.tools.Convert
import com.alphawallet.token.tools.TokenDefinition
import com.google.android.material.checkbox.MaterialCheckBox
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class TokenHolder(
    parent: ViewGroup,
    private val assetDefinition: AssetDefinitionService,
    private val tokensService: TokensService,
) :
    BinderViewHolder<TokenCardMeta>(R.layout.item_token, parent),
    OnClickListener,
    OnLongClickListener {

    companion object {
        @JvmField
        val VIEW_TYPE = 1005

        @JvmField
        val EMPTY_BALANCE = "\u2014\u2014"

        @JvmField
        val CHECK_MARK = "[x]"

        private const val TICKER_PERIOD_VALIDITY = 60 * DateUtils.MINUTE_IN_MILLIS
    }

    private val tokenIcon: TokenIcon = findViewById(R.id.token_icon)
    private val balanceEth: TextView = findViewById(R.id.eth_data)
    private val balanceCurrency: TextView = findViewById(R.id.balance_currency)
    private val balanceCoin: TextView = findViewById(R.id.balance_coin)
    private val text24Hours: TextView = findViewById(R.id.text_24_hrs)
    private val root24Hours: View = findViewById(R.id.root_24_hrs)
    private val image24h: ImageView = findViewById(R.id.image_24_hrs)
    private val textAppreciation: TextView = findViewById(R.id.text_appreciation)
    private val layoutAppreciation: View = findViewById(R.id.layout_appreciation)
    private val extendedInfo: LinearLayout = findViewById(R.id.layout_extended_info)
    private val tokenLayout: RelativeLayout = findViewById(R.id.token_layout)
    private val selectToken: MaterialCheckBox = findViewById(R.id.select_token)
    private val tickerProgress: ProgressBar = findViewById(R.id.ticker_progress)

    var token: Token? = null
    var tokenKey: String? = null
    private var tokensAdapterCallback: TokensAdapterCallback? = null

    init {
        itemView.setOnClickListener(this)
        itemView.setOnLongClickListener(this)
    }

    override fun bind(data: TokenCardMeta?, addition: Bundle) {
        tokenIcon.clearLoad()
        layoutAppreciation.foreground = null
        if (data == null) {
            fillEmpty()
            return
        }

        try {
            tokenKey = data.tokenId
            tokenLayout.visibility = View.VISIBLE
            token = tokensService.getToken(data.chain, data.address)?.also { it.group = data.getTokenGroup() }

            if (data.group == TokenGroup.ATTESTATION) {
                handleAttestation(data)
                return
            } else if (token == null) {
                fillEmpty()
                return
            } else if (data.getNameWeight() < 1000L && token?.isEthereum() == false) {
                // Edge condition - looking at a contract as an account
                tokensService.getToken(data.chain, "eth")?.let { token = it }
            }

            if (token != null && EthereumNetworkRepository.isPriorityTokenCompat(token!!)) {
                extendedInfo.visibility = View.GONE
            }

            if (!data.getFilterText().isNullOrEmpty() && data.getFilterText() == CHECK_MARK) {
                setupCheckButton(data)
            } else {
                selectToken.visibility = View.GONE
            }

            balanceEth.text = shortTitle()

            val coinBalance = token?.getStringBalanceForUI(4)
            if (!coinBalance.isNullOrEmpty()) {
                balanceCoin.visibility = View.VISIBLE
                val symbol = token?.getSymbol()?.let {
                    it.substring(0, min(it.length, Token.MAX_TOKEN_SYMBOL_LENGTH)).uppercase(Locale.getDefault())
                } ?: ""
                balanceCoin.text = getString(R.string.valueSymbol, coinBalance, symbol)
            } else {
                balanceCoin.visibility = View.GONE
            }

            token?.let {
                tokenIcon.bindData(it)
                if (!it.isEthereum()) {
                    tokenIcon.setChainIcon(it.tokenInfo.chainId)
                }
            }
            tokenIcon.setOnTokenClickListener(tokensAdapterCallback)

            populateTicker(token?.group ?: data.getTokenGroup())

            greyOutSpamTokenValue()
        } catch (ex: Exception) {
            Timber.w(ex)
            fillEmpty()
        }
    }

    private fun greyOutSpamTokenValue() {
        // noop - reserved for future styling changes
    }

    override fun onDestroyView() {
        // no-op
    }

    private fun handleAttestation(data: TokenCardMeta) {
        val attestation =
            tokensService.getAttestation(data.chain, data.address, data.getAttestationId()) as? Attestation
        if (attestation == null) {
            fillEmpty()
            return
        }

        val td: TokenDefinition? = assetDefinition.getAssetDefinition(attestation)
        val nftAsset = NFTAsset()
        nftAsset.setupScriptElements(td)

        balanceEth.text = attestation.getAttestationName(td)
        balanceCoin.text = attestation.getAttestationDescription(td)
        balanceCoin.visibility = View.VISIBLE

        if (attestation.knownIssuerKey()) {
            tokenIcon.setSmartPassIcon(data.chain)
        } else {
            tokenIcon.setAttestationIcon(nftAsset.image, attestation.getSymbol(), data.chain)
        }

        token = attestation
        blankTickerInfo()
    }

    private fun populateTicker(group: TokenGroup) {
        val currentToken = token ?: run {
            blankTickerInfo()
            return
        }

        resetTickerViews()
        val ticker = tokensService.getTokenTicker(currentToken)
        if (ticker != null || (currentToken.isEthereum() && EthereumNetworkRepository.hasRealValue(currentToken.tokenInfo.chainId))) {
            handleTicker(ticker, group)
        } else {
            balanceCurrency.visibility = View.GONE
            layoutAppreciation.visibility = View.GONE
        }

        if (!currentToken.isEthereum() && currentToken.tokenInfo.chainId != MAINNET_ID) {
            showNetworkLabel()
        } else {
            hideNetworkLabel()
        }
    }

    private fun handleTicker(ticker: TokenTicker?, group: TokenGroup) {
        if (ticker != null) {
            layoutAppreciation.visibility = View.VISIBLE
            balanceCurrency.visibility = View.VISIBLE
            setTickerInfo(ticker)
            maskSpamOrStaleTicker(ticker, group)
        } else {
            blankTickerInfo()
        }
    }

    private fun blankTickerInfo() {
        balanceCurrency.visibility = View.GONE
        layoutAppreciation.visibility = View.GONE
    }

    private fun maskSpamOrStaleTicker(ticker: TokenTicker, group: TokenGroup) {
        when {
            group == TokenGroup.SPAM -> {
                root24Hours.visibility = View.GONE
                textAppreciation.visibility = View.GONE
                tickerProgress.visibility = View.GONE
                balanceCurrency.alpha = 0.3f
            }

            System.currentTimeMillis() - ticker.updateTime > TICKER_PERIOD_VALIDITY -> {
                root24Hours.visibility = View.GONE
                textAppreciation.visibility = View.GONE
                tickerProgress.visibility = View.VISIBLE
                balanceCurrency.alpha = 0.3f
            }

            else -> {
                tickerProgress.visibility = View.GONE
                root24Hours.visibility = View.VISIBLE
                textAppreciation.visibility = View.VISIBLE
                balanceCurrency.alpha = 1.0f
            }
        }
    }

    private fun showNetworkLabel() {
        // reserved
    }

    private fun hideNetworkLabel() {
        // reserved
    }

    private fun fillEmpty() {
        token = null
        balanceEth.setText(R.string.empty)
        balanceCurrency.text = EMPTY_BALANCE
        balanceCoin.visibility = View.GONE
        layoutAppreciation.visibility = View.GONE
    }

    override fun onClick(v: View) {
        token?.let { tokensAdapterCallback?.onTokenClick(v, it, null, true) }
    }

    override fun onLongClick(v: View): Boolean {
        token?.let { tokensAdapterCallback?.onLongTokenClick(v, it, null) }
        return true
    }

    override fun setOnTokenClickListener(tokensAdapterCallback: TokensAdapterCallback?) {
        this.tokensAdapterCallback = tokensAdapterCallback
    }

    private fun setTickerInfo(ticker: TokenTicker) {
        val currentToken = token ?: return
        val correctedBalance = currentToken.getCorrectedBalance(Convert.Unit.ETHER.factor)
        val fiatBalance = correctedBalance.multiply(BigDecimal(ticker.price))
            .setScale(Convert.Unit.ETHER.factor, RoundingMode.DOWN)
        val converted =  TickerService.getCurrencyString(fiatBalance)
        val lbl = getString(R.string.token_balance, "", converted)
        if (correctedBalance > BigDecimal.ZERO) {
            val spannable: Spannable = SpannableString(lbl)
            spannable.setSpan(
                ForegroundColorSpan(Color.RED),
                converted.length,
                lbl.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            balanceCurrency.text = lbl
        } else {
            balanceCurrency.text = EMPTY_BALANCE
        }

        var color = Color.RED
        var formattedPercents = ""
        val percentage = ticker.percentChange24h.toDoubleOrNull() ?: 0.0
        try {
            color = ContextCompat.getColor(
                getContext(),
                if (percentage < 0) R.color.negative else R.color.positive,
            )
            formattedPercents = String.format(Locale.getDefault(), "%.2f", percentage)
                .replace("-", "") + "%"
            root24Hours.setBackgroundResource(
                if (percentage < 0) R.drawable.background_24h_change_red else R.drawable.background_24h_change_green,
            )
            text24Hours.text = formattedPercents
            text24Hours.setTextColor(color)
            image24h.setImageResource(
                if (percentage < 0) R.drawable.ic_price_down else R.drawable.ic_price_up,
            )
        } catch (e: Exception) {
            Timber.e(e)
        }

        val percentageDecimal =
            ticker.percentChange24h.toBigDecimalOrNull()?.divide(BigDecimal(100), 4, RoundingMode.HALF_DOWN)
                ?: BigDecimal.ZERO
        val currencyChange = fiatBalance.multiply(percentageDecimal)
        val formattedValue = TickerService.getCurrencyString(currencyChange)

        textAppreciation.setTextColor(color)
        textAppreciation.text = formattedValue
    }

    private fun shortTitle(): String {
        val currentToken = token ?: return ""
        val localizedName = currentToken.getTSName(assetDefinition, currentToken.getTokenCount())
        return if (!localizedName.isNullOrEmpty()) {
            localizedName
        } else {
            currentToken.getName() ?: ""
        }
    }

    private fun resetTickerViews() {
        extendedInfo.foreground = null
        layoutAppreciation.foreground = null
    }

    private fun setupCheckButton(data: TokenCardMeta) {
        selectToken.visibility = View.VISIBLE
        selectToken.setOnCheckedChangeListener(null)
        selectToken.isChecked = data.isEnabled
        selectToken.setOnCheckedChangeListener { _, isChecked ->
            data.isEnabled = isChecked
        }
    }
}
