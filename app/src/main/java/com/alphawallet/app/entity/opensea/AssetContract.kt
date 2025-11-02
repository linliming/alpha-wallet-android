package com.alphawallet.app.entity.opensea

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.util.LocaleUtils
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import io.realm.Realm
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OpenSea 资产合约信息。
 *
 * 封装合约的基础元数据，支持 JSON 序列化和 Parcelable 传递，便于在
 * 本地缓存、UI 展示与跨组件通信时复用。
 */
class AssetContract() : Parcelable {

    /** 合约地址 */
    @SerializedName("address")
    @Expose
    var address: String? = null

    /** 合约名称 */
    @SerializedName("name")
    @Expose
    var name: String? = null

    /** 合约符号 */
    @SerializedName("symbol")
    @Expose
    var symbol: String? = null

    /** 合约所使用的协议名称（ERC721、ERC1155 等） */
    @SerializedName("schema_name")
    @Expose
    var schemaName: String? = null

    /** 合约图标 */
    @SerializedName("image_url")
    @Expose
    var imageUrl: String? = null

    /** 合约创建时间字符串 */
    @SerializedName("created_date")
    @Expose
    var creationDateRaw: String? = null

    /** 合约描述 */
    @SerializedName("description")
    @Expose
    var description: String? = null

    /**
     * 通过 Token 初始化合约信息。
     */
    constructor(token: Token) : this() {
        address = token.tokenInfo.address
        name = token.tokenInfo.name
        symbol = token.tokenInfo.symbol
        schemaName = token.getInterfaceSpec().toString()
    }

    /**
     * 通过序列化数据恢复对象。
     */
    private constructor(parcel: Parcel) : this() {
        address = parcel.readString()
        name = parcel.readString()
        symbol = parcel.readString()
        schemaName = parcel.readString()
        creationDateRaw = parcel.readString()
        description = parcel.readString()
    }

    /**
     * 设置地址并返回自身，便于链式调用。
     */
    fun withAddress(address: String?): AssetContract {
        this.address = address
        return this
    }

    /**
     * 设置名称并返回自身。
     */
    fun withName(name: String?): AssetContract {
        this.name = name
        return this
    }

    /**
     * 设置符号并返回自身。
     */
    fun withSymbol(symbol: String?): AssetContract {
        this.symbol = symbol
        return this
    }

    /**
     * 设置 schema 并返回自身。
     */
    fun withSchemaName(schemaName: String?): AssetContract {
        this.schemaName = schemaName
        return this
    }

    /**
     * 返回格式化后的创建时间（本地日期 + 时间）。
     */
    /**
     * 返回本地化后的创建时间字符串。
     */
    fun getCreationDate(): String {
        val source = creationDateRaw ?: return ""
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT)
            val parsed: Date = formatter.parse(source) ?: return ""
            val locale = LocaleUtils.getDeviceLocale(Realm.getApplicationContext())
            val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            "${dateFormat.format(parsed)} ${timeFormat.format(parsed)}"
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 生成对应的 JSON 字符串，便于网络或本地持久化。
     */
    /**
     * 以 JSON 字符串形式导出当前合约信息。
     */
    fun getJSON(): String {
        val jsonData = JSONObject()
        try {
            jsonData.put("address", address)
            jsonData.put("name", name)
            jsonData.put("symbol", symbol)
            jsonData.put("schema_name", schemaName)
            jsonData.put("created_date", creationDateRaw)
            jsonData.put("description", description)
        } catch (ignored: JSONException) {
        }
        return jsonData.toString()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(address)
        dest.writeString(name)
        dest.writeString(symbol)
        dest.writeString(schemaName)
        dest.writeString(creationDateRaw)
        dest.writeString(description)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<AssetContract> =
            object : Parcelable.Creator<AssetContract> {
                override fun createFromParcel(parcel: Parcel): AssetContract = AssetContract(parcel)

                override fun newArray(size: Int): Array<AssetContract?> = arrayOfNulls(size)
            }
    }
}
