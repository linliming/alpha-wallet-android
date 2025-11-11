package com.alphawallet.app.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.res.TypedArray
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.CurrencyRepository
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.app.ui.widget.entity.StatusType
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.TokenIconViewModel
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.target.Target
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Displays token imagery, status, and fallback visuals with support for async loading and caching.
 */
class TokenIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val icon: ImageView
    private val iconSecondary: ImageView
    private val textIcon: TextView
    private val statusIcon: ImageView
    private val circle: ImageView
    private val pendingProgress: ProgressBar
    private val statusBackground: ImageView
    private val chainIcon: ImageView
    private val chainIconBackground: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val squareToken: Boolean
    private val viewModel: TokenIconViewModel

    private var tokensAdapterCallback: TokensAdapterCallback? = null
    @Volatile
    private var token: Token? = null
    private var disposable: Disposable? = null
    private var tokenName: String = ""
    private var currentStatus: StatusType = StatusType.NONE
    private var currentRequest: Request? = null

    /**
     * Listener that retries loading from alternative sources when the initial glide load fails.
     */
    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            loadFromAltRepo(model)
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            token?.let {
                viewModel.storeImageUrl(it.tokenInfo.chainId, it.tokenInfo.address, model.toString())
            }

            handler.post { 
                textIcon.visibility = View.GONE
                iconSecondary.visibility = View.VISIBLE
                iconSecondary.setImageDrawable(resource)
            }
            return false
        }
    }

    init {
        squareToken = resolveSquareFlag(context, attrs)
        inflate(context, if (squareToken) R.layout.item_token_icon_square else R.layout.item_token_icon, this)
        icon = findViewById(R.id.icon)
        iconSecondary = findViewById(R.id.icon_secondary)
        textIcon = findViewById(R.id.text_icon)
        statusIcon = findViewById(R.id.status_icon)
        circle = findViewById(R.id.circle)
        pendingProgress = findViewById(R.id.pending_progress)
        statusBackground = findViewById(R.id.status_icon_background)
        chainIcon = findViewById(R.id.status_chain_icon)
        chainIconBackground = findViewById(R.id.chain_icon_background)
        statusIcon.visibility = if (isInEditMode) View.VISIBLE else View.GONE
        viewModel = ViewModelProvider(findViewModelStoreOwner()).get(TokenIconViewModel::class.java)
        bindViews()
    }

    /**
     * Resolves whether the token icon should render in a square layout.
     */
    private fun resolveSquareFlag(context: Context, attrs: AttributeSet?): Boolean {
        val typedArray: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.TokenIcon,
            0,
            0
        )
        return try {
            typedArray.getBoolean(R.styleable.TokenIcon_square, false)
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Binds listener callbacks to the inflated view hierarchy.
     */
    private fun bindViews() {
        findViewById<View>(R.id.view_container).setOnClickListener(this::performTokenClick)
    }

    /**
     * Cancels any in-flight work and hides secondary visuals ready for fresh binding.
     */
    fun clearLoad() {
        iconSecondary.visibility = View.INVISIBLE
        handler.removeCallbacksAndMessages(null)
        currentRequest?.let { request ->
            if (request.isRunning) {
                request.pause()
                request.clear()
                token = null
            }
            currentRequest = null
        }
        disposable?.takeIf { !it.isDisposed }?.dispose()
        disposable = null
    }

    /**
     * Binds token data and initiates icon loading logic for the provided token.
     */
    fun bindData(token: Token) {
        this.token = token
        clearLoad()

        if (token.isEthereum()) {
            bindData(token.tokenInfo.chainId)
            return
        }

        if (token.group == TokenGroup.SPAM) {
            bindSpam(token)
        } else {
            tokenName = viewModel.getTokenName(token)
            val mainIcon = viewModel.getTokenIcon(token)
            bind(token, mainIcon)
        }
    }

    /**
     * Displays the icon corresponding to the supplied chain when no token metadata is present.
     */
    fun bindData(chainId: Long) {
        clearLoad()
        loadImageFromResource(EthereumNetworkRepository.getChainLogo(chainId))
    }

    /**
     * Sets up UI for a standard token and attempts to fetch its icon.
     */
    private fun bind(token: Token, iconUrl: String) {
        bindCommon(token)
        displayTokenIcon(iconUrl)
    }

    /**
     * Configures the view for spam tokens that should fall back to predefined visuals.
     */
    private fun bindSpam(token: Token) {
        bindCommon(token)
        setSpam()
    }

    /**
     * Applies state shared between spam and regular tokens before displaying imagery.
     */
    private fun bindCommon(token: Token) {
        handler.removeCallbacksAndMessages(null)
        this.token = token
        iconSecondary.visibility = View.INVISIBLE
        statusBackground.visibility = View.GONE
        chainIconBackground.visibility = View.GONE
        chainIcon.visibility = View.GONE
    }

    /**
     * Renders a chain icon overlay to identify the network associated with the token.
     */
    fun setChainIcon(chainId: Long) {
        chainIconBackground.visibility = View.VISIBLE
        chainIcon.visibility = View.VISIBLE
        chainIcon.setImageResource(EthereumNetworkRepository.getSmallChainLogo(chainId))
    }

    /**
     * Marks the token as spam with the static spam icon and clears highlight ring.
     */
    fun setSpam() {
        textIcon.visibility = View.GONE
        icon.setImageResource(R.drawable.ic_spam_token)
        circle.visibility = View.GONE
    }

    /**
     * Determines the default icon when no remote image is available.
     */
    private fun setupDefaultIcon() {
        val currentToken = token ?: return
        if (currentToken.isEthereum() ||
            EthereumNetworkRepository.getChainOverrideAddress(currentToken.tokenInfo.chainId)
                .equals(currentToken.getAddress(), ignoreCase = true)
        ) {
            loadImageFromResource(EthereumNetworkRepository.getChainLogo(currentToken.tokenInfo.chainId))
        } else {
            setupTextIcon(currentToken)
        }
    }

    /**
     * Attempts to load the token icon using the supplied URL and applies a placeholder strategy.
     */
    private fun displayTokenIcon(iconUrl: String) {
        setupDefaultIcon()
        val requestOptions = if (squareToken || iconUrl.startsWith(Utils.ALPHAWALLET_REPO_NAME)) {
            RequestOptions()
        } else {
            RequestOptions().circleCrop()
        }

        currentRequest = Glide.with(this)
            .load(iconUrl)
            .placeholder(R.drawable.ic_token_eth)
            .apply(requestOptions)
            .listener(requestListener)
            .into(DrawableImageViewTarget(icon))
            .request
    }

    /**
     * Updates the status icon to reflect the supplied transaction state.
     */
    fun setStatusIcon(type: StatusType) {
        val shouldAnimate = statusIcon.visibility == View.VISIBLE && type != currentStatus
        statusIcon.visibility = View.VISIBLE
        pendingProgress.visibility = View.GONE
        statusBackground.visibility = View.GONE
        when (type) {
            StatusType.PENDING -> pendingProgress.visibility = View.VISIBLE
            StatusType.FAILED -> statusIcon.setImageResource(R.drawable.ic_rejected_small)
            StatusType.REJECTED -> statusIcon.setImageResource(R.drawable.ic_transaction_rejected)
            StatusType.CONSTRUCTOR -> {
                val chainId = token?.tokenInfo?.chainId
                if (chainId != null) {
                    statusIcon.setImageResource(EthereumNetworkRepository.getChainLogo(chainId))
                    statusBackground.visibility = View.VISIBLE
                } else {
                    statusIcon.visibility = View.GONE
                }
            }
            else -> statusIcon.visibility = View.GONE
        }

        currentStatus = type

        if (shouldAnimate) {
            statusIcon.alpha = 0f
            statusIcon.animate().alpha(1f).setDuration(500).start()
        }
    }

    /**
     * Re-attempts icon loading from secondary repositories when the primary source fails.
     */
    private fun loadFromAltRepo(model: Any?) {
        val currentToken = token ?: return
        val checkUrl = model?.toString()
        if (!Utils.stillAvailable(context) || checkUrl.isNullOrEmpty()) {
            return
        }

        val useContractUri = when {
            checkUrl.startsWith(Utils.ALPHAWALLET_REPO_NAME) -> true
            !checkUrl.startsWith(Utils.TRUST_ICON_REPO_BASE) -> false
            else -> return
        }

        disposable = viewModel.getIconFallback(currentToken, useContractUri)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::loadImageAlt)
    }

    /**
     * Loads an alternate image URI into the secondary icon slot.
     */
    private fun loadImageAlt(fileUri: String) {
        val requestOptions = if (squareToken) RequestOptions() else RequestOptions().circleCrop()
        currentRequest = Glide.with(this)
            .load(fileUri)
            .apply(requestOptions)
            .listener(requestListener)
            .into(DrawableImageViewTarget(iconSecondary))
            .request
    }

    /**
     * Sets up textual initials as a fallback when no icon assets exist.
     */
    private fun setupTextIcon(token: Token) {
        icon.setImageResource(R.drawable.ic_clock)
        textIcon.visibility = View.VISIBLE
        textIcon.backgroundTintList = ContextCompat.getColorStateList(
            context,
            EthereumNetworkBase.getChainColour(token.tokenInfo.chainId)
        )

        textIcon.text = if (!token.tokenInfo.symbol.isNullOrEmpty() && token.tokenInfo.symbol.length > 1) {
            Utils.getIconisedText(token.tokenInfo.symbol)
        } else {
            Utils.getIconisedText(tokenName)
        }
    }

    /**
     * Shows fallback initials using the supplied text when no token data is available.
     */
    fun setupFallbackTextIcon(name: String) {
        textIcon.text = name
        textIcon.visibility = View.VISIBLE
        textIcon.backgroundTintList =
            ContextCompat.getColorStateList(context, EthereumNetworkBase.getChainColour(MAINNET_ID))
    }

    /**
     * Registers a listener for token click interactions.
     */
    fun setOnTokenClickListener(tokensAdapterCallback: TokensAdapterCallback?) {
        this.tokensAdapterCallback = tokensAdapterCallback
    }

    /**
     * Handles token clicks by delegating to the adapter callback with the latest token state.
     */
    private fun performTokenClick(view: View) {
        val currentToken = token ?: return
        tokensAdapterCallback?.onTokenClick(view, currentToken, null, true)
    }

    /**
     * Displays the current fiat currency flag instead of a token icon.
     */
    fun showLocalCurrency() {
        val isoCode = TickerService.getCurrencySymbolTxt()
        loadImageFromResource(CurrencyRepository.getFlagByISO(isoCode))
        token = null
    }

    /**
     * Loads a static drawable resource into the primary icon view.
     */
    private fun loadImageFromResource(@DrawableRes resourceId: Int) {
        handler.removeCallbacksAndMessages(null)
        statusBackground.visibility = View.GONE
        chainIconBackground.visibility = View.GONE
        chainIcon.visibility = View.GONE
        textIcon.visibility = View.GONE
        icon.setImageResource(resourceId)
        icon.visibility = View.VISIBLE
        circle.visibility = View.VISIBLE
    }

    /**
     * Applies a grayscale colour filter to the icon, reverting to full colour when disabled.
     */
    fun setGrayscale(grayscale: Boolean) {
        if (grayscale) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(matrix)
            icon.colorFilter = filter
            iconSecondary.colorFilter = filter
        } else {
            icon.clearColorFilter()
            iconSecondary.clearColorFilter()
        }
    }

    /**
     * Retrieves the view model store owner, which will be the host activity.
     */
    private fun findViewModelStoreOwner(): ViewModelStoreOwner {
        var context = context
        while (context is ContextWrapper) {
            if (context is ViewModelStoreOwner) {
                return context
            }
            context = context.baseContext
        }
        throw IllegalStateException("ViewModelStoreOwner not found")
    }
}
