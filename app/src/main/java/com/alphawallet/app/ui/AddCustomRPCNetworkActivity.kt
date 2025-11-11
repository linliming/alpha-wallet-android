package com.alphawallet.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.viewmodel.CustomNetworkViewModel
import com.alphawallet.app.widget.FunctionButtonBar
import com.alphawallet.app.widget.InputView
import com.google.android.material.checkbox.MaterialCheckBox
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for adding or editing custom RPC networks.
 */
@AndroidEntryPoint
class AddCustomRPCNetworkActivity : BaseActivity(), StandardFunctionInterface {

    companion object {
        const val CHAIN_ID = "chain_id"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var viewModel: CustomNetworkViewModel
    private lateinit var nameInputView: InputView
    private lateinit var rpcUrlInputView: InputView
    private lateinit var chainIdInputView: InputView
    private lateinit var symbolInputView: InputView
    private lateinit var blockExplorerUrlInputView: InputView
    private lateinit var blockExplorerApiUrl: InputView
    private lateinit var testNetCheckBox: MaterialCheckBox

    private var chainId: Long = -1
    private var isEditMode: Boolean = false

    /**
     * Sets up the toolbar, inputs, and view model observers.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_custom_rpc_network)
        toolbar()
        setTitle(R.string.title_activity_add_custom_rpcnetwork)

        initViews()
        initViewModel()

        chainId = intent.getLongExtra(CHAIN_ID, -1)
        isEditMode = chainId >= 0
        if (isEditMode) {
            setTitle(R.string.title_network_info)
            val network = viewModel.getNetworkInfo(chainId)
            renderNetwork(network)
            val buttons = mutableListOf(R.string.action_update_network)
            if (network != null && !network.isCustom) {
                chainIdInputView.getEditText().isEnabled = false
                buttons.add(R.string.action_reset_network)
            }
            setupFunctionBar(buttons)
        } else {
            setupFunctionBar(mutableListOf(R.string.action_add_network))
        }
    }

    /**
     * Tracks analytics navigation when activity resumes.
     */
    override fun onResume() {
        super.onResume()
        viewModel.track(Analytics.Navigation.ADD_CUSTOM_NETWORK)
    }

    /**
     * Handles button clicks for adding/updating/resetting networks.
     */
    /**
     * Responds to function bar actions such as add, update, or reset network.
     */
    override fun handleClick(action: String?, actionId: Int) {
        when (actionId) {
            R.string.action_reset_network -> resetDefault()
            else -> {
                if (validateInputs()) {
                    viewModel.saveNetwork(
                        isEditMode,
                        nameInputView.getText().toString(),
                        rpcUrlInputView.getText().toString(),
                        chainIdInputView.getText().toString().toLong(),
                        symbolInputView.getText().toString(),
                        blockExplorerUrlInputView.getText().toString(),
                        blockExplorerApiUrl.getText().toString(),
                        testNetCheckBox.isChecked(),
                        if (isEditMode) chainId else null
                    )
                    finish()
                } else {
                    handler.postDelayed({ resetValidationErrors() }, 2000)
                }
            }
        }
    }

    /**
     * Builds the view model instance.
     */
    /**
     * Builds the view model required for custom-network actions.
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[CustomNetworkViewModel::class.java]
    }

    /**
     * Configures input fields and checkboxes.
     */
    /**
     * Initialises all input widgets and their IME behaviours.
     */
    private fun initViews() {
        nameInputView = findViewById(R.id.input_network_name)
        rpcUrlInputView = findViewById(R.id.input_network_rpc_url)
        chainIdInputView = findViewById(R.id.input_network_chain_id)
        symbolInputView = findViewById(R.id.input_network_symbol)
        blockExplorerUrlInputView = findViewById(R.id.input_network_block_explorer_url)
        blockExplorerApiUrl = findViewById(R.id.input_network_explorer_api)
        testNetCheckBox = findViewById(R.id.checkbox_testnet)

        nameInputView.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        rpcUrlInputView.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        chainIdInputView.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        symbolInputView.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        blockExplorerUrlInputView.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://etherscan.com/tx/"
        }
        blockExplorerApiUrl.getEditText().apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://api.etherscan.io/api?"
        }
    }

    /**
     * Populates controls with existing network information.
     */
    /**
     * Renders an existing network into the form when editing.
     */
    private fun renderNetwork(network: NetworkInfo?) {
        network ?: return
        nameInputView.setText(network.name)
        rpcUrlInputView.setText(
            network.rpcServerUrl.replace(
                Regex("(/)([0-9a-fA-F]{32})"),
                "/********************************"
            )
        )
        chainIdInputView.setText(network.chainId.toString())
        symbolInputView.setText(network.symbol)
        blockExplorerUrlInputView.setText(network.etherscanUrl)
        blockExplorerApiUrl.setText(network.etherscanAPI)
        testNetCheckBox.isChecked = viewModel.isTestNetwork(network)
    }

    /**
     * Validates all fields according to required criteria.
     */
    private fun validateInputs(): Boolean {
        if (TextUtils.isEmpty(nameInputView.getText())) {
            nameInputView.setError(getString(R.string.error_field_required))
            return false
        }
        if (TextUtils.isEmpty(rpcUrlInputView.getText())) {
            rpcUrlInputView.setError(getString(R.string.error_field_required))
            return false
        } else if (!URLUtil.isValidUrl(rpcUrlInputView.getText().toString())) {
            rpcUrlInputView.setError(getString(R.string.error_invalid_url))
            return false
        }
        if (TextUtils.isEmpty(chainIdInputView.getText())) {
            chainIdInputView.setError(getString(R.string.error_field_required))
            return false
        } else {
            try {
                chainIdInputView.getText().toString().toLong()
            } catch (ex: NumberFormatException) {
                chainIdInputView.setError(getString(R.string.error_must_numeric))
                return false
            }
        }
        val newChainId = chainIdInputView.getText().toString().toLong()
        val currentChainId = intent.getLongExtra(CHAIN_ID, -1)
        if (newChainId != currentChainId && viewModel.getNetworkInfo(newChainId) != null) {
            chainIdInputView.setError(getString(R.string.error_chainid_already_taken))
            return false
        }
        if (TextUtils.isEmpty(symbolInputView.getText())) {
            symbolInputView.setError(getString(R.string.error_field_required))
            return false
        }
        if (!TextUtils.isEmpty(blockExplorerUrlInputView.getText()) &&
            !URLUtil.isValidUrl(blockExplorerUrlInputView.getText().toString())
        ) {
            blockExplorerUrlInputView.setError(getString(R.string.error_invalid_url))
            return false
        }
        if (!TextUtils.isEmpty(blockExplorerApiUrl.getText()) &&
            !URLUtil.isValidUrl(blockExplorerApiUrl.getText().toString())
        ) {
            blockExplorerApiUrl.setError(getString(R.string.error_invalid_url))
            return false
        }
        return true
    }

    /**
     * Resets validation errors after a short delay.
     */
    /**
     * Clears all validation error messages from the form.
     */
    private fun resetValidationErrors() {
        nameInputView.setError(null)
        rpcUrlInputView.setError(null)
        chainIdInputView.setError(null)
        symbolInputView.setError(null)
        blockExplorerUrlInputView.setError(null)
        blockExplorerApiUrl.setError(null)
    }

    /**
     * Resets fields to the default chain values.
     */
    /**
     * Restores original values for built-in networks when reset is requested.
     */
    private fun resetDefault() {
        renderNetwork(viewModel.getBuiltInNetwork(chainId))
    }

    /**
     * Displays action buttons for the provided string resources.
     */
    /**
     * Configures the function button bar with the provided actions.
     */
    private fun setupFunctionBar(functions: MutableList<Int>) {
        val functionBar = findViewById<FunctionButtonBar>(R.id.layoutButtons)
        functionBar.setupFunctions(this, functions)
        functionBar.revealButtons()
    }
}
