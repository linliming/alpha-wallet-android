package com.alphawallet.app.entity.opensea

import android.text.TextUtils
import com.alphawallet.app.C
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * OpenSea NFT 资产实体，封装资产展示所需的字段与辅助方法。
 */
class OpenSeaAsset {

    @SerializedName("id")
    @Expose
    var id: String? = null

    @SerializedName("background_color")
    @Expose
    var backgroundColor: String? = null

    @SerializedName("image")
    @Expose
    var image: String? = null

    @SerializedName("image_url")
    @Expose
    var imageUrl: String? = null

    @SerializedName("image_preview_url")
    @Expose
    var imagePreviewUrl: String? = null

    @SerializedName("image_original_url")
    @Expose
    var imageOriginalUrl: String? = null

    @SerializedName("animationUrl")
    @Expose
    var animationUrl: String? = null

    @SerializedName("animation_url")
    @Expose
    var animationUrlSnake: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("description")
    @Expose
    var description: String? = null

    @SerializedName("external_link")
    @Expose
    var externalLink: String? = null

    @SerializedName("asset_contract")
    @Expose
    var assetContract: AssetContract? = null

    @SerializedName("permalink")
    @Expose
    var permalink: String? = null

    @SerializedName("collection")
    @Expose
    var collection: Collection? = null

    @SerializedName("token_metadata")
    @Expose
    var tokenMetadata: String? = null

    @SerializedName("owner")
    @Expose
    var owner: Owner? = null

    @SerializedName("creator")
    @Expose
    var creator: Creator? = null

    @SerializedName("traits")
    @Expose
    var traits: List<Trait>? = null

    @SerializedName("last_sale")
    @Expose
    var lastSale: LastSale? = null

    @SerializedName("rarity_data")
    @Expose
    var rarity: Rarity? = null

    /**
     * 计算并返回平均成交价（以 ETH 表示）。
     */
    fun getAveragePrice(): String {
        val stats = collection?.stats ?: return ""
        val avgPrice = stats.averagePrice
        if (avgPrice.isNullOrEmpty()) return ""

        return try {
            val price = BigDecimal(avgPrice).setScale(3, RoundingMode.CEILING)
            "$price ${C.ETH_SYMBOL}"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 返回地板价（以 ETH 表示）。
     */
    fun getFloorPrice(): String {
        val stats = collection?.stats ?: return ""
        val floor = stats.floorPrice
        if (floor.isNullOrEmpty()) return ""
        return "$floor ${C.ETH_SYMBOL}"
    }

    /**
     * 返回最近一次成交价，附带货币符号。
     */
    fun getLastSale(): String {
        val sale = lastSale ?: return ""
        val token = sale.paymentToken ?: return ""
        val builder = computeLastSale(sale.totalPrice ?: return "", token.decimals)
        return if (builder.isNotEmpty()) builder.append(" ").append(token.symbol).toString() else ""
    }

    /**
     * 返回动画资源链接，兼容驼峰与下划线字段。
     */
    fun getAnimationUrl(): String? = animationUrl ?: animationUrlSnake

    /**
     * 返回合适的图片链接，按优先级遍历所有字段。
     */
    fun getImageUrl(): String =
        image
            ?: imageUrl
            ?: animationUrl
            ?: imageOriginalUrl
            ?: imagePreviewUrl
            ?: animationUrlSnake
            ?: ""

    /**
     * 判断资产是否具有展示意义（至少包含图片、名称或描述其一）。
     */
    fun isValid(): Boolean =
        !TextUtils.isEmpty(getImageUrl()) ||
            !name.isNullOrEmpty() ||
            !description.isNullOrEmpty()

    /**
     * 将历史成交总价转换为人类可读的数值。
     */
    private fun computeLastSale(totalPrice: String, decimals: Int): StringBuilder {
        val result = StringBuilder()
        if (totalPrice == "0") return result

        return if (totalPrice.length <= decimals) {
            result.append("0.")
            repeat(decimals - totalPrice.length) { result.append("0") }
            for (c in totalPrice) {
                if (c == '0') break
                result.append(c)
            }
            result
        } else {
            val endIndex = totalPrice.length - decimals
            result.append(totalPrice.substring(0, endIndex))
            result.append(".")
            for (c in totalPrice) {
                result.append(c)
                if (c == '0') break
            }
            result
        }
    }

    class Collection {
        @SerializedName("stats")
        @Expose
        var stats: Stats? = null

        @SerializedName("banner_image_url")
        @Expose
        var bannerImageUrl: String? = null

        @SerializedName("slug")
        @Expose
        var slug: String? = null

        class Stats {
            @SerializedName("total_supply")
            @Expose
            var totalSupply: Long = 0

            @SerializedName("count")
            @Expose
            var count: Long = 0

            @SerializedName("num_owners")
            @Expose
            var numOwners: Long = 0

            @SerializedName("average_price")
            @Expose
            var averagePrice: String? = null

            @SerializedName("floor_price")
            @Expose
            var floorPrice: String? = null
        }
    }

    class Owner {
        @SerializedName("user")
        @Expose
        var user: User? = null

        @SerializedName("profile_img_url")
        @Expose
        var profileImgUrl: String? = null

        @SerializedName("address")
        @Expose
        var address: String? = null

        class User {
            @SerializedName("username")
            @Expose
            var username: String? = null
        }
    }

    class Creator {
        @SerializedName("user")
        @Expose
        var user: User? = null

        class User {
            @SerializedName("username")
            @Expose
            var username: String? = null
        }
    }

    class Trait(
        @SerializedName("trait_type")
        @Expose
        var traitType: String? = null,

        @SerializedName("value")
        @Expose
        var value: String? = null,

        @SerializedName("trait_count")
        @Expose
        var traitCount: Long = 0,
    ) {
        /** 特定属性的稀有度百分比 */
        var traitRarity: Float = 0f

        /** 是否为唯一属性 */
        var isUnique: Boolean = false

        constructor(key: String, attrValue: String) : this(key, attrValue, 0)

        /** 标记属性是否唯一。 */
        fun setUnique(unique: Boolean) {
            isUnique = unique
        }

        /** 计算并返回该属性的稀有度百分比。 */
        fun getTraitRarity(totalSupply: Long): Float {
            setUnique(traitCount == 1L)
            traitRarity = traitCount * 100f / totalSupply
            return traitRarity
        }

        /** 使用新的发行量更新稀有度。 */
        fun updateTraitRarity(totalSupply: Long) {
            traitRarity = traitCount * 100f / totalSupply
        }
    }

    class LastSale {
        @SerializedName("total_price")
        @Expose
        var totalPrice: String? = null

        @SerializedName("payment_token")
        @Expose
        var paymentToken: PaymentToken? = null

        class PaymentToken {
            @SerializedName("symbol")
            @Expose
            var symbol: String? = null

            @SerializedName("decimals")
            @Expose
            var decimals: Int = 0
        }
    }

    class Rarity
}
