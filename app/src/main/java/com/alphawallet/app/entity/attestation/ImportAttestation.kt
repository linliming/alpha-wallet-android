package com.alphawallet.app.entity.attestation

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EIP681Type
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.KeyProvider
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.DeepLinkService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.util.CoroutineUtils.launchSafely
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.SEPOLIA_TESTNET_ID
import com.alphawallet.token.entity.AttestationValidationStatus
import com.alphawallet.token.tools.TokenDefinition
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.utils.Numeric
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.List
import java.util.Locale
import com.alphawallet.app.repository.EthereumNetworkBase as AppEthereumNetworkBase

/**
 * ImportAttestation - 认证导入服务类
 *
 * 这是AlphaWallet中处理认证导入的核心类，负责：
 * 1. 导入传统认证（Legacy Attestation）
 * 2. 导入EAS认证（Ethereum Attestation Service）
 * 3. 验证认证的有效性
 * 4. 存储认证到本地数据库
 * 5. 处理Smart Pass相关功能
 *
 * @param assetDefinitionService 资产定义服务
 * @param tokensService 代币服务
 * @param callback 导入回调接口
 * @param wallet 钱包实例
 * @param realmManager Realm数据库管理器
 * @param client HTTP客户端
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class ImportAttestation(
    private val assetDefinitionService: AssetDefinitionService,
    private val tokensService: TokensService,
    private val callback: AttestationImportInterface,
    private val wallet: Wallet,
    private val realmManager: RealmManager,
    private val client: OkHttpClient,
) {
    private val keyProvider: KeyProvider = KeyProviderFactory.get()

    /**
     * 导入认证的主要入口方法
     *
     * @param attestation QR扫描结果，包含认证信息
     */
    fun importAttestation(attestation: QRResult) {
        when (attestation.type) {
            EIP681Type.ATTESTATION -> importLegacyAttestation(attestation)
            EIP681Type.EAS_ATTESTATION -> importEASAttestation(attestation)
            else -> {
                // 不支持的认证类型
                Timber.w("不支持的认证类型: ${attestation.type}")
            }
        }
    }

    /**
     * 导入传统认证（Legacy Attestation）
     *
     * @param attestation QR扫描结果
     */
    private fun importLegacyAttestation(attestation: QRResult) {
        launchSafely {
            try {
                // 获取代币信息 - 假设认证基于NFT
                // TODO: 首先验证认证
                val tInfo :TokenInfo? = tokensService.update(attestation.getAddress()?:"", attestation.chainId, ContractType.ERC721)
                if (tInfo == null) {
                    callback.importError("无法获取代币信息")
                    return@launchSafely
                }
                    val storedTInfo = tokensService.storeTokenInfoDirect(wallet, tInfo, ContractType.ERC721)
                    if (storedTInfo == null) {
                        callback.importError("无法存储代币信息")
                        return@launchSafely
                    }

                    val attn: Attestation? = storeAttestation(attestation, storedTInfo)
                    completeImport(attestation, attn)

            } catch (e: Exception) {
                callback.importError(e.message ?: "导入传统认证时发生错误")
            }
        }
    }

    /**
     * 完成导入流程
     *
     * @param attestation QR扫描结果
     * @param tokenAttn 认证代币
     */
    private fun completeImport(
        attestation: QRResult,
        tokenAttn: Attestation?,
    ) {
        if (tokenAttn?.isValid() == AttestationValidationStatus.Pass) {
            val tcmAttestation =
                TokenCardMeta(
                    attestation.chainId,
                    attestation.getAddress()?:"",
                    "1",
                    System.currentTimeMillis(),
                    assetDefinitionService,
                    tokenAttn.tokenInfo.name,
                    tokenAttn.tokenInfo.symbol,
                    tokenAttn.getBaseTokenType(),
                    TokenGroup.ATTESTATION,
                    tokenAttn.getAttestationUID(),
                )
            tcmAttestation.isEnabled = true
            callback.attestationImported(tcmAttestation)
        } else {
            callback.importError(tokenAttn?.isValid()?.getValue())
        }
    }

    /**
     * 存储传统认证
     *
     * @param attestation QR扫描结果
     * @param tInfo 代币信息
     * @return 认证对象
     */
    private suspend fun storeAttestation(
        attestation: QRResult,
        tInfo: TokenInfo,
    ): Attestation? =
        withContext(Dispatchers.IO) {
            val attn: Attestation? = validateAttestation(attestation.getAttestation(), tInfo)
            when (attn?.isValid()) {
                AttestationValidationStatus.Pass -> storeAttestationInternal(tInfo, attn)
                AttestationValidationStatus.Expired,
                AttestationValidationStatus.Issuer_Not_Valid,
                AttestationValidationStatus.Incorrect_Subject,
                -> {
                    callback.importError(attn.isValid()?.getValue())
                    attn
                }
                null -> TODO()
            }
        }

    /**
     * 内部存储认证方法
     *
     * @param tInfo 代币信息
     * @param attn 认证对象
     * @return 认证对象
     */
    private suspend fun storeAttestationInternal(
        tInfo: TokenInfo,
        attn: Attestation?,
    ): Attestation? =
        withContext(Dispatchers.IO) {
            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val key = attn?.getDatabaseKey()
                        var realmAttn =
                            r
                                .where(RealmAttestation::class.java)
                                .equalTo("address", key)
                                .findFirst()

                        if (realmAttn == null) {
                            realmAttn = r.createObject(RealmAttestation::class.java, key)
                        }

                        if (realmAttn != null) {
                            attn?.populateRealmAttestation(realmAttn)
                        }
                        realmAttn?.setAttestation(attn?.getAttestation())
                    }
                }

                // 如果代币不存在，则存储代币信息
                if (tokensService.getToken(tInfo.chainId, tInfo.address) == null) {
                    tokensService.storeTokenInfo(wallet, tInfo, ContractType.ERC721)
                } else {
                    // 如果代币已存在，直接使用现有信息
                }

                setBaseType(attn, tInfo)
            } catch (e: Exception) {
                Timber.e(e, "存储认证时发生错误")
                attn
            }
        }

    /**
     * 设置基础类型
     *
     * @param attn 认证对象
     * @param info 代币信息
     * @return 认证对象
     */
    private fun setBaseType(
        attn: Attestation?,
        info: TokenInfo,
    ): Attestation? {
        val baseToken = tokensService.getToken(info.chainId, info.address)
        if (baseToken != null) {
            attn?.setBaseTokenType(baseToken.getInterfaceSpec())
        }
        return attn
    }

    /**
     * 导入EAS认证（Ethereum Attestation Service）
     *
     * @param qrAttn QR扫描结果
     */
    private fun importEASAttestation(qrAttn: QRResult) {
        launchSafely {
            try {
                // 验证认证
                // 获取链和地址
                val easAttn = Gson().fromJson(qrAttn.functionDetail, EasAttestation::class.java)

                // 验证UID
                val attn = storeAttestation(easAttn, qrAttn.getAddress()?:"")
                val processedAttn = callSmartPassLog(attn)
                checkTokenScript(processedAttn)
            } catch (e: Exception) {
                callback.importError(e.message ?: "导入EAS认证时发生错误")
            }
        }
    }

    /**
     * 存储EAS认证
     *
     * @param attestation EAS认证对象
     * @param originLink 原始链接
     * @return 认证对象
     */
    private suspend fun storeAttestation(
        attestation: EasAttestation,
        originLink: String,
    ): Attestation =
        withContext(Dispatchers.IO) {
            // 使用默认密钥，除非指定
            val attn = loadAttestation(attestation, originLink)
            when (attn.isValid()) {
                AttestationValidationStatus.Pass -> storeAttestationInternal(originLink, attn)
                AttestationValidationStatus.Expired,
                AttestationValidationStatus.Issuer_Not_Valid,
                AttestationValidationStatus.Incorrect_Subject,
                -> {
                    callback.importError(attn.isValid().getValue())
                    attn
                }
            }
        }

    /**
     * 内部存储EAS认证方法
     *
     * @param originLink 原始链接
     * @param attn 认证对象
     * @return 认证对象
     */
    private suspend fun storeAttestationInternal(
        originLink: String,
        attn: Attestation,
    ): Attestation =
        withContext(Dispatchers.IO) {
            val td = assetDefinitionService.getAssetDefinitionDeepScan(attn)
            val identifierHash = attn.getAttestationIdHash(td)
            removeOutdatedAttestation(identifierHash, attn.getDatabaseKey()) // 检测我们正在更新的认证（如果有）并移除

            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val key = attn.getDatabaseKey()
                        var realmAttn =
                            r
                                .where(RealmAttestation::class.java)
                                .equalTo("address", key)
                                .findFirst()

                        if (realmAttn == null) {
                            realmAttn = r.createObject(RealmAttestation::class.java, key)
                        }

                        realmAttn?.setAttestationLink(originLink)
                        if (realmAttn != null) {
                            attn.populateRealmAttestation(realmAttn)
                        }
                        realmAttn?.setIdentifierHash(identifierHash)
                        if (td != null) {
                            realmAttn?.setCollectionId(attn.getAttestationCollectionId(td))
                        }
                        r.insertOrUpdate(realmAttn)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "存储EAS认证时发生错误")
            }

            attn
        }

    /**
     * 移除过期的认证
     *
     * @param identifierHash 标识符哈希
     * @param databaseUID 数据库UID
     */
    private fun removeOutdatedAttestation(
        identifierHash: String,
        databaseUID: String,
    ) {
        try {
            realmManager.getRealmInstance(wallet).use { realm ->
                val realmAttn =
                    realm
                        .where(RealmAttestation::class.java)
                        .equalTo("identifierHash", identifierHash)
                        .findFirst()

                if (realmAttn != null && realmAttn.getAttestationKey() != databaseUID) {
                    // 检查是否相同
                    realm.executeTransaction { r ->
                        realmAttn.setCollectionId(DELETE_KEY)
                        r.insertOrUpdate(realmAttn)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "移除过期认证时发生错误")
        }
    }

    /**
     * 完成导入流程（Token版本）
     *
     * @param token 代币对象
     */
    private fun completeImport(token: Token) {
        if (token is Attestation && token.isValid() == AttestationValidationStatus.Pass) {
            val tcmAttestation =
                TokenCardMeta(
                    token.tokenInfo.chainId,
                    token.getAddress(),
                    "1",
                    System.currentTimeMillis(),
                    assetDefinitionService,
                    token.tokenInfo.name,
                    token.tokenInfo.symbol,
                    token.getBaseTokenType(),
                    TokenGroup.ATTESTATION,
                    token.getAttestationUID(),
                )
            tcmAttestation.isEnabled = true
            callback.attestationImported(tcmAttestation)
        }
    }

    /**
     * 检查TokenScript
     *
     * @param token 代币对象
     */
    private fun checkTokenScript(token: Token) {
        if (token is Attestation) {
            val td = assetDefinitionService.getAssetDefinitionDeepScan(token)
            if (td != null) {
                launchSafely {
                    try {
                        val updatedToken = updateAttestationIdentifier(token, td)
                        completeImport(updatedToken)
                    } catch (e: Exception) {
                        callback.importError(e.message ?: "更新认证标识符时发生错误")
                    }
                }
            } else {
                completeImport(token)
            }
        }
    }

    /**
     * 更新认证标识符哈希值
     *
     * 当从服务器获取到TokenScript时，更新认证的标识符哈希值。
     * 这个方法确保认证的标识符与最新的TokenScript定义保持一致。
     *
     * @param token 要更新的Token对象
     * @param td TokenScript定义，如果为null则不进行更新
     * @return 更新后的Token对象
     */
    private suspend fun updateAttestationIdentifier(
        token: Token,
        td: TokenDefinition?,
    ): Token {
        return withContext(Dispatchers.IO) {
            try {
                // 验证参数有效性
                if (td == null || td.holdingToken.isNullOrEmpty() || token !is Attestation) {
                    return@withContext token
                }

                val attn = token as Attestation

                // 获取新的标识符哈希值
                val identifierHash = attn.getAttestationIdHash(td)

                // 在Realm数据库中更新认证信息
                updateAttestationInRealm(attn, identifierHash, td)

                token
            } catch (e: Exception) {
                Timber.e(e, "更新认证标识符时发生错误: ${e.message}")
                token
            }
        }
    }

    /**
     * 在Realm数据库中更新认证信息
     *
     * @param attn 认证对象
     * @param identifierHash 新的标识符哈希值
     * @param td TokenScript定义
     */
    private suspend fun updateAttestationInRealm(
        attn: Attestation,
        identifierHash: String,
        td: TokenDefinition,
    ) {
        withContext(Dispatchers.IO) {
            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    val key = attn.getDatabaseKey()
                    val realmAttn =
                        realm
                            .where(RealmAttestation::class.java)
                            .equalTo("address", key)
                            .findFirst()

                    if (realmAttn != null && realmAttn.getIdentifierHash() != null &&
                        realmAttn.getIdentifierHash() != identifierHash
                    ) {
                        realm.executeTransaction { r ->
                            Timber.i("认证标识符哈希值已更新: ${realmAttn.getIdentifierHash()} -> $identifierHash")
                            realmAttn.setIdentifierHash(identifierHash)
                            realmAttn.setCollectionId(attn.getAttestationCollectionId(td))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "更新Realm认证数据时发生错误: ${e.message}")
            }
        }
    }

    /**
     * 验证认证数据
     *
     * 这个方法负责验证认证数据的有效性，包括：
     * 1. 获取TokenScript定义
     * 2. 创建认证对象
     * 3. 调用智能合约进行验证
     * 4. 处理验证结果
     *
     * @param attestation 认证数据的十六进制字符串
     * @param tInfo Token信息
     * @return 验证后的认证对象，如果验证失败则返回null
     */
    fun validateAttestation(
        attestation: String,
        tInfo: TokenInfo,
    ): Attestation? {
        // 参数验证
        if (attestation.isNullOrEmpty() || tInfo == null) {
            Timber.w("认证验证失败：参数无效")
            return null
        }

        return try {
            // 获取TokenScript定义
            val td = assetDefinitionService.getDefinition(getTSDataKeyTemp(tInfo.chainId, tInfo.address?:""))

            if (td == null) {
                Timber.w("未找到TokenScript定义，无法验证认证")
                return null
            }

            // 创建认证对象
            val att = createAttestationObject(attestation, tInfo)
            if (att == null) {
                return null
            }

            // 执行智能合约验证
            performSmartContractValidation(att, tInfo, td)

            att
        } catch (e: Exception) {
            Timber.e(e, "认证验证过程中发生错误: ${e.message}")
            null
        }
    }

    /**
     * 创建认证对象
     *
     * @param attestation 认证数据的十六进制字符串
     * @param tInfo Token信息
     * @return 创建的认证对象
     */
    private fun createAttestationObject(
        attestation: String,
        tInfo: TokenInfo,
    ): Attestation? {
        return try {
            val networkInfo = AppEthereumNetworkBase.getNetwork(tInfo.chainId)
            if (networkInfo == null) {
                Timber.w("未找到网络信息，chainId: ${tInfo.chainId}")
                return null
            }

            val attestationBytes =
                try {
                    if (attestation.startsWith("0x")) {
                        org.web3j.utils.Numeric
                            .hexStringToByteArray(attestation)
                    } else {
                        android.util.Base64.decode(attestation, android.util.Base64.DEFAULT)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "解析认证数据失败")
                    return null
                }

            val att = Attestation(tInfo, networkInfo.name, attestationBytes)

            tokensService.getCurrentAddress()?.let { att.setTokenWallet(it) }

            att
        } catch (e: Exception) {
            Timber.e(e, "创建认证对象时发生错误: ${e.message}")
            null
        }
    }

    /**
     * 执行智能合约验证
     *
     * @param att 认证对象
     * @param tInfo Token信息
     * @param td TokenScript定义
     */
    private fun performSmartContractValidation(
        att: Attestation,
        tInfo: TokenInfo,
        td: TokenDefinition,
    ) {
        try {
            val definitionAtt = td.attestation
            if (definitionAtt == null || definitionAtt.function == null) {
                Timber.w("TokenScript中未找到认证定义或验证函数")
                return
            }

            val fd = definitionAtt.function

            // 生成交易函数
            val transaction = assetDefinitionService.generateTransactionFunction(att, java.math.BigInteger.ZERO, td, fd)

            // 设置返回类型
            val updatedTransaction = Function(fd.method, transaction.inputParameters, td.attestationReturnTypes)

            // 调用智能合约
            val result = assetDefinitionService.callSmartContract(tInfo.chainId, tInfo.address?:"", updatedTransaction)

            if (result.isNullOrEmpty()) {
                Timber.w("智能合约调用返回空结果")
                return
            }

            // 解码返回结果
            val values = FunctionReturnDecoder.decode(result, updatedTransaction.outputParameters)
            if (values.isNullOrEmpty()) {
                Timber.w("无法解码智能合约返回结果")
                return
            }

            // 处理验证结果
            att.handleValidation(td.getValidation(values))

            Timber.d("认证验证完成，状态: ${att.isValid()}")
        } catch (e: Exception) {
            Timber.e(e, "执行智能合约验证时发生错误: ${e.message}")
        }
    }

    /**
     * 加载EAS认证
     *
     * @param attestation EAS认证对象
     * @param originLink 原始链接
     * @return 认证对象
     */
    private suspend fun loadAttestation(
        attestation: EasAttestation,
        originLink: String,
    ): Attestation =
        withContext(Dispatchers.IO) {
            val recoverAttestationSigner = recoverSigner(attestation)

            // 1. 通过密钥认证服务验证签名者（使用UID）
            val issuerOnKeyChain = checkAttestationSigner(attestation, recoverAttestationSigner)

            // 2. 解码ABI编码的有效载荷以提取信息。ABI解码模式字节
            // 最初我们需要一个硬编码的模式 - 这应该从模式记录EAS合约中获取
            // 获取认证的模式
            val attestationSchema = fetchSchemaRecord(attestation.chainId, attestation.getSchema())
            // 转换为functionDecode
            val names = ArrayList<String>()
            val values = decodeAttestationData(attestation.data, attestationSchema?.schema ?: "", names)

            val networkInfo = AppEthereumNetworkBase.getNetwork(attestation.chainId)

            var tInfo = Attestation.getDefaultAttestationInfo(attestation.chainId, getEASContract(attestation.chainId))
            var localAttestation = Attestation(tInfo, networkInfo?.name ?: "", originLink.toByteArray(StandardCharsets.UTF_8))
            localAttestation.handleEASAttestation(attestation, names, values, recoverAttestationSigner)

            val collectionHash = localAttestation.getAttestationCollectionId()

            tInfo = Attestation.getDefaultAttestationInfo(attestation.chainId, collectionHash)

            // 现在使用正确的collectionId重新生成
            localAttestation = Attestation(tInfo, networkInfo?.name ?: "", originLink.toByteArray(StandardCharsets.UTF_8))
            localAttestation.handleEASAttestation(attestation, names, values, recoverAttestationSigner)
            localAttestation.setTokenWallet(tokensService.getCurrentAddress()?:"")

            localAttestation
        }

    /**
     * 检查认证签名者
     *
     * @param attestation EAS认证对象
     * @param recoverAttestationSigner 恢复的认证签名者
     * @return 是否有效
     */
    private suspend fun checkAttestationSigner(
        attestation: EasAttestation,
        recoverAttestationSigner: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val keySchemaUID = getKeySchemaUID(attestation.chainId)
            val attestationValid: Boolean

            if (Attestation.getKnownRootIssuers(attestation.chainId).contains(recoverAttestationSigner)) {
                attestationValid = true
            } else if (!keySchemaUID.isNullOrEmpty()) {
                // 调用验证
                val schemaRecord = fetchSchemaRecord(attestation.chainId, keySchemaUID)
                attestationValid = checkAttestationIssuer(schemaRecord, attestation.chainId, recoverAttestationSigner)
            } else {
                attestationValid = false
            }

            attestationValid
        }

    /**
     * 获取数据值
     *
     * @param key 键
     * @param names 名称列表
     * @param values 值列表
     * @return 数据值
     */
    private fun getDataValue(
        key: String,
        names: List<String>,
        values: List<Type<*>>,
    ): String {
        val valueMap = HashMap<String, String>()
        for (index in names.indices) {
            val name = names[index]
            val type = values[index]
            valueMap[name] = type.toString()
        }
        return valueMap[key] ?: ""
    }

    /**
     * 解码认证数据
     *
     * @param attestationData 认证数据
     * @param decodeSchema 解码模式
     * @param names 名称列表
     * @return 解码后的类型列表
     */
    private fun decodeAttestationData(
        attestationData: String,
        decodeSchema: String,
        names: MutableList<String>,
    ): MutableList<Type<*>> {
        val returnTypes = ArrayList<TypeReference<*>>()
        if (decodeSchema.isEmpty()) {
            return ArrayList()
        }

        // 构建解码器
        val typeData = decodeSchema.split(",")
        for (typeElement in typeData) {
            val data = typeElement.split(" ")
            var type = data[0]
            val name = data[1]
            if (type.startsWith("uint") || type.startsWith("int")) {
                type = "uint"
            } else if (type.startsWith("bytes") && type != "bytes") {
                type = "bytes32"
            }

            val tRef: TypeReference<*>? =
                when (type) {
                    "uint" -> object : TypeReference<Uint256>() {}
                    "address" -> object : TypeReference<Address>() {}
                    "bytes32" -> object : TypeReference<Bytes32>() {}
                    "string" -> object : TypeReference<Utf8String>() {}
                    "bytes" -> object : TypeReference<DynamicBytes>() {}
                    "bool" -> object : TypeReference<Bool>() {}
                    else -> null
                }

            if (tRef != null) {
                returnTypes.add(tRef)
            } else {
                Timber.e("未处理的类型!")
                returnTypes.add(object : TypeReference<Uint256>() {})
            }

            names.add(name)
        }

        // 解码模式并填充认证元素
        return FunctionReturnDecoder.decode(
            attestationData,
            org.web3j.abi.Utils
                .convert(returnTypes),
        )
    }

    /**
     * 获取模式记录
     *
     * @param chainId 链ID
     * @param schemaUID 模式UID
     * @return 模式记录
     */
    private suspend fun fetchSchemaRecord(
        chainId: Long,
        schemaUID: String,
    ): SchemaRecord? =
        withContext(Dispatchers.IO) {
            try {
                val schemaRecord = tryCachedValues(schemaUID)
                if (schemaRecord == null) {
                    fetchSchemaRecordOnChain(chainId, schemaUID)
                } else {
                    schemaRecord
                }
            } catch (e: Exception) {
                Timber.e(e, "获取模式记录时发生错误")
                null
            }
        }

    /**
     * 从链上获取模式记录
     *
     * @param chainId 链ID
     * @param schemaUID 模式UID
     * @return 模式记录
     */
    private suspend fun fetchSchemaRecordOnChain(
        chainId: Long,
        schemaUID: String,
    ): SchemaRecord? =
        withContext(Dispatchers.IO) {
            try {
                // 1. 解析UID。目前，只使用默认值：这应该在链的开关上
                val globalResolver = getEASSchemaContract(chainId)

                // 格式化交易以获取密钥解析器
                val getKeyResolver2 =
                    Function(
                        "getSchema",
                        listOf(Bytes32(Numeric.hexStringToByteArray(schemaUID))),
                        listOf<TypeReference<*>>(object : TypeReference<SchemaRecord>() {}),
                    )

                val result = assetDefinitionService.callSmartContract(chainId, globalResolver, getKeyResolver2)
                val values = FunctionReturnDecoder.decode(result, getKeyResolver2.outputParameters)

                values[0] as SchemaRecord
            } catch (e: Exception) {
                Timber.e(e, "从链上获取模式记录时发生错误")
                null
            }
        }

    /**
     * 尝试缓存值
     *
     * @param schemaUID 模式UID
     * @return 模式记录
     */
    private fun tryCachedValues(schemaUID: String): SchemaRecord? {
        // 不关心链冲突 - 如果schemaUID匹配，则模式相同
        return getCachedSchemaRecords()[schemaUID]
    }

    /**
     * 检查认证发行者
     *
     * @param schemaRecord 模式记录
     * @param chainId 链ID
     * @param signer 签名者
     * @return 是否有效
     */
    private suspend fun checkAttestationIssuer(
        schemaRecord: SchemaRecord?,
        chainId: Long,
        signer: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val rootKeyUID = getDefaultRootKeyUID(chainId)
                // 拉取密钥解析器
                val resolverAddr = schemaRecord?.resolver
                // 调用解析器以测试密钥有效性
                val validateKey =
                    Function(
                        "validateSignature",
                        listOf(
                            Bytes32(Numeric.hexStringToByteArray(rootKeyUID)),
                            Address(signer),
                        ),
                        listOf<TypeReference<*>>(object : TypeReference<Bool>() {}),
                    )

                val result = assetDefinitionService.callSmartContract(chainId, resolverAddr?.value ?: "", validateKey)
                val values = FunctionReturnDecoder.decode(result, validateKey.outputParameters)
                (values[0] as Bool).value
            } catch (e: Exception) {
                Timber.e(e, "检查认证发行者时发生错误")
                false
            }
        }

    /**
     * 恢复签名者
     *
     * @param attestation EAS认证对象
     * @return 签名者地址
     */
    companion object {
        const val SMART_PASS_URL = DeepLinkService.AW_APP + "openurl?url="
        const val DELETE_KEY = "DELETE"
        private const val SMART_PASS_API = "https://backend.smartlayer.network/passes/pass-installed-in-aw"
        private const val SMART_PASS_API_DEV = "https://d2a5tt41o5qmyt.cloudfront.net/passes/pass-installed-in-aw"

        @JvmStatic
        fun recoverSigner(attestation: EasAttestation): String =
            try {
                val dataEncoder = StructuredDataEncoder(attestation.getEIP712Attestation())
                val hash = dataEncoder.hashStructuredData()
                val r = Numeric.hexStringToByteArray(attestation.r)
                val s = Numeric.hexStringToByteArray(attestation.s)
                val v = (attestation.getV() and 0xFF).toByte()

                val sig = Sign.SignatureData(v, r, s)
                val key = Sign.signedMessageHashToKey(hash, sig)
                Timber.d("PublicKey: ${Numeric.toHexString(Numeric.toBytesPadded(key, 64))}")
                Numeric.prependHexPrefix(Keys.getAddress(key))
            } catch (e: Exception) {
                Timber.e(e, "恢复签名者时发生错误")
                ""
            }

        /**
         * 获取EAS合约地址
         *
         * @param chainId 链ID
         * @return 合约地址
         */
        @JvmStatic
        fun getEASContract(chainId: Long): String =
            when (chainId) {
                MAINNET_ID -> "0xA1207F3BBa224E2c9c3c6D5aF63D0eb1582Ce587"
                ARBITRUM_MAIN_ID -> "0xbD75f629A22Dc1ceD33dDA0b68c546A1c035c458"
                SEPOLIA_TESTNET_ID -> "0xC2679fBD37d54388Ce493F1DB75320D236e1815e"
                else -> "" // 支持Optimism Goerli (0xC2679fBD37d54388Ce493F1DB75320D236e1815e)
            }
    }

    /**
     * 获取EAS模式合约地址
     *
     * @param chainId 链ID
     * @return 合约地址
     */
    private fun getEASSchemaContract(chainId: Long): String =
        when (chainId) {
            MAINNET_ID -> "0xA7b39296258348C78294F95B872b282326A97BDF"
            ARBITRUM_MAIN_ID -> "0xA310da9c5B885E7fb3fbA9D66E9Ba6Df512b78eB"
            SEPOLIA_TESTNET_ID -> "0x0a7E2Ff54e76B8E6659aedc9103FB21c038050D0"
            else -> "" // 支持Optimism Goerli (0x7b24C7f8AF365B4E308b6acb0A7dfc85d034Cb3f)
        }

    /**
     * 获取密钥模式UID
     *
     * @param chainId 链ID
     * @return 模式UID
     */
    private fun getKeySchemaUID(chainId: Long): String =
        when (chainId) {
            MAINNET_ID -> ""
            ARBITRUM_MAIN_ID -> "0x5f0437f7c1db1f8e575732ca52cc8ad899b3c9fe38b78b67ff4ba7c37a8bf3b4"
            SEPOLIA_TESTNET_ID -> "0x4455598d3ec459c4af59335f7729fea0f50ced46cb1cd67914f5349d44142ec1"
            else -> "" // 支持Optimism Goerli (0x7b24C7f8AF365B4E308b6acb0A7dfc85d034Cb3f)
        }

    /**
     * 获取默认根密钥UID
     *
     * @param chainId 链ID
     * @return 根密钥UID
     */
    private fun getDefaultRootKeyUID(chainId: Long): String =
        when (chainId) {
            MAINNET_ID -> ""
            ARBITRUM_MAIN_ID -> "0xe5c2bfd98a1b35573610b4e5a367bbcb5c736e42508a33fd6046bad63eaf18f9"
            SEPOLIA_TESTNET_ID -> "0xee99de42f544fa9a47caaf8d4a4426c1104b6d7a9df7f661f892730f1b5b1e23"
            else -> "" // 支持Optimism Goerli (0x7b24C7f8AF365B4E308b6acb0A7dfc85d034Cb3f)
        }

    /**
     * 获取缓存的模式记录
     *
     * @return 模式记录映射
     */
    private fun getCachedSchemaRecords(): MutableMap<String, SchemaRecord> {
        val recordMap = mutableMapOf<String, SchemaRecord>()

        val keySchema =
            SchemaRecord(
                Numeric.hexStringToByteArray("0x4455598d3ec459c4af59335f7729fea0f50ced46cb1cd67914f5349d44142ec1"),
                Address("0x0ed88b8af0347ff49d7e09aa56bd5281165225b6"),
                true,
                "string KeyDescription,bytes ASN1Key,bytes PublicKey",
            )
        val keySchema2 =
            SchemaRecord(
                Numeric.hexStringToByteArray("0x5f0437f7c1db1f8e575732ca52cc8ad899b3c9fe38b78b67ff4ba7c37a8bf3b4"),
                Address("0xF0768c269b015C0A246157c683f9377eF571dCD3"),
                true,
                "string KeyDescription,bytes ASN1Key,bytes PublicKey",
            )
        val smartPass =
            SchemaRecord(
                Numeric.hexStringToByteArray("0x7f6fb09beb1886d0b223e9f15242961198dd360021b2c9f75ac879c0f786cafd"),
                Address("0x0000000000000000000000000000000000000000"),
                true,
                "string eventId,string ticketId,uint8 ticketClass,bytes commitment",
            )
        val smartPass2 =
            SchemaRecord(
                Numeric.hexStringToByteArray("0x0630f3342772bf31b669bdbc05af0e9e986cf16458f292dfd3b57564b3dc3247"),
                Address("0x0000000000000000000000000000000000000000"),
                true,
                "string devconId,string ticketIdString,uint8 ticketClass,bytes commitment",
            )
        val smartPassMainNetLegacy =
            SchemaRecord(
                Numeric.hexStringToByteArray("0xba8aaaf91d1f63d998fb7da69449d9a314bef480e9555710c77d6e594e73ca7a"),
                Address("0x0000000000000000000000000000000000000000"),
                true,
                "string eventId,string ticketId,uint8 ticketClass,bytes commitment,string scriptUri",
            )
        val smartPassMainNet =
            SchemaRecord(
                Numeric.hexStringToByteArray("0x44ec5251add2115c92896cf4b531eb2fcfac6d8ec8caa451df52f0a25a028545"),
                Address("0x0000000000000000000000000000000000000000"),
                true,
                "uint16 version,string orgId,string memberId,string memberRole,bytes commitment,string scriptURI",
            )

        recordMap[Numeric.toHexString(keySchema.uid)] = keySchema
        recordMap[Numeric.toHexString(keySchema2.uid)] = keySchema2
        recordMap[Numeric.toHexString(smartPass.uid)] = smartPass
        recordMap[Numeric.toHexString(smartPass2.uid)] = smartPass2
        recordMap[Numeric.toHexString(smartPassMainNetLegacy.uid)] = smartPassMainNetLegacy
        recordMap[Numeric.toHexString(smartPassMainNet.uid)] = smartPassMainNet

        return recordMap
    }

    /**
     * 获取TS数据键临时值
     *
     * @param chainId 链ID
     * @param address 地址
     * @return 数据键
     */
    private fun getTSDataKeyTemp(
        chainId: Long,
        address: String,
    ): String {
        val currentAddress =
            if (address.equals(tokensService.getCurrentAddress(), ignoreCase = true)) {
                "ethereum"
            } else {
                address
            }

        return currentAddress.lowercase(Locale.ROOT) + "-" + chainId
    }

    /**
     * Smart Pass处理 - 调用Smart Pass日志
     *
     * @param attn 认证对象
     * @return 认证对象
     */
    private suspend fun callSmartPassLog(attn: Attestation): Attestation =
        withContext(Dispatchers.IO) {
            // 检查认证是否有效，以及是否为smartpass
            if (attn.isValid() == AttestationValidationStatus.Pass && attn.isSmartPass()) {
                callback.smartPassValidation(callPassConfirmAPI(attn))
            }

            attn
        }

    /**
     * 调用API（如果需要）
     *
     * @param attn 认证对象
     * @return Smart Pass返回结果
     */
    private fun callPassConfirmAPI(attn: Attestation): SmartPassReturn {
        // 需要发送原始认证（未处理）
        // 隔离pass
        val rawPass = attn.getRawAttestation()
        if (rawPass.isNullOrEmpty()) {
            return SmartPassReturn.IMPORT_FAILED // 如果我们到达这个阶段，不应该发生！
        }

        val builder = Request.Builder()

        val url =
            if (AppEthereumNetworkBase.hasRealValue(attn.tokenInfo.chainId)) {
                SMART_PASS_API
            } else {
                SMART_PASS_API_DEV
            }

        val authKey =
            if (AppEthereumNetworkBase.hasRealValue(attn.tokenInfo.chainId)) {
                keyProvider.getSmartPassKey()
            } else {
                keyProvider.getSmartPassDevKey()
            }

        val request =
            builder
                .url(url)
                .header("Authorization", "Bearer $authKey")
                .put(buildPassBody(rawPass))
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code / 100) {
                    2 -> SmartPassReturn.IMPORT_SUCCESS
                    4 -> SmartPassReturn.ALREADY_IMPORTED
                    else -> SmartPassReturn.IMPORT_FAILED
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "调用Pass确认API时发生错误")
            SmartPassReturn.NO_CONNECTION
        }
    }

    /**
     * 构建Pass请求体
     *
     * @param rawPass 原始Pass
     * @return 请求体
     */
    private fun buildPassBody(rawPass: String): RequestBody =
        try {
            val json =
                JSONObject().apply {
                    put("signedToken", rawPass)
                    put("installedPassedInAw", 1)
                }
            json.toString().toRequestBody("application/json".toMediaType())
        } catch (e: JSONException) {
            Timber.w(e, "构建Pass请求体时发生错误")
            "{}".toRequestBody("application/json".toMediaType())
        }
}

/**
 * SmartPassReturn - Smart Pass返回结果枚举
 */
enum class SmartPassReturn {
    IMPORT_SUCCESS,
    IMPORT_FAILED,
    ALREADY_IMPORTED,
    NO_CONNECTION,
}
