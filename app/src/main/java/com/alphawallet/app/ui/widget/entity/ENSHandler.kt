package com.alphawallet.app.ui.widget.entity

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.ui.widget.adapter.AutoCompleteAddressAdapter
import com.alphawallet.app.util.Utils
import com.alphawallet.app.util.ens.AWEnsResolver
import com.alphawallet.app.util.ens.EnsResolver
import com.alphawallet.app.widget.InputAddress
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.web3j.crypto.Keys

/**
 * Handles ENS lookups for [InputAddress] views, managing debounce timers and history.
 */
class ENSHandler(
    private val host: InputAddress,
    private val adapterUrl: AutoCompleteAddressAdapter
) : Runnable {

    private val handler = Handler(Looper.getMainLooper())
    private val ensResolver = AWEnsResolver(
        TokenRepository.getWeb3jService(EnsResolver.USE_ENS_CHAIN),
        host.context,
        host.chain
    )

    private var disposable: Disposable? = null
    @Volatile
    var waitingForENS: Boolean = false
        private set
    private var hostCallbackAfterENS = false
    var performEnsSync: Boolean = true

    init {
        createWatcher()
        getENSHistoryFromPrefs(host.context)
    }

    /**
     * Installs listeners on the host input view and clears previous state.
     */
    private fun createWatcher() {
        host.inputView.setAdapter(adapterUrl)
        host.inputView.setOnClickListener { host.inputView.showDropDown() }
        waitingForENS = false
    }

    /**
     * Debounces ENS lookups based on the current text value.
     */
    fun checkAddress() {
        handler.removeCallbacks(this)
        val input = host.inputText
        when {
            couldBeENS(input) -> {
                waitingForENS = true
                handler.postDelayed(this, ENS_RESOLVE_DELAY.toLong())
                disposable?.takeIf { !it.isDisposed }?.dispose()
                host.setWaitingSpinner(false)
            }

            Utils.isAddressValid(input) -> handler.post(this)

            else -> waitingForENS = false
        }
    }

    /**
     * Resolves the latest input and notifies the host when finished.
     */
    fun getAddress() {
        if (waitingForENS) {
            host.displayCheckingDialog(true)
            hostCallbackAfterENS = true
            handler.postDelayed({ checkIfWaitingForENS() }, ENS_TIMEOUT_DELAY.toLong())
        } else {
            val input = host.inputText
            if (Utils.isAddressValid(input) && host.status.isEmpty()) {
                val ensName = ensResolver.checkENSHistoryForAddress(input)
                if (ensName.isNotEmpty()) {
                    host.setStatus(ensName)
                }
            }
            host.ENSComplete()
        }
    }

    /**
     * Handles a previously cached ENS selection from the dropdown.
     */
    fun handleHistoryItemClick(ensName: String?) {
        host.hideKeyboard()
        host.inputView.removeTextChangedListener(host)
        host.inputView.setText(ensName)
        host.inputView.addTextChangedListener(host)
        host.inputView.dismissDropDown()
        handler.removeCallbacksAndMessages(this)
        waitingForENS = true
        handler.post(this)
    }

    /**
     * Called when ENS resolution succeeds, storing history and updating the host UI.
     */
    fun onENSSuccess(resolvedAddress: String, ensDomain: String) {
        waitingForENS = false
        host.setWaitingSpinner(false)
        when {
            Utils.isAddressValid(resolvedAddress) && canBeENSName(ensDomain) -> {
                host.inputView.dismissDropDown()
                host.setENSAddress(resolvedAddress)
                if (host.inputView.hasFocus()) host.hideKeyboard()
                storeItem(resolvedAddress, ensDomain)
                host.ENSResolved(resolvedAddress, ensDomain)
            }

            !TextUtils.isEmpty(resolvedAddress) && canBeENSName(resolvedAddress) &&
                Utils.isAddressValid(ensDomain) -> {
                host.inputView.dismissDropDown()
                host.setENSName(host.context.getString(R.string.ens_resolved, resolvedAddress))
                if (host.inputView.hasFocus()) host.hideKeyboard()
                storeItem(ensDomain, resolvedAddress)
                host.ENSResolved(ensDomain, resolvedAddress)
            }

            else -> host.setStatus(null)
        }
        checkIfWaitingForENS()
    }

    /**
     * Clears pending callbacks and signals completion when needed.
     */
    private fun checkIfWaitingForENS() {
        host.setWaitingSpinner(false)
        handler.removeCallbacksAndMessages(null)
        if (hostCallbackAfterENS) {
            disposable?.takeIf { !it.isDisposed }?.dispose()
            hostCallbackAfterENS = false
            host.ENSComplete()
        }
    }

    /**
     * Error callback for ENS lookups; resets state and clears statuses.
     */
    private fun onENSError(throwable: Throwable) {
        host.setWaitingSpinner(false)
        host.setStatus(null)
        checkIfWaitingForENS()
    }

    /**
     * Checks whether this string could plausibly be an ENS name.
     */
    fun couldBeENS(address: String?): Boolean {
        if (address.isNullOrEmpty()) return false
        val components = address.split(".")
        if (components.size > 1) {
            val extension = components.last()
            return extension.isNotEmpty() && Utils.isAlNum(extension)
        }
        return false
    }

    /**
     * Runnable entry point used for delayed ENS lookups.
     */
    override fun run() {
        val targetAddress = host.inputText
        disposable?.takeIf { !it.isDisposed }?.dispose()
        when {
            Utils.isAddressValid(targetAddress) -> {
                host.setWaitingSpinner(true)
                disposable = ensResolver.reverseResolveEns(targetAddress)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ resolved ->
                        if (resolved != EnsResolver.CANCELLED_REQUEST) {
                            onENSSuccess(resolved, targetAddress)
                        }
                    }, this::onENSError)
            }

            canBeENSName(targetAddress) -> {
                host.setWaitingSpinner(true)
                host.ENSName(targetAddress)
                disposable = ensResolver.resolveENSAddress(targetAddress)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ resolved ->
                        if (resolved != EnsResolver.CANCELLED_REQUEST) {
                            onENSSuccess(resolved, targetAddress)
                        }
                    }, this::onENSError)
            }

            else -> host.setWaitingSpinner(false)
        }
    }

    /**
     * Performs reverse lookup against the ENS resolver.
     */
    fun resolveENSNameFromAddress(address: String): Single<String> =
        ensResolver.reverseResolveEns(address)

    /**
     * Stores a resolved ENS pair in shared preferences.
     */
    private fun storeItem(address: String, ensName: String) {
        val history = getENSHistoryFromPrefs(host.context)
        if (!history.containsKey(address.lowercase())) {
            history[address.lowercase()] = ensName
            storeHistory(history)
        }
        adapterUrl.add(ensName)
    }

    /**
     * Persists the history map to preferences.
     */
    private fun storeHistory(history: HashMap<String, String>) {
        val historyJson = Gson().toJson(history)
        PreferenceManager.getDefaultSharedPreferences(host.context)
            .edit()
            .putString(C.ENS_HISTORY_PAIR, historyJson)
            .apply()
    }

    companion object {
        const val ENS_RESOLVE_DELAY = 750
        const val ENS_TIMEOUT_DELAY = 8000

        /**
         * Determines whether the input is a valid ENS-style domain.
         */
        @JvmStatic
        fun canBeENSName(address: String): Boolean {
            return !Utils.isAddressValid(address) &&
                !address.startsWith("0x") &&
                address.length > 5 &&
                address.contains(".") &&
                address.indexOf(".") <= address.length - 2
        }

        /**
         * Loads cached ENS history from shared preferences.
         */
        @JvmStatic
        fun getENSHistoryFromPrefs(context: Context): HashMap<String, String> {
            val historyJson = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(C.ENS_HISTORY_PAIR, "")
            return if (!historyJson.isNullOrEmpty()) {
                Gson().fromJson(
                    historyJson,
                    object : TypeToken<HashMap<String, String>>() {}.type
                )
            } else {
                HashMap()
            }
        }

        /**
         * Returns the ENS name for the address when known, else a formatted address.
         */
        @JvmStatic
        fun matchENSOrFormat(context: Context, ethAddress: String?): String {
            if (ethAddress.isNullOrEmpty()) return ""
            if (Utils.isAddressValid(ethAddress)) {
                val ensMap = getENSHistoryFromPrefs(context)
                val checksum = Keys.toChecksumAddress(ethAddress)
                val ensName = ensMap[ethAddress.lowercase()] ?: ensMap[checksum]
                return ensName ?: Utils.formatAddress(ethAddress)
            }
            return Utils.formatAddress(ethAddress)
        }

        /**
         * Displays either a known ENS entry or a (possibly shrunken) address.
         */
        @JvmStatic
        @JvmOverloads
        fun displayAddressOrENS(
            context: Context,
            ethAddress: String,
            shrinkAddress: Boolean = true
        ): String {
            var returnAddress = if (shrinkAddress) Utils.formatAddress(ethAddress) else ethAddress
            if (Utils.isAddressValid(ethAddress)) {
                val ensMap = getENSHistoryFromPrefs(context)
                returnAddress = ensMap[ethAddress] ?: returnAddress
            }
            return returnAddress
        }
    }

}
