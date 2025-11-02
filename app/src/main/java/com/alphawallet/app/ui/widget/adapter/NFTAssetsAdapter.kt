package com.alphawallet.app.ui.widget.adapter

import android.app.Activity
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.service.OpenSeaService
import com.alphawallet.app.ui.NFTActivity
import com.alphawallet.app.ui.widget.OnAssetClickListener
import com.alphawallet.app.widget.NFTImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class NFTAssetsAdapter(
    private val activity: Activity,
    private val token: Token,
    private val listener: OnAssetClickListener,
    private val openSeaService: OpenSeaService,
    private val isGrid: Boolean,
) : RecyclerView.Adapter<NFTAssetsAdapter.ViewHolder>() {

    private val actualData = mutableListOf<Pair<BigInteger, NFTAsset>>()
    private val displayData = mutableListOf<Pair<BigInteger, NFTAsset>>()
    private var lastFilter: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        when (token.getInterfaceSpec()) {
            ContractType.ERC721,
            ContractType.ERC721_LEGACY,
            ContractType.ERC721_TICKET,
            ContractType.ERC721_UNDETERMINED,
            ContractType.ERC721_ENUMERABLE,
            -> {
                for (tokenId in token.getUniqueTokenIds()) {
                    val asset = token.getAssetForToken(tokenId)
                    if (asset != null) {
                        actualData.add(Pair(tokenId, asset))
                    }
                }
            }

            ContractType.ERC1155 -> {
                val data = token.getCollectionMap()
                for ((key, value) in data) {
                    actualData.add(Pair(key, value))
                }
            }

            else -> {
                // no-op
            }
        }

        displayData.addAll(actualData)
        sortData()
    }

    fun attachAttestations(attestations: Array<Token>) {
        for (attestationToken in attestations) {
            val attestation = attestationToken as? Attestation ?: continue
            NFTAsset(attestation)
        }
        sortData()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes =
            if (isGrid) {
                R.layout.item_erc1155_asset_select_grid
            } else {
                R.layout.item_erc1155_asset_select
            }
        val itemView = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pair = displayData[position]
        val item = pair.second
        displayAsset(holder, item, pair.first)
        if (item.requiresReplacement()) {
            fetchAsset(holder, pair)
        }
    }

    override fun getItemCount(): Int = displayData.size

    private fun displayAsset(holder: ViewHolder, asset: NFTAsset, tokenId: BigInteger) {
        displayTitle(holder, asset, tokenId)
        displayImage(holder, asset)

        holder.layout.setOnClickListener {
            listener.onAssetClicked(Pair(tokenId, asset))
        }

        holder.layout.setOnLongClickListener {
            listener.onAssetLongClicked(Pair(tokenId, asset))
            false
        }

        if (isGrid) {
            holder.icon.setOnClickListener {
                listener.onAssetClicked(Pair(tokenId, asset))
            }
        }
    }

    private fun displayImage(holder: ViewHolder, asset: NFTAsset) {
        if (asset.hasImageAsset()) {
            holder.icon.setupTokenImageThumbnail(asset, isGrid)
        } else {
            holder.icon.showFallbackLayout(token)
        }
    }

    private fun displayTitle(holder: ViewHolder, asset: NFTAsset, tokenId: BigInteger) {
        val assetCount =
            if (asset.isCollection()) {
                asset.getCollectionCount()
            } else {
                asset.balance.toInt()
            }
        val textId =
            if (assetCount == 1) {
                R.string.asset_description_text
            } else {
                R.string.asset_description_text_plural
            }
        val title = asset.getName() ?: "ID #$tokenId"
        holder.title.text = title

        if (token.isERC721()) {
            holder.subtitle.visibility = View.GONE
        } else {
            holder.subtitle.text =
                activity.getString(textId, assetCount, asset.getAssetCategory(tokenId).value)
            holder.subtitle.visibility = View.VISIBLE
        }
    }

    private fun fetchAsset(holder: ViewHolder, pair: Pair<BigInteger, NFTAsset>) {
        pair.second.metaDataLoader?.cancel()
        pair.second.metaDataLoader =
            scope.launch {
                try {
                    val asset =
                        if (EthereumNetworkBase.hasOpenseaAPI(token.tokenInfo.chainId)) {
                            fetchFromOpenSea(pair) ?: fetchFromContract(pair)
                        } else {
                            fetchFromContract(pair)
                        }
                    if (asset != null) {
                        displayAsset(holder, asset, pair.first)
                    }
                } catch (c: CancellationException) {
                    // ignore cancelled job
                } catch (_: Throwable) {
                    // mimic original behaviour by swallowing errors
                }
            }
    }

    private suspend fun fetchFromOpenSea(pair: Pair<BigInteger, NFTAsset>): NFTAsset? {
        val oldAsset = pair.second
        val json =
            runCatching { openSeaService.getAsset(token, pair.first) }.getOrNull()?.takeIf {
                it.isNotBlank()
            } ?: return null
        val fetchedAsset = NFTAsset(json)
        val stored = storeAsset(pair.first, fetchedAsset, oldAsset)
        return if (stored.hasImageAsset()) stored else null
    }

    private suspend fun fetchFromContract(pair: Pair<BigInteger, NFTAsset>): NFTAsset? {
        val tokenId = pair.first
        val oldAsset = pair.second
        val newAsset =
            withContext(Dispatchers.IO) {
                token.fetchTokenMetadata(tokenId)
            } ?: return null
        return storeAsset(tokenId, newAsset, oldAsset)
    }

    private fun storeAsset(
        tokenId: BigInteger,
        fetchedAsset: NFTAsset,
        oldAsset: NFTAsset?,
    ): NFTAsset {
        if (!fetchedAsset.hasImageAsset()) {
            return oldAsset ?: fetchedAsset
        }
        if (oldAsset != null) {
            fetchedAsset.updateFromRaw(oldAsset)
        }
        (activity as? NFTActivity)?.storeAsset(tokenId, fetchedAsset)
        token.addAssetToTokenBalanceAssets(tokenId, fetchedAsset)
        return fetchedAsset
    }

    fun updateList(list: List<Pair<BigInteger, NFTAsset>>) {
        displayData.clear()
        displayData.addAll(list)
        sortData()
        notifyDataSetChanged()
    }

    fun filter(searchFilter: String) {
        if (lastFilter.equals(searchFilter, ignoreCase = true)) {
            return
        }

        val filteredList = ArrayList<Pair<BigInteger, NFTAsset>>()
        for (data in actualData) {
            val asset = data.second
            val name = asset.getName()
            if (name != null) {
                if (name.lowercase().contains(searchFilter.lowercase())) {
                    filteredList.add(data)
                }
            } else {
                val id = data.first.toString()
                if (id.contains(searchFilter)) {
                    filteredList.add(data)
                }
            }
        }
        updateList(filteredList)
        lastFilter = searchFilter
    }

    override fun getItemViewType(position: Int): Int = position

    fun onDestroy() {
        for (assetPair in displayData) {
            assetPair.second.metaDataLoader?.cancel()
        }
        scope.cancel()
    }

    private fun sortData() {
        displayData.sortBy { it.first }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: ViewGroup = view.findViewById(R.id.holding_view)
        val icon: NFTImageView = view.findViewById(R.id.icon)
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val arrowRight: ImageView? = view.findViewById(R.id.arrow_right)

        init {
            arrowRight?.visibility = View.VISIBLE
        }
    }
}
