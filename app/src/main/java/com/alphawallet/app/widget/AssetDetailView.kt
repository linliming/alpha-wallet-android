package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.MainThread
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActionSheetInterface
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 负责展示单个 NFT 资产信息的自定义视图。
 */
class AssetDetailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val assetName: TextView
    private val assetDescription: TextView
    private val layoutDetails: LinearLayout
    private val assetDetails: ImageView
    private val layoutHolder: LinearLayout
    private val loadingSpinner: ProgressBar
    private val imageView: NFTImageView
    private val spacingLine: View

    private val supervisorJob = SupervisorJob()
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + supervisorJob)
    private var fetchJob: Job? = null

    init {
        inflate(context, R.layout.item_asset_detail, this)
        assetName = findViewById(R.id.text_asset_name)
        assetDescription = findViewById(R.id.text_asset_description)
        assetDetails = findViewById(R.id.image_more)
        layoutDetails = findViewById(R.id.layout_details)
        layoutHolder = findViewById(R.id.layout_holder)
        imageView = findViewById(R.id.asset_image)
        loadingSpinner = findViewById(R.id.loading_spinner)
        spacingLine = findViewById(R.id.spacing_line)
    }

    /**
     * 根据代币与 tokenId 初始化资产展示；如未缓存则异步获取元数据。
     */
    @MainThread
    fun setupAssetDetail(token: Token, tokenId: String, actionSheetInterface: ActionSheetInterface?) {
        val cachedAsset = token.getAssetForToken(tokenId)
        if (cachedAsset == null) {
            loadingSpinner.visibility = View.VISIBLE
            fetchJob?.cancel()
            fetchJob = uiScope.launch {
                val asset = fetchAsset(token, tokenId)
                if (asset != null) {
                    setupAssetDetail(asset, actionSheetInterface)
                } else {
                    loadingSpinner.visibility = View.GONE
                }
            }
        } else {
            setupAssetDetail(cachedAsset, actionSheetInterface)
        }
    }

    /**
     * 使用已获取的 NFT 资产填充视图内容。
     */
    @MainThread
    private fun setupAssetDetail(asset: NFTAsset, actionSheetInterface: ActionSheetInterface?) {
        if (!Utils.stillAvailable(context)) return
        loadingSpinner.visibility = View.GONE
        layoutHolder.visibility = View.VISIBLE
        assetName.text = asset.getName()
        assetDescription.text = asset.getDescription()

        if (assetDetails.visibility != View.GONE) {
            layoutHolder.setOnClickListener {
                if (layoutDetails.visibility == View.GONE) {
                    layoutDetails.visibility = View.VISIBLE
                    assetDetails.setImageResource(R.drawable.ic_expand_less_black)
                    imageView.setupTokenImage(asset)
                    imageView.visibility = View.VISIBLE
                    actionSheetInterface?.fullExpand()
                } else {
                    layoutDetails.visibility = View.GONE
                    assetDetails.setImageResource(R.drawable.ic_expand_more)
                    imageView.visibility = View.GONE
                }
            }
        }
    }

    /**
     * 从链上或远端拉取 NFT 元数据。
     */
    private suspend fun fetchAsset(token: Token, tokenId: String): NFTAsset? =
        withContext(Dispatchers.IO) {
            val bigIntegerId = tokenId.toBigIntegerOrNull() ?: return@withContext null
            runCatching {
                token.fetchTokenMetadata(bigIntegerId)
            }.getOrNull()
        }

    /**
     * 展开全部视图内容，供外部在需要时调用。
     */
    fun setFullyExpanded() {
        layoutDetails.visibility = View.VISIBLE
        assetDetails.visibility = View.GONE
        spacingLine.visibility = View.GONE
        layoutHolder.setOnClickListener(null)
    }

    /**
     * 视图销毁时取消未完成的异步任务，避免内存泄漏。
     */
    fun onDestroy() {
        fetchJob?.cancel()
        supervisorJob.cancelChildren()
    }

    /**
     * 视图脱离窗口时同步清理资源。
     */
    override fun onDetachedFromWindow() {
        onDestroy()
        super.onDetachedFromWindow()
    }
}
