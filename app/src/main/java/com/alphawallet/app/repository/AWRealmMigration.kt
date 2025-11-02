package com.alphawallet.app.repository

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration
import io.realm.RealmObjectSchema
import io.realm.RealmSchema

class AWRealmMigration : RealmMigration {

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val schema: RealmSchema = realm.schema
        var version = oldVersion

        if (version == 4L) {
            val realmTicker = schema.get("RealmTokenTicker")!!
            if (!realmTicker.hasField("currencySymbol")) {
                realmTicker.addField("currencySymbol", String::class.java)
            }
            version++
        }
        if (version == 5L) {
            val realmToken = schema.get("RealmToken")!!
            if (!realmToken.hasField("lastTxTime")) {
                realmToken.addField("lastTxTime", Long::class.javaPrimitiveType!!)
            }
            version++
        }
        if (version == 6L) {
            schema.create("RealmCertificateData")
                .addField("instanceKey", String::class.java, FieldAttribute.PRIMARY_KEY)
                .addField("result", String::class.java)
                .addField("subject", String::class.java)
                .addField("keyName", String::class.java)
                .addField("keyType", String::class.java)
                .addField("issuer", String::class.java)
                .addField("certificateName", String::class.java)
                .addField("type", Int::class.javaPrimitiveType!!)
            version++
        }
        if (version == 7L) {
            var realmData = schema.get("RealmAuxData")
            if (realmData == null) {
                schema.create("RealmAuxData")
                    .addField("instanceKey", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("chainId", Int::class.javaPrimitiveType!!)
                    .addField("tokenAddress", String::class.java)
                    .addField("tokenId", String::class.java)
                    .addField("functionId", String::class.java)
                    .addField("result", String::class.java)
                    .addField("resultTime", Long::class.javaPrimitiveType!!)
                    .addField("resultReceivedTime", Long::class.javaPrimitiveType!!)
            } else {
                if (!realmData.hasField("tokenAddress")) {
                    realmData.addField("tokenAddress", String::class.java)
                }
                if (!realmData.hasField("resultReceivedTime")) {
                    realmData.addField("resultReceivedTime", Long::class.javaPrimitiveType!!)
                }
            }

            realmData = schema.get("RealmKeyType")
            if (realmData == null) {
                schema.create("RealmKeyType")
                    .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("type", Byte::class.javaPrimitiveType!!)
                    .addField("authLevel", String::class.java)
                    .addField("lastBackup", Long::class.javaPrimitiveType!!)
                    .addField("dateAdded", Long::class.javaPrimitiveType!!)
                    .addField("modulus", String::class.java)
            }

            realmData = schema.get("RealmWalletData")
            if (realmData == null) {
                schema.create("RealmWalletData")
                    .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("ENSName", String::class.java)
                    .addField("balance", String::class.java)
                    .addField("name", String::class.java)
                    .addField("lastWarning", Long::class.javaPrimitiveType!!)
            }

            version += 2
        } else if (version == 8L) {
            val realmData = schema.get("RealmAuxData")!!
            if (!realmData.hasField("resultReceivedTime")) {
                realmData.addField("resultReceivedTime", Long::class.javaPrimitiveType!!)
                    .transform { obj -> obj.set("resultReceivedTime", 0L) }
            }
            version++
        }

        if (version == 9L) {
            val realmToken = schema.get("RealmToken")!!
            if (!realmToken.hasField("earliestTxBlock")) {
                realmToken.addField("earliestTxBlock", Long::class.javaPrimitiveType!!)
            }
            version++
        }
        if (version == 10L) {
            schema.create("RealmTokenScriptData")
                .addField("instanceKey", String::class.java, FieldAttribute.PRIMARY_KEY)
                .addField("fileHash", String::class.java)
                .addField("filePath", String::class.java)
                .addField("names", String::class.java)
                .addField("viewList", String::class.java)
            version++
        }
        if (version == 11L) {
            val realmToken = schema.get("RealmTokenScriptData")!!
            if (!realmToken.hasField("hasEvents")) {
                realmToken.addField("hasEvents", Boolean::class.javaPrimitiveType!!)
            }
            version++
        }
        if (version == 12L) {
            schema.create("RealmWCSession")
                .addField("sessionId", String::class.java, FieldAttribute.PRIMARY_KEY)
                .addField("peerId", String::class.java)
                .addField("sessionData", String::class.java)
                .addField("remotePeerData", String::class.java)
                .addField("remotePeerId", String::class.java)
                .addField("usageCount", Int::class.javaPrimitiveType!!)
                .addField("lastUsageTime", Long::class.javaPrimitiveType!!)
                .addField("walletAccount", String::class.java)
            version++
        }
        if (version == 13L) {
            schema.create("RealmWCSignElement")
                .addField("sessionId", String::class.java)
                .addField("signMessage", ByteArray::class.java)
                .addField("signType", String::class.java)
                .addField("signTime", Long::class.javaPrimitiveType!!)
            version++
        }
        if (version == 14L) {
            val realmToken = schema.get("RealmToken")!!
            if (!realmToken.hasField("visibilityChanged")) {
                realmToken.addField("visibilityChanged", Boolean::class.javaPrimitiveType!!)
            }
            version++
        }
        if (version == 15L) {
            schema.create("RealmGasSpread")
                .addField("timeStamp", Long::class.javaPrimitiveType!!, FieldAttribute.PRIMARY_KEY)
                .addField("chainId", Int::class.javaPrimitiveType!!)
                .addField("rapid", String::class.java)
                .addField("fast", String::class.java)
                .addField("standard", String::class.java)
                .addField("slow", String::class.java)
            version++
        }
        if (version == 16L) {
            val realmToken = schema.get("RealmTransaction")!!
            if (!realmToken.hasField("expectedCompletion")) {
                realmToken.addField("expectedCompletion", Long::class.javaPrimitiveType!!)
            }
            if (realmToken.hasField("token")) {
                realmToken.removeField("token")
            }
            version++
        }
        if (version == 17L) {
            var realmData = schema.get("RealmTransfer")
            if (realmData == null) {
                schema.create("RealmTransfer")
                    .addField("hash", String::class.java)
                    .addField("tokenAddress", String::class.java)
                    .addField("eventName", String::class.java)
                    .addField("transferDetail", String::class.java)
            }
            version++
        }
        if (version == 18L) {
            val realmData = schema.get("RealmTransaction")
            if (realmData != null && realmData.hasField("operations")) {
                realmData.removeField("operations")
            }
            version++
        }
        if (version == 19L) {
            var realmData = schema.get("RealmTransactionOperation")
            if (realmData != null) {
                realmData.removeField("viewType")
                realmData.removeField("from")
                realmData.removeField("to")
                realmData.removeField("value")
                realmData.removeField("contract")
            }

            realmData = schema.get("RealmTransactionContract")
            if (realmData != null) {
                realmData.removeField("name")
                realmData.removeField("totalSupply")
                realmData.removeField("decimals")
                realmData.removeField("symbol")
                realmData.removeField("balance")
                realmData.removeField("operation")
                realmData.removeField("otherParty")
                realmData.removeField("indices")
                realmData.removeField("type")
                realmData.removeField("contractType")
            }

            version++
        }
        if (version == 20L) {
            schema.remove("RealmTransactionOperation")
            schema.remove("RealmTransactionContract")
            version++
        }
        if (version == 21L) {
            val realmData = schema.get("RealmWCSession")
            if (realmData != null && !realmData.hasField("chainId")) {
                realmData.addField("chainId", Int::class.javaPrimitiveType!!)
            }
            version++
        }
        if (version == 22L) {
            val realmData = schema.get("RealmERC721Asset")
            if (realmData != null && !realmData.hasField("imageOriginalUrl")) {
                realmData.addField("imageOriginalUrl", String::class.java)
            }
            version++
        }
        if (version == 23L) {
            val realmData = schema.get("RealmERC721Asset")
            if (realmData != null && !realmData.hasField("imageThumbnailUrl")) {
                realmData.addField("imageThumbnailUrl", String::class.java)
            }
            version++
        }
        if (version == 24L) {
            var realmData = schema.get("RealmNFTAsset")
            if (realmData == null) {
                schema.create("RealmNFTAsset")
                    .addField("tokenIdAddr", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("metaData", String::class.java)
            }
            version++
        }
        if (version == 25L) {
            schema.remove("RealmERC721Asset")
            version++
        }
        if (version == 26L) {
            val realmData = schema.get("RealmNFTAsset")
            if (realmData != null && !realmData.hasField("balance")) {
                realmData.addField("balance", String::class.java)
            }
            version++
        }
        if (version == 27L) {
            val realmData = schema.get("RealmNFTAsset")
            if (realmData != null && !realmData.hasField("balance")) {
                realmData.addField("balance", String::class.java)
            }
            val realmToken = schema.get("RealmToken")
            if (realmToken != null) {
                if (realmToken.hasField("erc1155BlockRead")) {
                    realmToken.removeField("erc1155BlockRead")
                }
                if (!realmToken.hasField("erc1155BlockRead")) {
                    realmToken.addField("erc1155BlockRead", String::class.java)
                }
            }
            version++
        }
        if (version == 28L) {
            schema.create("RealmAToken")
                .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
            version++
        }
        if (version == 29L || version == 30L) {
            val realmData = schema.get("RealmTransaction")
            if (realmData != null && !realmData.hasField("contractAddress")) {
                realmData.addField("contractAddress", String::class.java)
            }
            version = 31L
        }
        if (version == 31L) {
            schema.remove("RealmGasSpread")
            schema.create("RealmGasSpread")
                .addField("chainId", Int::class.javaPrimitiveType!!, FieldAttribute.PRIMARY_KEY)
                .addField("timeStamp", Long::class.javaPrimitiveType!!)
                .addField("rapid", String::class.java)
                .addField("fast", String::class.java)
                .addField("standard", String::class.java)
                .addField("slow", String::class.java)
                .addField("baseFee", String::class.java)
            version++
        }
        if (version == 32L || version == 33L) {
            val realmData = schema.get("RealmWalletData")
            if (realmData != null && !realmData.hasField("ENSAvatar")) {
                realmData.addField("ENSAvatar", String::class.java)
            }
            version = 34L
        }
        if (version == 34L) {
            var realmData = schema.get("RealmToken")!!
            realmData.addField("temp_chainId", Long::class.javaPrimitiveType!!)
                .transform { obj -> obj.setLong("temp_chainId", obj.getInt("chainId").toLong()) }
                .removeField("chainId")
                .renameField("temp_chainId", "chainId")

            realmData = schema.get("RealmAuxData")!!
            realmData.addField("temp_chainId", Long::class.javaPrimitiveType!!)
                .transform { obj -> obj.setLong("temp_chainId", obj.getInt("chainId").toLong()) }
                .removeField("chainId")
                .renameField("temp_chainId", "chainId")

            realmData = schema.get("RealmGasSpread")!!
            realmData.addField("temp_chainId", Long::class.javaPrimitiveType!!)
                .transform { obj -> obj.setLong("temp_chainId", obj.getInt("chainId").toLong()) }
                .removeField("chainId")
                .renameField("temp_chainId", "chainId")
                .addPrimaryKey("chainId")

            realmData = schema.get("RealmTransaction")!!
            realmData.addField("temp_chainId", Long::class.javaPrimitiveType!!)
                .transform { obj -> obj.setLong("temp_chainId", obj.getInt("chainId").toLong()) }
                .removeField("chainId")
                .renameField("temp_chainId", "chainId")

            realmData = schema.get("RealmWCSession")!!
            realmData.addField("temp_chainId", Long::class.javaPrimitiveType!!)
                .transform { obj -> obj.setLong("temp_chainId", obj.getInt("chainId").toLong()) }
                .removeField("chainId")
                .renameField("temp_chainId", "chainId")

            version++
        }
        if (version == 35L || version == 36L) {
            val realmData = schema.get("RealmAToken")
            if (realmData == null) {
                schema.create("RealmAToken")
                    .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
            }
            version = 37L
        }
        if (version in 37L..42L) {
            var realmData: RealmObjectSchema? = schema.get("RealmTokenMapping")
            if (realmData != null) {
                schema.remove("RealmTokenMapping")
            }
            schema.create("RealmTokenMapping")
                .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
                .addField("base", String::class.java)
                .addField("group", Int::class.javaPrimitiveType!!)

            realmData = schema.get("RealmAToken")
            if (realmData != null) {
                schema.remove("RealmAToken")
            }

            version = 43L
        }
        if (version == 43L || version == 44L) {
            val realmData = schema.get("Realm1559Gas")
            if (realmData != null) {
                schema.remove("Realm1559Gas")
            }
            schema.create("Realm1559Gas")
                .addField("chainId", Long::class.javaPrimitiveType!!, FieldAttribute.PRIMARY_KEY)
                .addField("timeStamp", Long::class.javaPrimitiveType!!)
                .addField("resultData", String::class.java)

            version = 45L
        }
        if (version == 45L) {
            val realmData = schema.get("RealmTransaction")
            if (realmData != null) {
                if (!realmData.hasField("maxFeePerGas")) {
                    realmData.addField("maxFeePerGas", String::class.java)
                }
                if (!realmData.hasField("maxPriorityFee")) {
                    realmData.addField("maxPriorityFee", String::class.java)
                }
            }
            version++
        }
        if (version == 46L) {
            val realmData = schema.get("RealmTokenScriptData")
            if (realmData != null && !realmData.hasField("ipfsPath")) {
                realmData.addField("ipfsPath", String::class.java)
            }
            version++
        }
        if (version in 47L..50L) {
            val realmData = schema.get("RealmAttestation")
            if (realmData == null) {
                schema.create("RealmAttestation")
                    .addField("address", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("name", String::class.java)
                    .addField("chains", String::class.java)
                    .addField("subTitle", String::class.java)
                    .addField("id", String::class.java)
                    .addField("hash", String::class.java)
                    .addField("attestation", String::class.java)
            } else if (!realmData.hasField("attestation")) {
                realmData.addField("attestation", String::class.java)
            }

            version = 51L
        }
        if (version == 51L) {
            var realmData = schema.get("RealmTokenScriptData")
            if (realmData != null && !realmData.hasField("schemaUID")) {
                realmData.addField("schemaUID", String::class.java)
            }

            realmData = schema.get("RealmAttestation")
            if (realmData != null && !realmData.hasField("identifierHash")) {
                realmData.addField("identifierHash", String::class.java)
            }

            if (realmData != null && realmData.hasField("hash")) {
                realmData.renameField("hash", "schemaUID")
            }

            version = 52L
        }
        if (version == 52L) {
            val realmData = schema.get("RealmAttestation")
            if (realmData != null && realmData.hasField("schemaUID")) {
                realmData.renameField("schemaUID", "collectionId")
            }
            version = 53L
        }
        if (version == 53L) {
            val realmData = schema.get("Realm1559Gas")
            if (realmData != null) {
                schema.remove("Realm1559Gas")
            }
            schema.create("Realm1559Gas")
                .addField("chainId", Long::class.javaPrimitiveType!!, FieldAttribute.PRIMARY_KEY)
                .addField("timeStamp", Long::class.javaPrimitiveType!!)
                .addField("resultData", String::class.java)

            version = 54L
        }
    }

    override fun hashCode(): Int = AWRealmMigration::class.java.hashCode()

    override fun equals(other: Any?): Boolean = other is AWRealmMigration
}
