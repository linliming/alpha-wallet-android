package com.alphawallet.app.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.alphawallet.app.R
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.ui.widget.entity.AvatarWriteCallback
import com.alphawallet.app.util.Blockies
import com.alphawallet.app.util.Utils.loadFile
import com.alphawallet.app.util.ens.AWEnsResolver
import com.alphawallet.app.util.ens.EnsResolver.Companion.USE_ENS_CHAIN
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.nio.charset.StandardCharsets

/**
 * Displays a wallet avatar by preferring ENS metadata, with blockies as the fallback strategy.
 */
class UserAvatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val ensResolver: AWEnsResolver =
        AWEnsResolver(TokenRepository.getWeb3jService(USE_ENS_CHAIN), context)
    private var loadAvatarDisposable: Disposable? = null
    private var iconRequest: Request? = null
    private var walletAddress: String? = null

    private val image: ImageView
    private val webLayout: RelativeLayout
    private val webView: WebView
    private var state: BindingState = BindingState.NONE

    /**
     * Listener that ensures blockies fallback when Glide fails.
     */
    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            setBlockie(walletAddress)
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (resource != null) {
                image.visibility = View.VISIBLE
                image.setImageDrawable(resource)
            }
            return false
        }
    }

    init {
        inflate(context, R.layout.item_asset_image, this)
        image = findViewById(R.id.image_asset)
        webLayout = findViewById(R.id.web_view_wrapper)
        webView = findViewById(R.id.image_web_view)
        findViewById<View>(R.id.overlay).visibility = View.VISIBLE
    }

    /**
     * Binds the avatar using cached wallet data, without ENS lookup.
     */
    fun bind(wallet: Wallet) {
        bind(wallet, null)
    }

    /**
     * Resets current binding state and hides rendered assets.
     */
    fun resetBinding() {
        image.visibility = View.GONE
        webLayout.visibility = View.GONE
        state = BindingState.NONE
        walletAddress = null
    }

    /**
     * Shows the waiting spinner while ENS resolution occurs.
     */
    fun setWaiting() {
        findViewById<View>(R.id.avatar_progress_spinner).visibility = View.VISIBLE
    }

    /**
     * Hides the waiting spinner once resolution completes.
     */
    fun finishWaiting() {
        findViewById<View>(R.id.avatar_progress_spinner).visibility = View.GONE
    }

    /**
     * Binds the wallet and kicks off ENS resolution when required.
     */
    fun bindAndFind(wallet: Wallet) {
        walletAddress = wallet.address
        if ((state == BindingState.NONE || state == BindingState.BLOCKIE) &&
            !wallet.address.equals(ZERO_ADDRESS, ignoreCase = true)
        ) {
            bind(wallet, null)
        }

        when {
            (state == BindingState.NONE || state == BindingState.BLOCKIE) && !wallet.ENSname.isNullOrEmpty() ->
                resolveAvatar(wallet, null)

            state == BindingState.SCANNING_ENS ->
                setBlockie(wallet.address)
        }
    }

    /**
     * Binds a wallet and optionally persists discovered avatars.
     */
    fun bind(wallet: Wallet, avCallback: AvatarWriteCallback?) {
        if (iconRequest?.isRunning == true) iconRequest?.clear()
        if (loadAvatarDisposable?.isDisposed == false) {
            loadAvatarDisposable?.dispose()
        }

        walletAddress = wallet.address

        if (!wallet.ENSAvatar.isNullOrEmpty() && wallet.ENSAvatar!!.length > 1) {
            loadAvatar(wallet.ENSAvatar!!, null, wallet, alwaysLoad = true)
        } else {
            setBlockie(wallet.address)
            if (wallet.ENSAvatar != null && wallet.ENSAvatar!!.length == 1) return
        }

        if (avCallback != null) {
            resolveAvatar(wallet, avCallback)
        }
    }

    /**
     * Resolves ENS avatar URLs asynchronously.
     */
    private fun resolveAvatar(wallet: Wallet, callback: AvatarWriteCallback?) {
        if (!wallet.ENSname.isNullOrEmpty()) {
            state = BindingState.SCANNING_ENS
            loadAvatarDisposable = ensResolver.getENSUrl(wallet.ENSname!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { iconUrl -> loadAvatar(iconUrl, callback, wallet, alwaysLoad = false) },
                    { throwable -> onError(throwable) }
                )
        }
    }

    /**
     * Handles ENS lookup errors by reverting to blockies.
     */
    private fun onError(throwable: Throwable) {
        setBlockie(walletAddress)
        Timber.e(throwable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (iconRequest?.isRunning == true) {
            iconRequest?.clear()
        }
        if (loadAvatarDisposable?.isDisposed == false) {
            loadAvatarDisposable?.dispose()
            loadAvatarDisposable = null
        }
    }

    /**
     * Displays the blockies icon for the supplied address.
     */
    private fun setBlockie(address: String?) {
        state = BindingState.BLOCKIE
        if (address.isNullOrEmpty() || address.equals(ZERO_ADDRESS, ignoreCase = true)) return
        image.visibility = View.VISIBLE
        webLayout.visibility = View.GONE
        image.setImageBitmap(Blockies.createIcon(address.lowercase()))
    }

    /**
     * Loads avatar imagery from ENS or persisted metadata.
     */
    private fun loadAvatar(
        iconUrl: String?,
        avCallback: AvatarWriteCallback?,
        wallet: Wallet,
        alwaysLoad: Boolean
    ) {
        if (loadAvatarDisposable == null && !alwaysLoad) return
        if (iconUrl.isNullOrEmpty()) {
            wallet.ENSAvatar = "-"
            setBlockie(wallet.address)
            return
        } else if (avCallback != null && !iconUrl.equals(wallet.ENSAvatar, ignoreCase = true)) {
            avCallback.avatarFound(wallet)
        }

        wallet.ENSAvatar = iconUrl
        state = BindingState.IMAGE

        if (iconUrl.endsWith(".svg", ignoreCase = true)) {
            setWebView(iconUrl)
        } else {
            image.visibility = View.VISIBLE
            webLayout.visibility = View.GONE
            iconRequest = Glide.with(context)
                .load(iconUrl)
                .apply(RequestOptions().circleCrop())
                .listener(requestListener)
                .into(image)
                .request
        }
    }

    /**
     * Renders SVG-based avatars inside an embedded WebView.
     */
    private fun setWebView(imageUrl: String) {
        val loader = loadFile(context, R.raw.token_graphic).replace("[URL]", imageUrl)
        val base64 = Base64.encodeToString(loader.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
        image.visibility = View.GONE
        webLayout.visibility = View.VISIBLE
        webView.loadData(base64, "text/html; charset=utf-8", "base64")
    }

    private enum class BindingState {
        NONE,
        BLOCKIE,
        SCANNING_ENS,
        IMAGE
    }
}
