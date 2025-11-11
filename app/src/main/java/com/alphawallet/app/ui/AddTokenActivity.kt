package com.alphawallet.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LongSparseArray
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.C
import com.alphawallet.app.C.ADDED_TOKEN
import com.alphawallet.app.C.RESET_WALLET
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.TokenFilter
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.ui.QRScanning.QRScannerActivity
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.app.ui.widget.adapter.TokensAdapter
import com.alphawallet.app.ui.widget.entity.AddressReadyCallback
import com.alphawallet.app.ui.widget.holder.TokenHolder.Companion.CHECK_MARK
import com.alphawallet.app.util.QRParser
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.AddTokenViewModel
import com.alphawallet.app.widget.AWBottomSheetDialog
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.AWalletAlertDialog.Companion.ERROR
import com.alphawallet.app.widget.FunctionButtonBar
import com.alphawallet.app.widget.InputAddress
import com.alphawallet.app.widget.TestNetDialog
import com.alphawallet.token.tools.ParseMagicLink
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.math.BigInteger
import java.util.regex.Pattern

/**
 * Activity that lets users scan for and add tokens to their wallet.
 */
@AndroidEntryPoint
class AddTokenActivity : BaseActivity(),
    AddressReadyCallback,
    StandardFunctionInterface,
    TokensAdapterCallback,
    TestNetDialog.TestNetDialogCallback {

    private lateinit var viewModel: AddTokenViewModel

    private val findAddress: Pattern = Pattern.compile("(0x)([0-9a-fA-F]{40})($|\\s)")
    private var lastCheck: String = ""

    private lateinit var progressLayout: LinearLayout
    private lateinit var counterLayout: LinearLayout
    private lateinit var counterText: TextView
    lateinit var inputAddressView: InputAddress
        private set

    private var contractAddress: String? = null
    private var networkInfo: NetworkInfo? = null
    private var currentResult: QRResult? = null

    private lateinit var adapter: TokensAdapter
    private lateinit var recyclerView: RecyclerView

    private var aDialog: AWalletAlertDialog? = null
    private var dialog: AWBottomSheetDialog? = null
    private val tokenList = LongSparseArray<Token>()

    /**
     * Sets up the UI, observers, and default network state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_token)

        toolbar()
        setTitle(getString(R.string.title_add_token))

        counterLayout = findViewById(R.id.layout_progress_counter)
        counterText = findViewById(R.id.text_check_counter)
        contractAddress = intent.getStringExtra(C.EXTRA_CONTRACT_ADDRESS)

        val functionBar = findViewById<FunctionButtonBar>(R.id.layoutButtons)
        functionBar.setupFunctions(this, arrayListOf(R.string.action_save))
        functionBar.revealButtons()

        progressLayout = findViewById(R.id.layout_progress)
        recyclerView = findViewById(R.id.list)
        progressLayout.visibility = View.GONE

        viewModel = ViewModelProvider(this)[AddTokenViewModel::class.java]
        viewModel.error().observe(this, this::onError)
        viewModel.switchNetwork().observe(this, this::setupNetwork)
        viewModel.chainScanCount().observe(this, this::onChainScanned)
        viewModel.onToken().observe(this, this::gotToken)
        viewModel.allTokens().observe(this, this::gotAllTokens)

        adapter = TokensAdapter(this, viewModel.getAssetDefinitionService(), viewModel.getTokensService(), null).apply {
            setHasStableIds(true)
            setFilterType(TokenFilter.NO_FILTER)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        inputAddressView = findViewById(R.id.input_address_view)
        inputAddressView.setAddressCallback(this)
        inputAddressView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val check = inputAddressView.inputText.lowercase().trim()
                if (check.length > 38) {
                    onCheck(check)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        setTitle(R.string.empty)
        setupNetwork(EthereumNetworkRepository.getOverrideTokenCompat().chainId)
        viewModel.prepare()

        intent.getStringExtra(C.EXTRA_QR_CODE)?.let {
            runOnUiThread {
                onActivityResult(C.BARCODE_READER_REQUEST_CODE, Activity.RESULT_OK, intent)
            }
        }

        intent.getStringExtra(C.EXTRA_ADDRESS)?.takeIf { it.isNotEmpty() }?.let {
            inputAddressView.setAddress(it)
        }
    }

    /**
     * Updates the adapter for a single token result.
     */
    private fun gotToken(token: Token) {
        val meta = TokenCardMeta(token, CHECK_MARK).apply { isEnabled = false }
        adapter.updateToken(meta)
    }

    /**
     * Populates the adapter when all token results are ready.
     */
    private fun gotAllTokens(tokens: Array<Token>) {
        val metas = tokens.map { token ->
            tokenList.put(token.tokenInfo.chainId, token)
            TokenCardMeta(token, CHECK_MARK)
        }
        adapter.setTokens(metas.toTypedArray())
        onChainScanned(0)
        if (tokens.isEmpty()) {
            onNoContractFound(true)
        }
    }

    /**
     * Updates the scan counter and hides progress when finished.
     */
    private fun onChainScanned(count: Int?) {
        counterText.text = (count ?: 0).toString()
        if (count == 0) {
            showProgress(false)
        }
    }

    /**
     * Re-applies any pending contract address from intents.
     */
    override fun onResume() {
        super.onResume()
        contractAddress?.let {
            inputAddressView.setAddress(it.lowercase())
            contractAddress = null
        }
    }

    /**
     * Shows or hides the progress indicator.
     */
    private fun showProgress(shouldShow: Boolean) {
        if (shouldShow) {
            progressLayout.visibility = View.VISIBLE
            counterLayout.visibility = View.VISIBLE
        } else {
            progressLayout.visibility = View.GONE
            counterLayout.visibility = View.GONE
        }
    }

    /**
     * Displays a dialog when camera permissions are denied.
     */
    private fun showCameraDenied() {
        aDialog = AWalletAlertDialog(this).apply {
            setTitle(R.string.title_dialog_error)
            setMessage(R.string.error_camera_permission_denied)
            setIcon(ERROR)
            setButtonText(R.string.button_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * Opens the send flow based on the parsed QR result.
     */
    private fun finishAndLaunchSend() {
        val result = currentResult ?: return
        val targetAddress = result.getAddress() ?: return
        if (!result.function.isNullOrEmpty()) {
            val token = viewModel.getToken(result.chainId, targetAddress)
            if (token == null) {
                showProgress(true)
                viewModel.fetchToken(result.chainId, targetAddress)
            } else {
                viewModel.showSend(this, result, token)
                finish()
            }
        } else {
            val walletAddress = viewModel.wallet().value?.address
            val baseToken = walletAddress?.let { viewModel.getToken(result.chainId, it) }
            if (baseToken != null) {
                viewModel.showSend(this, result, baseToken)
            }
            finish()
        }
    }

    /**
     * Completes the activity, returning the selected contract.
     */
    private fun onSaved() {
        showProgress(false)
        val selected = adapter.getSelected()
        if (selected.isNotEmpty()) {
            val result = selected[0]
            val locator = ContractLocator(result.address, result.chain)
            val intent = Intent().apply {
                putParcelableArrayListExtra(ADDED_TOKEN, arrayListOf(locator))
                putExtra(RESET_WALLET, true)
            }
            setResult(RESULT_OK, intent)
        }
        finish()
    }

    /**
     * Shows the generic add-token error dialog.
     */
    private fun onError(errorEnvelope: ErrorEnvelope?) {
        aDialog = AWalletAlertDialog(this).apply {
            setTitle(R.string.title_dialog_error)
            setMessage(R.string.error_add_token)
            setIcon(ERROR)
            setButtonText(R.string.try_again)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * Handles clicks from the FunctionButtonBar.
     */
    override fun handleClick(action: String?, id: Int) {
        onSave()
    }

    /**
     * Sanitises and validates the input address before querying networks.
     */
    private fun onCheck(addressInput: String) {
        var address = addressInput
        if (!Utils.isAddressValid(address)) {
            val matcher = findAddress.matcher(address)
            if (matcher.find()) {
                address = matcher.group(1) + matcher.group(2)
            }
        }
        if (Utils.isAddressValid(address) && address != lastCheck) {
            lastCheck = address
            showProgress(true)
            adapter.clear()
            viewModel.testNetworks(address)
        }
    }

    /**
     * Persists selected tokens and ensures their chains are enabled.
     */
    private fun onSave() {
        val activeChains = viewModel.ethereumNetworkRepository().getFilterNetworkList()
        val selected = adapter.getSelected()
        val chainsNotEnabled = HashSet<Long>()
        selected.forEach { token ->
            val info = viewModel.getNetworkInfo(token.chain)
            if (info != null && !activeChains.contains(info.chainId)) {
                chainsNotEnabled.add(info.chainId)
            }
        }

        viewModel.markTokensEnabled(selected)
        if (chainsNotEnabled.isEmpty()) {
            onSelectedChains(selected)
        } else {
            showAddChainsDialog()
        }
    }

    /**
     * Saves newly enabled tokens to the repository.
     */
    private fun onSelectedChains(selected: List<TokenCardMeta>) {
        val toSave = mutableListOf<Token>()
        selected.forEach { meta ->
            tokenList.get(meta.chain)?.let { toSave.add(it) }
        }
        viewModel.saveTokens(toSave)
        onSaved()
    }

    /**
     * Prompts the user to enable additional chains required by the selected tokens.
     */
    private fun showAddChainsDialog() {
        if (dialog?.isShowing == true) return
        dialog = AWBottomSheetDialog(this, object : AWBottomSheetDialog.Callback {
            override fun onClosed() = Unit
            override fun onCancelled() = Unit
            override fun onConfirmed() {
                viewModel.selectExtraChains(selectedChains)
                onSelectedChains(adapter.getSelected())
            }
        }).apply {
            setTitle(getString(R.string.enable_required_chains))
            setContent(getString(R.string.enable_required_chains_message))
            setConfirmButton(getString(R.string.dialog_ok))
            show()
        }
    }

    /**
     * Displays a dialog when no contract matches the searched address.
     */
    private fun onNoContractFound(@Suppress("UNUSED_PARAMETER") noContract: Boolean) {
        showProgress(false)
        aDialog = AWalletAlertDialog(this).apply {
            setTitle(R.string.no_token_found_title)
            setIcon(AWalletAlertDialog.NONE)
            setMessage(R.string.no_token_found)
            setButtonText(R.string.dialog_ok)
            setButtonListener { dismiss() }
            show()
        }
    }

    /**
     * Configures the primary chain for the lookup process.
     */
    private fun setupNetwork(chainId: Long) {
        networkInfo = viewModel.getNetworkInfo(chainId)
        networkInfo?.let { viewModel.setPrimaryChain(it.chainId) }
    }

    /**
     * Handles QR scan results, launching send flows or parsing magic links.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == C.BARCODE_READER_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val barcode = data?.getStringExtra(C.EXTRA_QR_CODE) ?: return
                    val parser = QRParser.getInstance(EthereumNetworkBase.extraChains())
                    currentResult = parser.parse(barcode)
                    var extractedAddress: String? = currentResult?.getAddress()
                    if (currentResult != null) {
                        when (currentResult?.getProtocol()) {
                            "address" -> Unit
                            "ethereum" -> {
                                if (currentResult?.chainId != 0L && !extractedAddress.isNullOrEmpty()) {
                                    finishAndLaunchSend()
                                    return
                                }
                            }
                        }
                    } else {
                        val magicParser = ParseMagicLink(CryptoFunctions(), EthereumNetworkRepository.extraChainsCompat())
                        try {
                            if (magicParser.parseUniversalLink(barcode).chainId > 0) {
                                viewModel.showImportLink(this, barcode)
                                finish()
                                return
                            }
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                    if (extractedAddress.isNullOrEmpty()) {
                        Toast.makeText(this, R.string.toast_qr_code_no_address, Toast.LENGTH_SHORT).show()
                    } else {
                        inputAddressView.setAddress(extractedAddress)
                    }
                }

                QRScannerActivity.DENY_PERMISSION -> showCameraDenied()

                else -> Timber.tag("SEND").e(getString(R.string.barcode_error_format, "Code: $resultCode"))
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Callback when the address view resolves; unused for manual entry.
     */
    override fun addressReady(address: String?, ensName: String?) = Unit

    /**
     * Stops any ongoing scans when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScan()
    }

    /**
     * Token click handler is unused in this context.
     */
    override fun onTokenClick(view: View, token: Token, tokenIds: List<BigInteger>, selected: Boolean) = Unit

    /**
     * Long click handler is unused in this context.
     */
    override fun onLongTokenClick(view: View, token: Token, tokenIds: List<BigInteger>) = Unit

    /**
     * Required callback for the TestNetDialog; unused.
     */
    override fun onTestNetDialogClosed() = Unit

    /**
     * Confirms the selection of extra chains from the dialog.
     */
    override fun onTestNetDialogConfirmed(chainId: Long) {
        viewModel.selectExtraChains(selectedChains)
        onSelectedChains(adapter.getSelected())
    }

    /**
     * TestNet dialog cancellation requires no action in this flow.
     */
    override fun onTestNetDialogCancelled() = Unit

    /**
     * Builds a list of chains represented by the selected tokens.
     */
    private val selectedChains: List<Long>
        get() {
            val selected = HashSet<Long>()
            adapter.getSelected().forEach { token ->
                viewModel.getNetworkInfo(token.chain)?.let { selected.add(it.chainId) }
            }
            return ArrayList(selected)
        }
}
