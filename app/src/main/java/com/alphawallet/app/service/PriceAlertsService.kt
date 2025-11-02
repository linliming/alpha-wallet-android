package com.alphawallet.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.alphawallet.app.R
import com.alphawallet.app.entity.CurrencyItem
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.CurrencyRepository
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.router.TokenDetailRouter
import com.alphawallet.app.service.TickerService.Companion.getCurrencySymbolTxt
import com.alphawallet.app.service.TickerService.Companion.getCurrencyWithoutSymbol
import com.alphawallet.app.ui.widget.entity.PriceAlert
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Background service monitoring price alerts for tracked tokens.
 */
@AndroidEntryPoint
class PriceAlertsService : Service() {
    @Inject
    lateinit var preferenceRepository: PreferenceRepositoryType

    @Inject
    lateinit var tokensService: TokensService

    @Inject
    lateinit var tickerService: TickerService

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var tokenDetailRouter: TokenDetailRouter

    @Inject
    lateinit var genericWalletInteract: GenericWalletInteract

    @Inject
    lateinit var assetDefinitionService: AssetDefinitionService

    private var defaultWallet: Wallet? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var heartBeatJob: Job? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PriceAlertsService = this@PriceAlertsService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            try {
                val wallet = genericWalletInteract.find()
                onDefaultWallet(wallet)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startHeartBeatTimer()
        return START_STICKY
    }

    override fun onDestroy() {
        heartBeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onDefaultWallet(wallet: Wallet) {
        tokensService.setCurrentAddress(wallet.address)
        assetDefinitionService.startEventListener()
        defaultWallet = wallet
    }

    /**
     * Starts polling price alerts every 30 seconds if not already running.
     */
    private fun startHeartBeatTimer() {
        if (heartBeatJob?.isActive == true) return
        heartBeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    heartBeat()
                } catch (e: Exception) {
                    Timber.e(e)
                }
                delay(30_000L)
            }
        }
    }

    /**
     * Processes enabled price alerts against current market data.
     */
    private suspend fun heartBeat() {
        val wallet = defaultWallet ?: return
        val alerts = getEnabledPriceAlerts()
        if (alerts.isEmpty()) return
        var updated = false
        for (alert in alerts) {
            try {
                val rate = tickerService.convertPair(getCurrencySymbolTxt(), alert.currency)
                val token = tokensService.getToken(alert.chainId, alert.address) ?: continue
                val ticker = tokensService.getTokenTicker(token) ?: continue
                val currentPrice = ticker.price.toDoubleOrNull() ?: continue
                if (alert.match(rate, currentPrice)) {
                    val currency = CurrencyRepository.getCurrencyByISO(alert.currency) ?: continue
                    val content = constructContent(alert, currency)
                    notificationService.displayPriceAlertNotification(
                        alert.token,
                        content,
                        0,
                        constructIntent(token),
                    )
                    alert.isEnabled = false
                    updated = true
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        if (updated) {
            updatePriceAlerts(alerts)
        }
    }

    private fun getEnabledPriceAlerts(): MutableList<PriceAlert> {
        val enabled = ArrayList<PriceAlert>()
        for (alert in getPriceAlerts()) {
            if (alert.isEnabled) enabled.add(alert)
        }
        return enabled
    }

    private fun getPriceAlerts(): MutableList<PriceAlert> {
        val json = preferenceRepository.priceAlerts.orEmpty()
        if (json.isEmpty()) return mutableListOf()
        val type = object : TypeToken<MutableList<PriceAlert>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun updatePriceAlerts(priceAlerts: List<PriceAlert>) {
        val type = object : TypeToken<List<PriceAlert>>() {}.type
        preferenceRepository.priceAlerts = Gson().toJson(priceAlerts, type)
    }

    private fun constructIntent(token: Token): Intent {
        val wallet = defaultWallet ?: return Intent()
        val hasDefinition = assetDefinitionService.hasDefinition(token)
        return tokenDetailRouter.makeERC20DetailsIntent(
            this,
            token.getAddress(),
            token.tokenInfo.symbol,
            token.tokenInfo.decimals,
            !token.isEthereum(),
            wallet,
            token,
            hasDefinition,
        )
    }

    private fun constructContent(alert: PriceAlert, currencyItem: CurrencyItem): String {
        val indicator = getIndicatorText(alert.above)
        val threshold = alert.value.toDoubleOrNull()
        val formattedValue = threshold?.let { getCurrencyWithoutSymbol(it) }
            ?: alert.value
        return "$indicator ${currencyItem.symbol}$formattedValue"
    }

    private fun getIndicatorText(isAbove: Boolean): String {
        return if (isAbove) {
            getString(R.string.price_alert_indicator_above)
        } else {
            getString(R.string.price_alert_indicator_below)
        }
    }
}
