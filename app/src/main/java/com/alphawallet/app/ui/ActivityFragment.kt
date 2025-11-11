package com.alphawallet.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.interact.ActivityDataInteract
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.repository.entity.RealmTransfer
import com.alphawallet.app.ui.widget.adapter.ActivityAdapter
import com.alphawallet.app.ui.widget.entity.TokenTransferData
import com.alphawallet.app.util.LocaleUtils
import com.alphawallet.app.viewmodel.ActivityViewModel
import com.alphawallet.app.widget.EmptyTransactionsView
import com.alphawallet.app.widget.SystemView
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults

/**
 * Fragment that renders the wallet activity list and listens for Realm-backed updates.
 */
@AndroidEntryPoint
class ActivityFragment : BaseFragment(), View.OnClickListener, ActivityDataInteract {

    private val handler = Handler(Looper.getMainLooper())
    private var viewModel: ActivityViewModel? = null
    private var systemView: SystemView? = null
    private var adapter: ActivityAdapter? = null
    private var listView: RecyclerView? = null
    private var realmUpdates: RealmResults<RealmTransaction>? = null
    private var checkTimer = false
    private var realm: Realm? = null
    private var lastUpdateTime = 0L
    private var isVisibleToUser = false

    /**
     * Inflates the activity layout, initialises the toolbar, view model, and list UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocaleUtils.setActiveLocale(requireContext())
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)
        toolbar(view)
        setToolbarTitle(R.string.activity_label)
        initViewModel()
        initViews(view)
        return view
    }

    /**
     * Lazily initialises the [ActivityViewModel] and hooks LiveData observers.
     */
    private fun initViewModel() {
        if (viewModel == null) {
            viewModel = ViewModelProvider(this)[ActivityViewModel::class.java]
            viewModel?.defaultWallet()?.observe(viewLifecycleOwner, this::onDefaultWallet)
            viewModel?.activityItems()?.observe(viewLifecycleOwner, this::onItemsLoaded)
        }
    }

    /**
     * Called whenever the activity feed changes, refreshing the adapter contents.
     */
    private fun onItemsLoaded(activityItems: Array<ActivityMeta>) {
        viewModel?.getRealmInstance()?.use { realmInstance ->
            val filtered = buildTransactionList(realmInstance, activityItems)
            adapter?.updateActivityItems(filtered.toTypedArray())
            showEmptyTx()
            activityItems.filterIsInstance<TransactionMeta>()
                .filter { it.timeStampSeconds > lastUpdateTime }
                .forEach { lastUpdateTime = it.timeStampSeconds - 60 }
        }
        if (isVisibleToUser) {
            startTxListener()
        }
    }

    /**
     * Starts a Realm change listener to observe incoming transactions.
     */
    private fun startTxListener() {
        val currentWallet = viewModel?.defaultWallet()?.value ?: return
        if (currentWallet.address.isNullOrEmpty()) return
        if (realm == null || realm?.isClosed == true) {
            realm = viewModel?.getRealmInstance()
        }
        realmUpdates?.removeAllChangeListeners()
        val realmInstance = realm ?: return

        realmUpdates = realmInstance.where(RealmTransaction::class.java)
            .greaterThan("timeStamp", lastUpdateTime)
            .findAllAsync()

        realmUpdates?.addChangeListener { transactions ->
            if (transactions.isEmpty()) return@addChangeListener

            val metas = mutableListOf<TransactionMeta>()
            transactions.forEach { item ->
                if (viewModel?.tokensService?.getNetworkFilters()?.contains(item.chainId) == true) {
                    val meta = TransactionMeta(item.hash, item.timeStamp, item.to, item.chainId, item.blockNumber)
                    metas.add(meta)
                    lastUpdateTime = meta.timeStampSeconds + 1
                }
            }

            if (metas.isNotEmpty()) {
                val realmRef = realm ?: return@addChangeListener
                val filtered = buildTransactionList(realmRef, metas.toTypedArray())
                adapter?.updateActivityItems(filtered.toTypedArray())
                systemView?.hide()
            }
        }
    }

    /**
     * Builds the transaction display list by expanding token transfers.
     */
    private fun buildTransactionList(realm: Realm, activityItems: Array<ActivityMeta>): List<ActivityMeta> {
        val filteredList = mutableListOf<ActivityMeta>()
        activityItems.forEach { meta ->
            if (meta is TransactionMeta) {
                val tokenTransfers = getTokenTransfersForHash(realm, meta)
                if (tokenTransfers.size != 1) {
                    filteredList.add(meta)
                }
                filteredList.addAll(tokenTransfers)
            }
        }
        return filteredList
    }

