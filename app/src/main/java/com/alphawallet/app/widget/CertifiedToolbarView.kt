package com.alphawallet.app.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.MainThread
import androidx.core.view.isVisible
import com.alphawallet.app.R
import com.alphawallet.token.entity.SigReturnType
import com.alphawallet.token.entity.XMLDsigDescriptor
import com.google.android.material.appbar.MaterialToolbar

/**
 * 展示 TokenScript 证书状态的工具栏组件，负责锁标识与同步指示器的显示。
 */
class CertifiedToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialToolbar(context, attrs) {

    private var hostActivity: Activity? = null
    private var detailsDialog: AWalletAlertDialog? = null
    private val downloadSpinner: ProgressBar
    private val syncSpinner: ProgressBar
    private var lockResource: Int = 0

    init {
        inflate(context, R.layout.layout_certified_toolbar, this)
        downloadSpinner = findViewById(R.id.cert_progress_spinner)
        syncSpinner = findViewById(R.id.nft_scan_spinner)
    }

    /**
     * 根据签名数据更新锁标识与点击行为。
     */
    @MainThread
    fun onSigData(sigData: XMLDsigDescriptor, activity: Activity) {
        hostActivity = activity
        val lockStatus = findViewById<ImageView>(R.id.image_lock)
        lockStatus.visibility = View.VISIBLE
        lockStatus.setOnClickListener { showCertificateDetails(sigData) }

        val type = sigData.type ?: SigReturnType.NO_TOKENSCRIPT
        downloadSpinner.visibility = View.GONE
        lockResource = when (type) {
            SigReturnType.NO_TOKENSCRIPT -> {
                lockStatus.visibility = View.GONE
                0
            }
            SigReturnType.DEBUG_NO_SIGNATURE,
            SigReturnType.DEBUG_SIGNATURE_INVALID -> R.mipmap.ic_unlocked_debug
            SigReturnType.DEBUG_SIGNATURE_PASS -> R.mipmap.ic_locked_debug
            SigReturnType.NO_SIGNATURE,
            SigReturnType.SIGNATURE_INVALID -> R.mipmap.ic_unverified
            SigReturnType.SIGNATURE_PASS -> R.mipmap.ic_locked
        }

        if (lockResource != 0) {
            lockStatus.setImageResource(lockResource)
        }
    }

    /**
     * 隐藏锁图标及下载动画，供外部在必要时调用。
     */
    fun hideCertificateResource() {
        findViewById<ImageView>(R.id.image_lock).visibility = View.GONE
        stopDownload()
    }

    /**
     * 显示证书详情弹窗。
     */
    private fun showCertificateDetails(sigData: XMLDsigDescriptor) {
        val activity = hostActivity ?: return
        if (detailsDialog?.isShowing == true) detailsDialog?.cancel()
        val dialog = AWalletAlertDialog(activity).also { detailsDialog = it }
        dialog.setIcon(lockResource)
        dialog.setTitle(R.string.signature_details)
        downloadSpinner.visibility = View.GONE

        val message = if (sigData.issuer == null) {
            "Tokenscript is not signed"
        } else {
            buildString {
                append("Issuer: ")
                append(sigData.issuer)
                append("\n\nCertifier: ")
                append(sigData.certificateName)
                append("\n\nKey Type: ")
                append(sigData.keyType)
                append("\n\nKey Owner: ")
                append(sigData.keyName)
            }
        }

        dialog.setTextStyle(AWalletAlertDialog.TextStyle.LEFT)
        dialog.setMessage(message)
        dialog.setButtonText(R.string.button_ok)
        dialog.setButtonListener { dialog.dismiss() }
        dialog.setCancelable(true)
        dialog.show()
    }

    /**
     * 显示 TokenScript 下载中的进度指示。
     */
    fun startDownload() {
        if (lockResource == 0) {
            downloadSpinner.visibility = View.VISIBLE
        }
    }

    /**
     * 隐藏 TokenScript 下载进度。
     */
    fun stopDownload() {
        downloadSpinner.visibility = View.GONE
    }

    /**
     * 展示 NFT 同步动画。
     */
    fun showNFTSync() {
        syncSpinner.isVisible = true
    }

    /**
     * NFT 同步完成时隐藏动画。
     */
    fun nftSyncComplete() {
        syncSpinner.isVisible = false
    }

    override fun onDetachedFromWindow() {
        detailsDialog?.dismiss()
        detailsDialog = null
        hostActivity = null
        super.onDetachedFromWindow()
    }
}
