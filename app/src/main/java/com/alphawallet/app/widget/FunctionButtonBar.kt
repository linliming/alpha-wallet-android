package com.alphawallet.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.BuyCryptoInterface
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.ItemClick
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.entity.UpdateType
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.OnRampRepositoryType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.ui.widget.NonFungibleAdapterInterface
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.GNOSIS_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.token.entity.TSAction
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * FunctionButtonBar is a custom view that displays a set of one, two, or more (via a "More" button)
 * action buttons. It is designed to handle TokenScript functions, standard token actions (send, receive),
 * and custom functions (buy, swap).
 *
 * It manages UI state, click handling, and asynchronous fetching of TokenScript function availability.
 */
class FunctionButtonBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), AdapterView.OnItemClickListener, TokensAdapterCallback {

    private val handler = Handler(Looper.getMainLooper())
    private val functionMapMutex = Mutex()
    private var isMapFetching = false
    private var functions: Map<String, TSAction>? = null
    private var adapter: NonFungibleAdapterInterface? = null
    private val selection = mutableListOf<BigInteger>()
    private var callStandardFunctions: StandardFunctionInterface? = null
    private var buyFunctionInterface: BuyCryptoInterface? = null
    private var buttonCount = 0
    private var token: Token? = null
    private var showButtons = false
    private var assetService: AssetDefinitionService? = null
    private var walletType = WalletType.NOT_DEFINED
    private var hasBuyFunction = false
    private var onRampRepository: OnRampRepositoryType? = null

    // Views
    private lateinit var primaryButton: MaterialButton
    private lateinit var secondaryButton: MaterialButton
    private lateinit var primaryButtonWrapper: RelativeLayout
    private lateinit var primaryButtonSpinner: ProgressBar
    private lateinit var moreButton: MaterialButton
    private lateinit var bottomSheet: BottomSheetDialog
    private lateinit var moreActionsListView: ListView
    private val moreActionsList = mutableListOf<ItemClick>()
    private lateinit var moreActionsAdapter: FunctionItemAdapter