    /**
     * Loads token transfer data for the supplied transaction hash.
     */
    private fun getTokenTransfersForHash(realm: Realm, tm: TransactionMeta): List<TokenTransferData> {
        val transferData = mutableListOf<TokenTransferData>()
        val transfers = realm.where(RealmTransfer::class.java)
            .equalTo("hash", RealmTransfer.databaseKey(tm.chainId, tm.hash?:""))
            .findAll()

        if (transfers != null && transfers.isNotEmpty()) {
            var nextTransferTime = if (transfers.size == 1) tm.timeStamp else tm.timeStamp - 1
            transfers.forEach { rt ->
                transferData.add(
                    TokenTransferData(
                        rt.getHash(),
                        tm.chainId,
                        rt.getTokenAddress(),
                        rt.getEventName(),
                        rt.getTransferDetail(),
                        nextTransferTime
                    )
                )
                nextTransferTime--
            }
        }
        return transferData
    }

    /**
     * Sets up the recycler view, adapter, and swipe-to-refresh interactions.
     */
    private fun initViews(view: View) {
        val vm = viewModel ?: return
        adapter = ActivityAdapter(
            vm.getTokensService(),
            vm.provideTransactionsInteract(),
            vm.getAssetDefinitionService(),
            this
        )
        val refreshLayout: SwipeRefreshLayout = view.findViewById(R.id.refresh_layout)
        systemView = view.findViewById(R.id.system_view)
        listView = view.findViewById(R.id.list)
        listView?.layoutManager = LinearLayoutManager(requireContext())
        listView?.adapter = adapter
        listView?.addRecyclerListener { holder -> adapter?.onRViewRecycled(holder) }

        systemView?.attachRecyclerView(listView)
        systemView?.attachSwipeRefreshLayout(refreshLayout)
        systemView?.showProgress(false)

        refreshLayout.setOnRefreshListener {
            refreshTransactionList()
            refreshLayout.isRefreshing = false
        }
    }

    /**
     * Updates the adapter with the selected wallet.
     */
    private fun onDefaultWallet(wallet: Wallet) {
        adapter?.setDefaultWallet(wallet)
    }

    /**
     * Shows or hides the empty transactions state view.
     */
    private fun showEmptyTx() {
        if (adapter?.isEmpty == true) {
            val emptyView = EmptyTransactionsView(requireContext(), this)
            systemView?.showEmpty(emptyView)
        } else {
            systemView?.hide()
        }
    }

    /**
     * Clears local data and requests a fresh transaction load.
     */
    private fun refreshTransactionList() {
        adapter?.clear()
        viewModel?.prepare()
    }

    /**
     * Resets tokens when the wallet context changes.
     */
    override fun resetTokens() {
        if (adapter != null) {
            adapter?.clear()
            viewModel?.prepare()
        } else {
            requireActivity().recreate()
        }
    }

    /**
     * Notifies the adapter of newly added tokens.
     */
    override fun addedToken(tokenContracts: MutableList<ContractLocator?>?) {
        adapter?.updateItems(tokenContracts)
    }

    /**
     * Releases Realm resources and adapter listeners on destroy.
     */
    override fun onDestroy() {
        super.onDestroy()
        realmUpdates?.removeAllChangeListeners()
        realmUpdates = null
        realm?.takeIf { !it.isClosed }?.close()
        realm = null
        viewModel?.onDestroy()
        if (adapter != null && listView != null) {
            adapter?.onDestroy(listView)
        }
    }

    /**
     * Refreshes analytics and data when the fragment resumes.
     */
    override fun onResume() {
        super.onResume()
        val vm = viewModel
        if (vm == null) {
            requireActivity().recreate()
        } else {
            vm.track(Analytics.Navigation.ACTIVITY)
            vm.prepare()
        }
        checkTimer = true
    }

    /**
     * Fetches more historical data while throttling repeated requests.
     */
    override fun fetchMoreData(latestDate: Long) {
        if (checkTimer) {
            viewModel?.fetchMoreTransactions(latestDate)
            checkTimer = false
            handler.postDelayed({ checkTimer = true }, 5 * DateUtils.SECOND_IN_MILLIS)
        }
    }

    /**
     * Required click listener implementation (unused).
     */
    override fun onClick(v: View?) = Unit

    /**
     * Starts Realm listeners when the fragment becomes visible.
     */
    override fun comeIntoFocus() {
        isVisibleToUser = true
        startTxListener()
    }

    /**
     * Stops Realm listeners when the fragment is no longer visible.
     */
    override fun leaveFocus() {
        isVisibleToUser = false
        realmUpdates?.removeAllChangeListeners()
        realmUpdates = null
        realm?.takeIf { !it.isClosed }?.close()
        realm = null
    }

    /**
     * Refreshes the adapter after the transaction database is updated.
     */
    override fun resetTransactions() {
        refreshTransactionList()
    }

    /**
     * Scrolls the recycler view back to the top position.
     */
    override fun scrollToTop() {
        listView?.smoothScrollToPosition(0)
    }
}
