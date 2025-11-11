package com.alphawallet.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.coinbasepay.DestinationWallet
import com.alphawallet.app.viewmodel.CoinbasePayViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CoinbasePayActivity : BaseActivity() {
    private var viewModel: CoinbasePayViewModel? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_coinbase_pay)

        toolbar()

        setTitle(getString(R.string.title_buy_with_coinbase_pay))

        initViewModel()

        initWebView()

        viewModel!!.track(Analytics.Navigation.COINBASE_PAY)

        viewModel!!.prepare()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = findViewById<WebView>(R.id.web_view)
        webView!!.setWebViewClient(WebViewClient())
        webView!!.getSettings().setJavaScriptEnabled(true)
        webView!!.getSettings().setJavaScriptCanOpenWindowsAutomatically(true)
        webView!!.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE)
        webView!!.clearCache(true)
        webView!!.clearHistory()
    }

    private fun initViewModel() {
        viewModel =
            ViewModelProvider(this).get<CoinbasePayViewModel>(CoinbasePayViewModel::class.java)
        viewModel!!.defaultWallet()
            .observe(this, Observer { wallet: Wallet? -> this.onDefaultWallet(wallet!!) })
    }

    private fun onDefaultWallet(wallet: Wallet) {
        val type: DestinationWallet.Type?
        val list: MutableList<String?> = ArrayList<String?>()
        val asset = getIntent().getStringExtra("asset")
        if (!TextUtils.isEmpty(asset)) {
            type = DestinationWallet.Type.ASSETS
            list.add(asset)
        } else {
            type = DestinationWallet.Type.BLOCKCHAINS
            val blockchain = getIntent().getStringExtra("blockchain")
            list.add(blockchain)
        }

        val uri = viewModel!!.getUri(type, wallet.address, list)
        if (TextUtils.isEmpty(uri)) {
            Toast.makeText(this, "Missing Coinbase Pay App ID.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            webView!!.loadUrl(uri!!)
        }
    }

    override fun onBackPressed() {
        webView!!.clearCache(true)
        super.onBackPressed()
        overridePendingTransition(R.anim.hold, R.anim.slide_out_right)
    }
}