    // Coroutine scope tied to the view's lifecycle
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        inflate(context, R.layout.layout_function_buttons, this)
        initializeViews()
    }

    /**
     * Initializes all child views, adapters, and the BottomSheetDialog.
     */
    private fun initializeViews() {
        primaryButton = findViewById(R.id.primary_button)
        primaryButtonWrapper = findViewById(R.id.primary_button_wrapper)
        primaryButtonSpinner = findViewById(R.id.primary_spinner)
        secondaryButton = findViewById(R.id.secondary_button)
        moreButton = findViewById(R.id.more_button)

        bottomSheet = BottomSheetDialog(context).apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.layout_more_actions, this, false)
        moreActionsListView = view.findViewById(R.id.list_view)
        moreActionsAdapter = FunctionItemAdapter(
            context,
            R.layout.item_action, moreActionsList
        )
        moreActionsListView.adapter = moreActionsAdapter
        bottomSheet.setContentView(view)
        moreActionsListView.onItemClickListener = this
    }

    /**
     * Resets all buttons to a hidden state and clears the "More" actions list.
     */
    private fun resetButtonCount() {
        buttonCount = 0
        primaryButtonWrapper.visibility = View.GONE
        secondaryButton.visibility = View.GONE
        moreButton.visibility = View.GONE
        moreActionsList.clear()
        moreActionsAdapter.notifyDataSetChanged()
    }

    /**
     * Sets up the button bar with a list of standard functions defined by string resources.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param functionResources A list of string resource IDs for button labels.
     */
    fun setupFunctions(
        functionInterface: StandardFunctionInterface,
        functionResources: MutableList<Int>
    ) {
        callStandardFunctions = functionInterface
        adapter = null
        functions = null
        resetButtonCount()
        functionResources.forEach { addFunction(it) }
        findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE
    }

    /**
     * Sets up the button bar for a single secondary-style function.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param functionNameResource The string resource ID for the button label.
     */
    fun setupSecondaryFunction(
        functionInterface: StandardFunctionInterface,
        functionNameResource: Int
    ) {
        callStandardFunctions = functionInterface
        adapter = null
        functions = null
        resetButtonCount()
        buttonCount = 1
        addFunction(functionNameResource)
        findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE
    }

    /**
     * Sets up the button bar for TokenScript functions related to a specific token.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param assetSvs The service for managing asset definitions.
     * @param token The token associated with these functions.
     * @param adp The adapter managing token selection (if any).
     * @param tokenIds The list of token IDs currently selected.
     */
    fun setupFunctions(
        functionInterface: StandardFunctionInterface,
        assetSvs: AssetDefinitionService,
        token: Token,
        adp: NonFungibleAdapterInterface?,
        tokenIds: List<BigInteger>
    ) {
        callStandardFunctions = functionInterface
        adapter = adp
        selection.clear()
        addTokenSelection(tokenIds)
        resetButtonCount()
        this.token = token
        functions = assetSvs.getTokenFunctionMap(token)
        assetService = assetSvs
        getFunctionMap(assetSvs, token.getInterfaceSpec())
    }

    /**
     * Sets up functions for the JavaScript (TokenScript) viewer.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param functionNameResource The string resource ID for the primary button.
     * @param token The token associated with these functions.
     * @param tokenIds The list of token IDs currently selected.
     */
    fun setupFunctionsForJsViewer(
        functionInterface: StandardFunctionInterface,
        functionNameResource: Int,
        token: Token,
        tokenIds: List<BigInteger>
    ) {
        callStandardFunctions = functionInterface
        adapter = null
        functions = null
        addTokenSelection(tokenIds)
        resetButtonCount()
        this.token = token
        addFunction(functionNameResource)
        this.addStandardTokenFunctions(token)
        findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE
    }

    /**
     * Sets up functions for an attestation.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param assetSvs The service for managing asset definitions.
     * @param token The attestation token.
     * @param adp The adapter managing token selection.
     * @param tokenIds The list of token IDs currently selected.
     */
    fun setupAttestationFunctions(
        functionInterface: StandardFunctionInterface,
        assetSvs: AssetDefinitionService,
        token: Token,
        adp: NonFungibleAdapterInterface,
        tokenIds: List<BigInteger>
    ) {
        callStandardFunctions = functionInterface
        adapter = adp
        selection.clear()
        addTokenSelection(tokenIds)
        resetButtonCount()
        this.token = token
        functions = assetSvs.getAttestationFunctionMap(token)
        assetService = assetSvs
        getFunctionMap(assetSvs, token.getInterfaceSpec())
    }

    /**
     * Use only for displaying a single TokenScript function.
     *
     * @param functionInterface The callback interface for standard actions.
     * @param functionName The name of the function to display.
     */
    fun setupFunctionList(functionInterface: StandardFunctionInterface, functionName: String) {
        callStandardFunctions = functionInterface
        if (functions == null) functions = HashMap()
        functions?.toMutableMap()?.clear() // Re-check this logic
        resetButtonCount()

        addFunction(functionName)
        functions = mapOf(functionName to TSAction()) // Assuming TSAction is the correct value

        findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE
    }

    /**
     * Adds intrinsic token functions (like Send, Receive) to the button list.
     *
     * @param token The token to get standard functions for.
     */
    private fun addStandardTokenFunctions(token: Token?) {
        token ?: return
        for (i in token.getStandardFunctions()) {
            addFunction(i)
        }
    }

    /**
     * Flags the buttons to be shown once the availability map is loaded.
     */
    fun revealButtons() {
        showButtons = true
    }

    /**
     * Internal click listener for the primary and secondary buttons.
     *
     * @param v The button view that was clicked.
     */
    private suspend fun onMainButtonClick(v: MaterialButton) {
        debounceButton(v)
        handleAction(ItemClick(v.text.toString(), v.id))
    }

    /**
     * Internal click listener for the "More" button.
     */
    private fun onMoreButtonClick() {
        bottomSheet.show()
    }

    /**
     * Adds a list of token IDs to the current selection.
     *
     * @param tokenIds The list of token IDs to add.
     */
    private fun addTokenSelection(tokenIds: List<BigInteger>?) {
        tokenIds?.forEach {
            if (!selection.contains(it)) {
                selection.add(it)
            }
        }
    }

    /**
     * Routes a user action (from a button click) to the correct handler.
     *
     * @param action The [ItemClick] object representing the action.
     */
    private suspend fun handleAction(action: ItemClick) {
        if (functions?.containsKey(action.buttonText) == true && action.buttonId == 0) {
            handleUseClick(action)
        } else if (action.buttonId == R.string.action_buy_crypto) {
            buyFunctionInterface?.handleBuyFunction(token)
        } else if (action.buttonId == R.string.generate_payment_request) {
            buyFunctionInterface?.handleGeneratePaymentRequest(token)
        } else {
            handleStandardFunctionClick(action)
        }
    }

    /**
     * Handles clicks for standard token functions (Send, Receive, Transfer, etc.).
     *
     * @param action The [ItemClick] object representing the action.
     */
    private fun handleStandardFunctionClick(action: ItemClick) {
        when (action.buttonId) {
            R.string.action_sell -> {
                if (isSelectionValid(action.buttonId)) {
                    callStandardFunctions?.sellTicketRouter(selection)
                }
            }
            R.string.action_send -> callStandardFunctions?.showSend()
            R.string.action_receive -> callStandardFunctions?.showReceive()
            R.string.action_transfer -> {
                if (isSelectionValid(action.buttonId)) {
                    callStandardFunctions?.showTransferToken(selection)
                }
            }
            R.string.action_use -> {
                if (isSelectionValid(action.buttonId)) {
                    callStandardFunctions?.selectRedeemTokens(selection)
                }
            }
            else -> callStandardFunctions?.handleClick(action.buttonText, action.buttonId)
        }
    }

    /**
     * Handles a click on a TokenScript-defined function.
     *
     * @param function The [ItemClick] object representing the function.
     */
    private suspend fun handleUseClick(function: ItemClick) {
        val actions = functions?.get(function.buttonText) ?: return
        val currentAssetService = assetService ?: return

        // First check for availability
        if (!TextUtils.isEmpty(actions.exclude)) {
            val denialMessage = token?.let {
                currentAssetService.checkFunctionDenied(
                    it,
                    function.buttonText,
                    selection
                )
            } as? String
            if (!TextUtils.isEmpty(denialMessage)) {
                callStandardFunctions?.handleFunctionDenied(denialMessage)
                return
            }
        }

        // Ensure we have sufficient tokens for selection
        if (!hasCorrectTokens(actions)) {
            callStandardFunctions?.displayTokenSelectionError(actions)
        } else {
            val selected = getSelectionFromAdapter()
            callStandardFunctions?.handleTokenScriptFunction(function.buttonText, selected)
        }
    }

    /**
     * Checks if the current token selection is valid for a given action.
     *
     * @param buttonId The resource ID of the action button.
     * @return True if the selection is valid, false otherwise.
     */
    private fun isSelectionValid(buttonId: Int): Boolean {
        val selected = getSelectionFromAdapter()
        return if (token == null || token?.checkSelectionValidity(selected) == true) {
            true
        } else {
            displayInvalidSelectionError()
            false
        }
    }

    /**
     * Retrieves the list of selected token IDs from the adapter, or the internal list if no adapter is present.
     *
     * @return A list of selected token IDs.
     */
    private fun getSelectionFromAdapter(): List<BigInteger> {
        return adapter?.getSelectedTokenIds(selection) ?: selection
    }

    /**
     * Checks if the number of selected tokens matches the requirement of the TokenScript action.
     *
     * @param action The [TSAction] being performed.
     * @return True if the token count is correct.
     */
    private fun hasCorrectTokens(action: TSAction): Boolean {
        val currentAdapter = adapter
        // Get selected tokens
        if (currentAdapter == null) {
            return (action.function?.tokenRequirement ?: 0) <= 1 // Can't use multi-token with no selection adapter.
        }

        val selected = currentAdapter.getSelectedTokenIds(selection)
        val groupings = currentAdapter.getSelectedGroups()
        val requiredCount = action.function?.tokenRequirement ?: 0

        if (requiredCount == 1 && selected.size > 1 && groupings == 1) {
            val first = getSelectedTokenId(selected)
            selected.clear()
            selected.add(first)
        }
        return selected.size == requiredCount
    }

    /**
     * Callback from [TokensAdapterCallback] when a token is clicked.
     */
    override fun onTokenClick(view: View, token: Token, tokenIds: List<BigInteger>, selected: Boolean) {
        if (!selected) return

        var maxSelect = 1

        if (functions != null) {
            // Wait for availability to complete
            scope.launch {
                waitForMapBuild()
                // This code runs after the map is built
                populateButtons(token, getSelectedTokenId(tokenIds))
            }

            functions?.let {
                for (action in it.values) {
                    action.function?.let { func ->
                        if (func.tokenRequirement > maxSelect) {
                            maxSelect = func.tokenRequirement
                        }
                    }
                }
            }

        }

        if (maxSelect <= 1) {
            selection.clear()
            addTokenSelection(tokenIds)
            adapter?.setRadioButtons(true)
        }
    }

    /**
     * Callback from [TokensAdapterCallback] when a token is long-clicked.
     */
    override fun onLongTokenClick(view: View, token: Token, tokenIds: List<BigInteger>) {
        // Show radio buttons of all token groups
        adapter?.setRadioButtons(true)
        selection.clear()
        addTokenSelection(tokenIds)

        // Vibrate
        val vb = ContextCompat.getSystemService(context, Vibrator::class.java)
        vb?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibe = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                it.vibrate(vibe)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(200)
            }
        }

        // Wait for availability to complete
        scope.launch {
            waitForMapBuild()
            // This code runs after the map is built
            populateButtons(token, getSelectedTokenId(tokenIds))
            showButtons()
        }
    }

    /**
     * Suspends if the function map is currently being fetched.
     * This is non-blocking and will resume execution once the fetch is complete.
     */
    private suspend fun waitForMapBuild() {
        if (isMapFetching) {
            callStandardFunctions?.showWaitSpinner(true)
            functionMapMutex.withLock {
                // This block will execute *after* the fetch is complete
                // and the mutex is released.
            }
            callStandardFunctions?.showWaitSpinner(false)
        }
    }

    /**
     * Displays a toast message for an invalid token selection.
     */
    private fun displayInvalidSelectionError() {
        Toast.makeText(context, "Invalid token selection", Toast.LENGTH_SHORT).show()
    }

    /**
     * Callback from [AdapterView.OnItemClickListener] for the "More" actions list.
     */
    override  fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        bottomSheet.dismiss()
        val action = moreActionsAdapter.getItem(position)
        action?.let {
            scope.launch {
                handleAction(it)
            }

        }
    }

    /**
     * Sets the text of the primary button.
     *
     * @param resource A string resource ID, or null to hide the button.
     */
    fun setPrimaryButtonText(resource: Int?) {
        if (resource != null) {
            primaryButtonWrapper.visibility = View.VISIBLE
            primaryButton.setText(resource)
        } else {
            primaryButtonWrapper.visibility = View.GONE
        }
    }

    /**
     * Sets the text of the secondary button.
     *
     * @param resource A string resource ID, or null to hide the button.
     */
    fun setSecondaryButtonText(resource: Int?) {
        if (resource != null) {
            secondaryButton.visibility = View.VISIBLE
            secondaryButton.setText(resource)
        } else {
            secondaryButton.visibility = View.GONE
        }
    }

    /**
     * Enables or disables the primary button.
     *
     * @param enabled True to enable, false to disable.
     */
    fun setPrimaryButtonEnabled(enabled: Boolean) {
        primaryButton.isEnabled = enabled
        if (enabled) primaryButtonSpinner.visibility = View.GONE
    }

    /**
     * Shows a spinner on the primary button to indicate a waiting state.
     */
    fun setPrimaryButtonWaiting() {
        primaryButton.isEnabled = false
        primaryButtonSpinner.visibility = View.VISIBLE
    }

    /**
     * Enables or disables the secondary button.
     *
     * @param enabled True to enable, false to disable.
     */
    fun setSecondaryButtonEnabled(enabled: Boolean) {
        secondaryButton.isEnabled = enabled
    }

    /**
     * Sets the [OnClickListener] for the primary button.
     */
    fun setPrimaryButtonClickListener(listener: OnClickListener) {
        primaryButton.setOnClickListener(listener)
    }

    /**
     * Sets the [OnClickListener] for the secondary button.
     */
    fun setSecondaryButtonClickListener(listener: OnClickListener) {
        secondaryButton.setOnClickListener(listener)
    }

    /**
     * Prevents rapid double-clicks on a button.
     *
     * @param v The view to debounce.
     */
    private fun debounceButton(v: View?) {
        v ?: return
        v.isEnabled = false
        handler.postDelayed({ v.isEnabled = true }, 500)
    }

    /**
     * Adds a function to the button bar, placing it as primary, secondary, or in the "More" list.
     *
     * @param function The [ItemClick] object representing the function.
     */
    private fun addFunction(function: ItemClick) {
        when (buttonCount) {
            0 -> {
                primaryButton.text = function.buttonText
                primaryButton.id = function.buttonId
                primaryButtonWrapper.visibility = View.VISIBLE
                primaryButton.setOnClickListener {
                    scope.launch {
                        onMainButtonClick(primaryButton)
                    }

                }
            }
            1 -> {
                secondaryButton.text = function.buttonText
                secondaryButton.id = function.buttonId
                secondaryButton.visibility = View.VISIBLE
                secondaryButton.setOnClickListener {
                    scope.launch {
                        onMainButtonClick(secondaryButton)
                    }

                }
            }
            else -> {
                moreActionsList.add(function)
                moreActionsAdapter.notifyDataSetChanged()
                moreButton.visibility = View.VISIBLE
                moreButton.setOnClickListener { onMoreButtonClick() }
            }
        }
        buttonCount++
    }

    /**
     * Adds a TokenScript function by name.
     *
     * @param function The name of the function.
     */
    private fun addFunction(function: String) {
        addFunction(ItemClick(function, 0))
    }

    /**
     * Adds a standard function by its string resource ID.
     *
     * @param resourceId The string resource ID.
     */
    private fun addFunction(resourceId: Int) {
        addFunction(ItemClick(context.getString(resourceId), resourceId))
    }

    /**
     * Sets the current wallet type (e.g., Watch, Keystore).
     *
     * @param type The [WalletType].
     */
    fun setWalletType(type: WalletType) {
        walletType = type
    }

    /**
     * Populates the button bar based on available TokenScript functions and standard functions.
     *
     * @param token The token to populate buttons for.
     * @param tokenId The specific token ID (for NFTs).
     */
    private fun populateButtons(token: Token?, tokenId: BigInteger) {
        token ?: return
        resetButtonCount()

        val availableFunctions = mutableMapOf<String, TSAction>()

        // TokenScript first:
        addTokenScriptFunctions(availableFunctions, token, tokenId)

        // If Token is Non-Fungible then display the custom functions first
        if (!token.isNonFungible() && token.getInterfaceSpec() != ContractType.ATTESTATION) {
            addStandardTokenFunctions(token)
        }

        setupCustomTokenActions()

        // Add buy function
        if (hasBuyFunction) {
            addBuyFunction()
        }

        // Now add the standard functions for NonFungibles (lower priority)
        if (token.isNonFungible()) {
            addStandardTokenFunctions(token)
        }

        findViewById<View>(R.id.layoutButtons).visibility = View.GONE

        if (!token.isNonFungible() && token.getInterfaceSpec() != ContractType.ATTESTATION) {
            addFunction(
                ItemClick(
                    context.getString(R.string.generate_payment_request),
                    R.string.generate_payment_request
                )
            )
        }
    }

    /**
     * Adds available TokenScript functions to the button list.
     *
     * @param availableFunctions The map to populate with available functions.
     * @param token The token.
     * @param tokenId The specific token ID.
     */
    private fun addTokenScriptFunctions(
        availableFunctions: MutableMap<String, TSAction>,
        token: Token,
        tokenId: BigInteger
    ) {
        val td = assetService?.getAssetDefinition(token)
        val currentFunctions = functions

        if (td != null && tokenId != null && currentFunctions != null) {
            for (actionName in currentFunctions.keys) {
                if (token.isFunctionAvailable(tokenId, actionName)) {
                    availableFunctions[actionName] = currentFunctions[actionName]!!
                }
            }
        } else if (currentFunctions != null) {
            availableFunctions.putAll(currentFunctions)
        }

        if (availableFunctions.isNotEmpty()) {
            val actions = SparseArray<String>()
            for (actionName in availableFunctions.keys) {
                actions.put(availableFunctions[actionName]!!.order, actionName)
            }

            for (i in 0 until actions.size()) {
                addFunction(ItemClick(actions.get(actions.keyAt(i)), 0))
            }
        }
    }

    /**
     * Adds custom hardcoded commands for known tokens (e.g., Swap, Convert to xDAI).
     *
     * @return True if a custom action was added, false otherwise.
     */
    private fun setupCustomTokenActions(): Boolean {
        val currentToken = token ?: return false

        if (currentToken.tokenInfo.chainId == POLYGON_ID && currentToken.isNonFungible() ||
            currentToken.getInterfaceSpec() == ContractType.ATTESTATION
        ) {
            return false
        }

        when (currentToken.tokenInfo.chainId) {
            MAINNET_ID -> {
                return when (currentToken.getAddress().lowercase()) {
                    C.DAI_TOKEN, C.SAI_TOKEN -> {
                        addFunction(R.string.convert_to_xdai)
                        true
                    }
                    else -> {
                        if (currentToken.isERC20() || currentToken.isEthereum()) {
                            addFunction(R.string.swap)
                        }
                        true
                    }
                }
            }
            BINANCE_MAIN_ID, OPTIMISTIC_MAIN_ID, ARBITRUM_MAIN_ID -> {
                if (currentToken.isERC20() || currentToken.isEthereum()) {
                    addFunction(R.string.swap)
                    return true
                }
            }
            POLYGON_ID -> {
                addFunction(R.string.swap_with_quickswap)
                return true
            }
        }
        return false
    }

    /**
     * Fetches the TokenScript function availability map asynchronously using coroutines.
     *
     * @param assetSvs The service for managing asset definitions.
     * @param type The contract type.
     */
    private fun getFunctionMap(assetSvs: AssetDefinitionService, type: ContractType) {
        findViewById<View>(R.id.wait_buttons).visibility = View.VISIBLE
        isMapFetching = true // Set flag

        scope.launch {
            functionMapMutex.withLock { // Lock the mutex
                try {
                    val availabilityMap = withContext(Dispatchers.IO) {
                        // Use .await() from kotlinx-coroutines-rx2 to bridge RxJava to Coroutines
                        token?.let {
                            assetSvs.fetchFunctionMap(
                                it,
                                selection,
                                type,
                                UpdateType.UPDATE_IF_REQUIRED
                            )
                        }
                    }
                    // Back on Main thread
                    token?.let {
                        if (availabilityMap != null) {
                            setupTokenMap(it, availabilityMap)
                        }
                    }
                } catch (e: Throwable) {
                    if (e !is CancellationException) {
                        onMapFetchError(e)
                    }
                } finally {
                    isMapFetching = false // Unset flag
                }
            }
        }
    }

    /**
     * Handles errors during the function map fetch.
     *
     * @param throwable The error that occurred.
     */
    private fun onMapFetchError(throwable: Throwable) {
        Timber.e(throwable)
        findViewById<View>(R.id.wait_buttons).visibility = View.GONE
        // Mutex is released automatically by withLock in getFunctionMap
    }

    /**
     * Gets the first token ID from a list, or BigInteger.ZERO if the list is empty.
     *
     * @param tokenIds The list of token IDs.
     * @return The first token ID.
     */
    private fun getSelectedTokenId(tokenIds: List<BigInteger>): BigInteger {
        return tokenIds.firstOrNull() ?: BigInteger.ZERO
    }

    /**
     * Stores the fetched availability map in the token and updates the UI.
     *
     * @param token The token.
     * @param availabilityMap The fetched availability map.
     */
    private fun setupTokenMap(
        token: Token,
        availabilityMap: Map<BigInteger, List<String>>
    ) {
        token.setFunctionAvailability(availabilityMap)
        findViewById<View>(R.id.wait_buttons).visibility = View.GONE
        // Mutex is released automatically

        if (showButtons) {
            val tokenId = getSelectedTokenId(selection)
            populateButtons(token, tokenId)
            showButtons()
        }

        callStandardFunctions?.completeFunctionSetup()
    }

    /**
     * Shows the button layout, respecting wallet type (Watch wallets have limited functionality).
     */
    private fun showButtons() {
        if (BuildConfig.DEBUG || walletType != WalletType.WATCH) {
            handler.post {
                findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE

                if (BuildConfig.DEBUG && walletType == WalletType.WATCH) {
                    findViewById<View>(R.id.text_debug).visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Enables the "Buy Crypto" function.
     *
     * @param buyCryptoInterface The callback interface for the buy action.
     * @param onRampRepository The repository for on-ramp contracts.
     */
    fun setupBuyFunction(
        buyCryptoInterface: BuyCryptoInterface,
        onRampRepository: OnRampRepositoryType
    ) {
        this.hasBuyFunction = true
        this.buyFunctionInterface = buyCryptoInterface
        this.onRampRepository = onRampRepository
    }

    /**
     * Adds the "Buy Crypto" function to the button list if applicable for the current chain.
     */
    private fun addBuyFunction() {
        val currentToken = token ?: return
        val currentRepo = onRampRepository ?: return
        if (currentToken.tokenInfo.chainId == MAINNET_ID ||
            currentToken.tokenInfo.chainId == GNOSIS_ID
        ) {
            addPurchaseVerb(currentToken, currentRepo)
        }
    }

    /**
     * Adds the "Buy" button with the correct token symbol.
     *
     * @param token The token to buy.
     * @param onRampRepository The repository for on-ramp contracts.
     */
    private fun addPurchaseVerb(token: Token, onRampRepository: OnRampRepositoryType) {
        val contract = onRampRepository.getContract(token)
        val symbol = if (contract.symbol.isEmpty()) {
            context.getString(R.string.crypto)
        } else {
            token.tokenInfo.symbol
        }
        addFunction(
            ItemClick(
                context.getString(R.string.action_buy_crypto, symbol),
                R.string.action_buy_crypto
            )
        )
    }

    /**
     * Cancels all running coroutines when the view is detached from the window.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    /**
     * Simple [ArrayAdapter] for displaying [ItemClick] actions in the "More" BottomSheet.
     * Implements the ViewHolder pattern for performance.
     */
    private class FunctionItemAdapter(
        context: Context,
        resource: Int,
        objects: List<ItemClick>
    ) : ArrayAdapter<ItemClick>(context, resource, 0, objects) {

        private val inflater: LayoutInflater = LayoutInflater.from(context)

        @SuppressLint("ViewHolder")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val holder: ViewHolder

            if (convertView == null) {
                view = inflater.inflate(R.layout.item_action, parent, false)
                holder = ViewHolder(view)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val item = getItem(position)
            holder.textView.text = item?.buttonText

            return view
        }

        private class ViewHolder(view: View) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }
    }
}

