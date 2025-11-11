package com.alphawallet.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.app.util.Utils
import com.alphawallet.app.util.Utils.isIPFS
import com.alphawallet.app.util.Utils.loadFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.target.Target
import timber.log.Timber
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Displays NFT imagery with Glide backed image loading and WebView fallbacks.
 */
class NFTImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), View.OnTouchListener {

    private val image: ImageView
    private val webLayout: RelativeLayout
    private val webView: WebView
    private val holdingView: ConstraintLayout
    private val fallbackLayout: RelativeLayout
    private val fallbackIcon: TokenIcon
    private val progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var loadRequest: Request? = null
    private var imageUrl: String? = null
    private var isThumbnail: Boolean = false
    private var webViewHeight: Int = 0
    private var heightUpdates: Int = 0

    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            val message = e?.toString().orEmpty()
            when {
                message.contains(C.GLIDE_URL_INVALID) -> handler.post {
                    progressBar.visibility = GONE
                    fallbackLayout.visibility = VISIBLE
                }
                model != null -> {
                    setWebView(model.toString(), ImageType.IMAGE)
                    fallbackLayout.visibility = GONE
                }
            }
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            progressBar.visibility = GONE
            fallbackLayout.visibility = GONE
            return false
        }
    }

    init {
        inflate(context, R.layout.item_asset_image, this)
        image = findViewById(R.id.image_asset)
        webLayout = findViewById(R.id.web_view_wrapper)
        webView = findViewById(R.id.image_web_view)
        holdingView = findViewById(R.id.layout_holder)
        fallbackLayout = findViewById(R.id.layout_fallback)
        fallbackIcon = findViewById(R.id.icon_fallback)
        progressBar = findViewById(R.id.avatar_progress_spinner)

        webLayout.visibility = GONE
        webView.visibility = GONE

        attrs?.let { applyAttributes(context, it) }
    }

    /**
     * Configures the view to display an NFT thumbnail.
     */
    fun setupTokenImageThumbnail(asset: NFTAsset) {
        setupTokenImageThumbnail(asset, false)
    }

    /**
     * Configures the view to display an NFT thumbnail with optional rounded top corners.
     */
    fun setupTokenImageThumbnail(asset: NFTAsset, onlyRoundTopCorners: Boolean) {
        heightUpdates = 0
        fallbackIcon.setupFallbackTextIcon(asset.getName())
        isThumbnail = true
        loadImage(asset.getThumbnail(), asset.getBackgroundColor() ?: "")
        if (onlyRoundTopCorners) {
            findViewById<ImageView>(R.id.overlay_rect).setImageResource(R.drawable.mask_rounded_corners_top_only)
            findViewById<ImageView>(R.id.image_overlay_rect).setImageResource(R.drawable.mask_rounded_corners_top_only)
        }
    }

    /**
     * Loads the primary NFT image or animation for a full view card.
     */
    @Throws(IllegalArgumentException::class)
    fun setupTokenImage(asset: NFTAsset) {
        heightUpdates = 0
        isThumbnail = false
        val animationUrl = asset.getAnimation()
        fallbackIcon.setupFallbackTextIcon(asset.getName())

        if (animationUrl != null && !isGlb(animationUrl) && !isAudio(animationUrl) && !isIPFS(animationUrl)) {
            if (!shouldLoad(animationUrl)) return
            setWebView(animationUrl, ImageType.ANIM)
        } else if (shouldLoad(asset.getImage())) {
            loadImage(asset.getImage(), asset.getBackgroundColor() ?: "")
            playAudioIfAvailable(animationUrl)
        }
    }

    /**
     * Updates the underlying ImageView resource id.
     */
    fun setImageResource(resourceId: Int) {
        image.setImageResource(resourceId)
    }

    /**
     * Loads a still NFT image using Glide and applies background tinting.
     */
    @Throws(IllegalArgumentException::class)
    private fun loadImage(url: String?, backgroundColor: String) {
        if (!Utils.stillAvailable(context)) return
        if (!shouldLoad(url)) return

        loadRequest?.takeIf { it.isRunning }?.clear()

        imageUrl = url
        image.visibility = View.VISIBLE
        webLayout.visibility = View.GONE

        try {
            val color = Color.parseColor("#$backgroundColor")
            holdingView.backgroundTintList = ColorStateList.valueOf(color)
        } catch (e: Exception) {
            holdingView.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent))
        }

        loadRequest = Glide.with(context)
            .load(url)
            .transition(withCrossFade())
            .override(Target.SIZE_ORIGINAL)
            .timeout(30 * 1000)
            .listener(requestListener)
            .into(DrawableImageViewTarget(image))
            .request

        startImageListener()
    }

    /**
     * Displays a web-based asset (animations, models, SVG) using a WebView fallback.
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setWebView(imageUrl: String, hint: ImageType) {
        progressBar.visibility = VISIBLE
        loadRequest?.takeIf { it.isRunning }?.clear()
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.webChromeClient = WebChromeClient()
        startWebViewListener()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = GONE
            }
        }

        val displayType = DisplayType(imageUrl, hint)

        handler.post {
            this.imageUrl = imageUrl
            image.visibility = GONE
            webLayout.visibility = VISIBLE
            webView.visibility = VISIBLE

            when (displayType.imageType) {
                ImageType.WEB -> {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.javaScriptCanOpenWindowsAutomatically = true
                    webView.loadUrl(imageUrl)
                }
                ImageType.ANIM -> {
                    val loaderAnim = loadFile(context, R.raw.token_anim)
                        .replace("[URL]", imageUrl)
                        .replace("[MIME]", displayType.mimeType)
                    webView.setOnTouchListener(this)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.javaScriptCanOpenWindowsAutomatically = true
                    webView.settings.mediaPlaybackRequiresUserGesture = false
                    webView.settings.allowContentAccess = true
                    webView.settings.blockNetworkLoads = false
                    webView.settings.domStorageEnabled = true
                    val base64 = Base64.encodeToString(loaderAnim.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
                    webView.loadData(base64, "text/html; charset=utf-8", "base64")
                }
                ImageType.MODEL -> {
                    webView.setOnTouchListener(this)
                    val loader = loadFile(context, R.raw.token_model).replace("[URL]", imageUrl)
                    val base64 = Base64.encodeToString(loader.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
                    webView.loadData(base64, "text/html; charset=utf-8", "base64")
                }
                ImageType.RAW_SVG -> {
                    val svgWithClass = if (isThumbnail) imageUrl else addClassToSvg(imageUrl, "center-fit")
                    val loaderSvg = loadFile(context, R.raw.token_svg).replace("[SVG_IMAGE_CODE]", svgWithClass)
                    val base64 = Base64.encodeToString(loaderSvg.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
                    webView.loadData(base64, "text/html; charset=utf-8", "base64")
                }
                ImageType.IMAGE -> {
                    val loader = loadFile(context, R.raw.token_graphic).replace("[URL]", imageUrl)
                    val base64 = Base64.encodeToString(loader.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
                    webView.loadData(base64, "text/html; charset=utf-8", "base64")
                    if (isThumbnail) {
                        setWebViewHeight(image.height)
                    }
                }
                ImageType.AUDIO -> {
                    // Unused branch as audio is handled separately, maintained for completeness.
                }
            }
        }
    }

    /**
     * Adds a CSS class to the root SVG element when needed.
     */
    private fun addClassToSvg(svgString: String, className: String): String {
        return if (svgString.contains("""class="$className"""")) {
            svgString
        } else {
            val insertPosition = svgString.indexOf("<svg").takeIf { it >= 0 }?.plus(4) ?: return svgString
            svgString.substring(0, insertPosition) + """ class="$className"""" + svgString.substring(insertPosition)
        }
    }

    /**
     * Reads custom attributes to configure initial view dimensions.
     */
    private fun applyAttributes(context: Context, attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ERC721ImageView, 0, 0).use { typedArray ->
            val heightInDp = typedArray.getInteger(R.styleable.ERC721ImageView_webview_height, 0)
            webViewHeight = Utils.dp2px(getContext(), heightInDp)
        }
    }

    /**
     * Observes layout changes on WebView to stabilise its height.
     */
    private fun startWebViewListener() {
        if (isThumbnail) return

        webView.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (heightUpdates < 3) {
                val height = bottom - top
                heightUpdates++
                val delay = if (heightUpdates == 3) 0L else 500L
                updateWebView(height, delay)
            }
        }
    }

    /**
     * Listens for initial layout pass of the thumbnail image and adjusts minimum height.
     */
    private fun startImageListener() {
        if (!isThumbnail) return

        image.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (heightUpdates == 0) {
                val height = bottom - top
                if (height > 0) {
                    updateImageView(height)
                }
            }
        }
    }

    /**
     * Adjusts the thumbnail height on the main thread.
     */
    private fun updateImageView(height: Int) {
        handler.post {
            setImageViewHeight(height)
            heightUpdates = 3
        }
    }

    /**
     * Adjusts the WebView wrapper height with an optional delay.
     */
    private fun updateWebView(height: Int, delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            setWebViewHeight(height.coerceAtLeast(webViewHeight))
            heightUpdates = 3
        }, delay)
    }

    /**
     * Sets the WebView container height if available.
     */
    private fun setWebViewHeight(height: Int) {
        if (height <= 0) return
        val params = webLayout.layoutParams
        params.height = height
        webLayout.layoutParams = params
    }

    /**
     * Ensures thumbnail images honour a sensible minimum height in dp.
     */
    private fun setImageViewHeight(height: Int) {
        val defaultHeight = Utils.dp2px(context, STANDARD_THUMBNAIL_HEIGHT)
        if (height < defaultHeight) {
            val params = image.layoutParams
            params.height = defaultHeight
            image.layoutParams = params
        }
    }

    /**
     * Shows the fallback icon view and wires click behaviour through the adapter callback.
     */
    fun showFallbackLayout(token: Token) {
        fallbackLayout.visibility = View.VISIBLE
        fallbackIcon.bindData(token)
        fallbackIcon.setOnTokenClickListener(object : TokensAdapterCallback {
            override fun onTokenClick(view: View, token: Token, tokenIds: List<BigInteger>, selected: Boolean) {
                performClick()
            }

            override fun onLongTokenClick(view: View, token: Token, tokenIds: List<BigInteger>) {
                // no-op
            }
        })
    }

    /**
     * Determines whether the requested url should trigger a reload.
     */
    private fun shouldLoad(url: String?): Boolean {
        return when {
            url.isNullOrEmpty() -> false
            imageUrl == null -> true
            else -> imageUrl != url
        }
    }

    /**
     * Clears the stored url so the next call will trigger a reload.
     */
    fun clearImage() {
        imageUrl = null
    }

    /**
     * Indicates whether an image or web view content is currently associated with the view.
     */
    fun isDisplayingImage(): Boolean = !TextUtils.isEmpty(imageUrl)

    /**
     * Checks whether the url points to a GLB 3D model.
     */
    private fun isGlb(url: String?): Boolean {
        val extension = url?.let { MimeTypeMap.getFileExtensionFromUrl(it) } ?: return false
        return extension.equals("glb", ignoreCase = true)
    }

    /**
     * Checks whether the url is a known audio file type.
     */
    private fun isAudio(url: String?): Boolean {
        val extension = url?.let { MimeTypeMap.getFileExtensionFromUrl(it) }?.lowercase()
            ?: return false
        return AUDIO_TYPES.contains(extension)
    }

    /**
     * Plays an accompanying audio track when supplied alongside the NFT media.
     */
    private fun playAudioIfAvailable(url: String?) {
        if (!isAudio(url)) return

        mediaPlayer?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            try {
                it.reset()
                it.release()
            } catch (e: Exception) {
                Timber.w(e, "Failed to release previous media player")
            }
        }

        val player = MediaPlayer()
        try {
            player.setDataSource(url)
            player.prepare()
            player.start()
            player.isLooping = true
            mediaPlayer = player
        } catch (e: Exception) {
            Timber.w(e, "Failed to play NFT audio")
            try {
                player.reset()
                player.release()
            } catch (releaseError: Exception) {
                Timber.w(releaseError, "Failed to release media player after error")
            }
            mediaPlayer = null
        }
    }

    /**
     * Releases the media player resources when the containing lifecycle ends.
     */
    fun onDestroy() {
        mediaPlayer?.let {
            try {
                it.reset()
                it.release()
            } catch (e: Exception) {
                Timber.w(e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    /**
     * Pauses audio playback when the host lifecycle is paused.
     */
    fun onPause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    /**
     * Resumes audio playback when the host lifecycle resumes.
     */
    fun onResume() {
        if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    /**
     * Handles touch events to forward clicks when the WebView acts as the touch target.
     */
    override fun onTouch(view: View?, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    /**
     * Displays predefined artwork for attestation tokens.
     */
    fun setAttestationImage(token: Token) {
        if (token.getInterfaceSpec() == ContractType.ATTESTATION) {
            val attestation = token as? Attestation
            if (attestation?.isSmartPass() == true) {
                setImageResource(R.drawable.smart_pass)
            } else {
                setImageResource(R.drawable.zero_one_block)
            }
        }
    }

    private inner class DisplayType(url: String?, hint: ImageType) {
        val imageType: ImageType
        val mimeType: String

        init {
            val (type, mime) = run {
                if (url.isNullOrEmpty() || url.length < 5) {
                    return@run hint to ""
                }
                if (url.startsWith("<svg") && url.endsWith("</svg>")) {
                    return@run ImageType.RAW_SVG to ""
                }

                val extension = MimeTypeMap.getFileExtensionFromUrl(url).orEmpty().lowercase()

                when (extension) {
                    "" -> {
                        val imageType = when {
                            url.contains("tokenscript.org") -> ImageType.WEB
                            hint == ImageType.IMAGE || hint == ImageType.ANIM -> hint
                            else -> ImageType.WEB
                        }
                        imageType to ""
                    }
                    "mp4", "webm", "avi", "mpeg", "mpg", "m2v" -> {
                        ImageType.ANIM to "video/$extension"
                    }
                    "bmp", "png", "jpg", "svg" -> {
                        ImageType.IMAGE to "image/$extension"
                    }
                    "glb" -> {
                        ImageType.MODEL to "model/gltf-binary"
                    }
                    else -> {
                        ImageType.IMAGE to "image/$extension"
                    }
                }
            }
            imageType = type
            mimeType = mime
        }
    }

    private enum class ImageType {
        IMAGE, ANIM, WEB, MODEL, AUDIO, RAW_SVG
    }

    companion object {
        private const val STANDARD_THUMBNAIL_HEIGHT = 156
        private val AUDIO_TYPES = listOf("mp3", "ogg", "wav", "flac", "aac", "opus", "weba")
    }
}
