package com.alphawallet.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.widget.StandardHeader
import com.alphawallet.app.widget.TestNetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import javax.inject.Inject

/**
 * Base activity that manages the mainnet/testnet list UI and the enable-testnet dialog flow.
 */
abstract class NetworkBaseActivity : BaseActivity(), TestNetDialog.TestNetDialogCallback {

    protected lateinit var mainnetRecyclerView: RecyclerView
    protected lateinit var testnetRecyclerView: RecyclerView
    protected lateinit var mainnetHeader: StandardHeader
    protected lateinit var testnetHeader: StandardHeader
    protected lateinit var testnetSwitch: SwitchMaterial
    protected lateinit var testnetDialog: TestNetDialog

    @Inject
    lateinit var preferenceRepositoryType: PreferenceRepositoryType

    /**
     * Inflates the layout and initialises toolbar/controls.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_network)
        toolbar()
        initViews()
        setDispatcher()
    }

    /**
     * Adds the action bar menu (add network / node status).
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_select_network_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Handles toolbar item clicks (home/add network/node status).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> handleSetNetworks()
            R.id.action_add -> startActivity(Intent(this, AddCustomRPCNetworkActivity::class.java))
            R.id.action_node_status -> startActivity(Intent(this, NodeStatusActivity::class.java))
            else -> return false
        }
        return true
    }

    /**
     * Overrides back navigation to ensure network selections are persisted.
     */
    override fun handleBackPressed() {
        handleSetNetworks()
    }

    /**
     * Called when the user navigates away, allowing subclasses to persist selections.
     */
    protected abstract fun handleSetNetworks()

    /**
     * Creates the testnet confirmation dialog.
     */
    protected fun initTestNetDialog(callback: TestNetDialog.TestNetDialogCallback) {
        testnetDialog = TestNetDialog(this, 0, callback)
    }

    /**
     * Initialises headers, lists, switch, and dialog wiring.
     */
    protected fun initViews() {
        mainnetHeader = findViewById(R.id.mainnet_header)
        testnetHeader = findViewById(R.id.testnet_header)
        testnetSwitch = testnetHeader.switch
        mainnetRecyclerView = findViewById(R.id.main_list)
        testnetRecyclerView = findViewById(R.id.test_list)

        mainnetRecyclerView.layoutManager = LinearLayoutManager(this)
        testnetRecyclerView.layoutManager = LinearLayoutManager(this)

        testnetSwitch.setOnClickListener {
            if (testnetSwitch.isChecked) {
                testnetDialog.show()
            } else {
                toggleListVisibility(false)
            }
        }

        val testnetEnabled = preferenceRepositoryType.isTestnetEnabled
        testnetSwitch.isChecked = testnetEnabled
        toggleListVisibility(testnetEnabled)
        initTestNetDialog(this)
    }

    /**
     * Provides subclasses with a hook to update headers when visibility changes.
     */
    protected open fun updateTitle() = Unit

    /**
     * Hides the testnet toggle header, for flows that don't need it.
     */
    protected fun hideSwitch() {
        testnetHeader.visibility = View.GONE
    }

    /**
     * Shows or hides the testnet list based on the toggle state.
     */
    protected fun toggleListVisibility(testnetChecked: Boolean) {
        testnetRecyclerView.visibility = if (testnetChecked) View.VISIBLE else View.GONE
        updateTitle()
    }

    /**
     * Callback when the dialog closes without confirmation.
     */
    override fun onTestNetDialogClosed() {
        testnetSwitch.isChecked = false
    }

    /**
     * Callback when the dialog is confirmed; reveals the testnet list.
     */
    override fun onTestNetDialogConfirmed(chainId: Long) {
        toggleListVisibility(true)
    }

    /**
     * Callback when the dialog is cancelled.
     */
    override fun onTestNetDialogCancelled() {
        testnetSwitch.isChecked = false
    }
}
