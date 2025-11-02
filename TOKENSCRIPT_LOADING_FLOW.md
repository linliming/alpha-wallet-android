# AssetDefinitionService TokenScript 文件加载完整流程

## 📋 概述

`AssetDefinitionService` 负责从多个来源加载、解析、验证和管理 TokenScript 文件。

## 🔄 完整加载流程

### 1. 初始化阶段

```kotlin
class AssetDefinitionService(
    private val ipfsService: IPFSServiceType,
    private val context: Context,
    private val notificationService: NotificationService,
    private val realmManager: RealmManager,
    private val tokensService: TokensService,
    private val tokenLocalSource: TokenLocalSource,
    private val alphaWalletService: AlphaWalletService
) : ParseResult, AttributeInterface
```

### 2. 主加载入口

```kotlin
private fun loadAssetScripts() {
    // 1. 获取信号量防止并发
    assetLoadingLock.acquire()

    // 2. 加载内部资产
    loadInternalAssets()

    // 3. 完成加载
    finishLoading()
}
```

### 3. 内部资产加载流程

#### `loadInternalAssets()` - 加载捆绑脚本

```kotlin
private fun loadInternalAssets() {
    CoroutineUtils.launchSafely(
        scope = serviceScope,
        dispatcher = ioDispatcher,
        onError = { error -> onError(error) }
    ) {
        // 1. 删除所有内部脚本
        deleteAllInternalScriptFromRealm()

        // 2. 获取本地TSML文件列表
        val localFiles = localTSMLFiles

        // 3. 处理每个文件
        localFiles.forEach { asset ->
            addContractAssets(asset)
        }
    }
}
```

#### `getLocalTSMLFiles()` - 获取本地TSML文件

```kotlin
private fun getLocalTSMLFiles(): List<String> {
    val localTSMLFilesStr = mutableListOf<String>()

    try {
        val assetManager: AssetManager = context.resources.assets
        val fileList: Array<String>? = assetManager.list("")

        if (fileList != null) {
            for (file in fileList) {
                if (file.contains("tsml")) {
                    localTSMLFilesStr.add(file)
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "获取本地 TSML 文件失败")
    }

    return localTSMLFilesStr
}
```

#### `addContractAssets()` - 处理单个资产文件

```kotlin
private fun addContractAssets(asset: String): Boolean {
    return try {
        context.resources.assets.open(asset).use { input ->
            // 1. 解析TokenDefinition
            val token: TokenDefinition = parseFile(input)
            val tsf = TokenScriptFile(context, asset)

            // 2. 获取持有代币的合约信息
            val holdingContracts: ContractInfo? = token.contracts.get(token.holdingToken)

            if (holdingContracts != null && holdingContracts.addresses.isNotEmpty()) {
                // 3. 处理每个网络的地址
                for (network in holdingContracts.addresses.keys) {
                    val networkAddresses = holdingContracts.addresses[network]

                    if (!networkAddresses.isNullOrEmpty()) {
                        for (address in networkAddresses) {
                            if (address.isNotBlank()) {
                                updateRealmForBundledScript(network, address, asset, token)
                            }
                        }
                    }
                }

                // 4. 处理签名和证书数据
                processSignatureAndCertificate(tsf)
                return true
            }
        }
        return false
    } catch (e: Exception) {
        Timber.e(e)
        return false
    }
}
```

### 4. 外部文件加载流程

#### `loadNewFiles()` - 加载外部文件

```kotlin
private fun loadNewFiles(handledHashes: MutableList<String>) {
    CoroutineUtils.launchSafely(
        scope = serviceScope,
        dispatcher = ioDispatcher,
        onError = { error -> Timber.e(error, "加载新文件时发生错误") }
    ) {
        // 1. 构建文件列表
        val fileList = buildFileList()

        // 2. 过滤有效文件
        val validFiles = fileList.asSequence()
            .filter { file -> file.isFile }
            .filter { file -> allowableExtension(file) }
            .filter { file -> file.canRead() }
            .toList()

        // 3. 并发处理文件
        validFiles.chunked(3).forEach { fileChunk ->
            val jobs = fileChunk.map { file ->
                async {
                    try {
                        val tsf = TokenScriptFile(context, file.absolutePath)
                        val hash = tsf.calcMD5()

                        if (handledHashes.contains(hash)) return@async

                        val td: TokenDefinition = parseFile(tsf.getInputStreamSafe())

                        cacheSignature(file, td)
                        val originContracts = getOriginContracts(td)

                        withContext(mainDispatcher) {
                            fileLoadComplete(originContracts, tsf, td)
                        }
                    } catch (e: Exception) {
                        handledHashes.add(
                            TokenScriptFile(context, file.absolutePath).calcMD5()
                        )
                        handleFileLoadError(e, file)
                    }
                }
            }
            jobs.awaitAll()
        }
    }
}
```

#### `buildFileList()` - 构建文件列表

```kotlin
private fun buildFileList(): List<File> {
    val fileList: MutableList<File> = ArrayList()

    try {
        // 1. AlphaWallet目录文件 (最高优先级)
        if (checkReadPermission()) {
            val alphaWalletDir = File(
                "${Environment.getExternalStorageDirectory()}${File.separator}${HomeViewModel.ALPHAWALLET_DIR}"
            )

            if (alphaWalletDir.exists()) {
                alphaWalletDir.listFiles()?.let { files ->
                    fileList.addAll(files.filterNotNull())
                }
            }
        }

        // 2. 应用外部目录文件
        context.getExternalFilesDir("")?.listFiles()?.let { files ->
            fileList.addAll(files.filterNotNull())
        }

        // 3. 服务器下载文件 (最低优先级)
        context.filesDir.listFiles()?.let { files ->
            fileList.addAll(files.filterNotNull())
        }
    } catch (e: Exception) {
        Timber.e(e, "构建文件列表失败")
    }

    return fileList
}
```

### 5. 文件解析和处理

#### `parseFile()` - 解析TokenScript文件

```kotlin
@Throws(Exception::class)
private fun parseFile(xmlInputStream: InputStream): TokenDefinition {
    val locale = context.resources.configuration.locales[0]
    return TokenDefinition(xmlInputStream, locale, this)
}
```

#### `getOriginContracts()` - 获取原始合约信息

```kotlin
private fun getOriginContracts(tokenDef: TokenDefinition): List<ContractLocator> {
    val holdingContracts: ContractInfo? = tokenDef.contracts[tokenDef.holdingToken]

    return if (holdingContracts != null) {
        addToEventList(tokenDef)
        ContractLocator.fromContractInfo(holdingContracts)
    } else {
        ArrayList()
    }
}
```

### 6. 文件完成处理

#### `fileLoadComplete()` - 完成文件加载

```kotlin
private fun fileLoadComplete(
    originContracts: List<ContractLocator>,
    file: TokenScriptFile,
    td: TokenDefinition
): TokenDefinition {

    // 1. 验证输入参数
    if (originContracts.isEmpty()) return td
    if (td.attestation != null) return td

    // 2. 获取主要链ID和事件状态
    val primaryChainId = getPrimaryChainId(originContracts)
    val hasEvents = td.hasEvents()

    try {
        // 3. 处理Realm数据库更新
        processRealmDatabaseUpdate(originContracts, file, td, hasEvents, primaryChainId)
    } catch (e: Exception) {
        Timber.e(e, "处理文件加载完成时发生错误: ${file.absolutePath}")
    }

    return td
}
```

#### `processRealmDatabaseUpdate()` - 处理数据库更新

```kotlin
private fun processRealmDatabaseUpdate(
    originContracts: List<ContractLocator>,
    file: TokenScriptFile,
    td: TokenDefinition,
    hasEvents: Boolean,
    primaryChainId: Long
) {
    realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->

        // 1. 检查安全区域的过期脚本
        if (isInSecureZone(file) && td.isSchemaLessThanMinimum) {
            removeFile(file.absolutePath)
            loadScriptFromServer(primaryChainId, getFileName(file) ?: return@use)
            return@use
        }

        // 2. 计算文件哈希
        val fileHash = file.calcMD5()

        // 3. 执行数据库事务
        realm.executeTransaction { transactionRealm ->
            originContracts.forEach { contractLocator ->
                processContractData(
                    transactionRealm,
                    contractLocator,
                    file,
                    td,
                    hasEvents,
                    fileHash
                )
            }
        }
    }
}
```

### 7. 数据库操作

#### `processContractData()` - 处理合约数据

```kotlin
private fun processContractData(
    realm: Realm,
    contractLocator: ContractLocator,
    file: TokenScriptFile,
    td: TokenDefinition,
    hasEvents: Boolean,
    fileHash: String
) {
    try {
        // 1. 获取或创建数据库条目
        val entryKey = getTSDataKey(contractLocator.chainId, contractLocator.address)
        var entry = realm.where(RealmTokenScriptData::class.java)
            .equalTo("instanceKey", entryKey)
            .findFirst()

        if (entry == null) {
            entry = realm.createObject(RealmTokenScriptData::class.java, entryKey)
        }

        // 2. 检查是否可以更新文件路径
        if (canUpdateFilePath(entry, file)) {
            updateTokenScriptData(entry, file, td, hasEvents, fileHash)
        }

    } catch (e: Exception) {
        Timber.e(e, "处理合约数据时发生错误: ${contractLocator.address}")
    }
}
```

#### `updateTokenScriptData()` - 更新TokenScript数据

```kotlin
private fun updateTokenScriptData(
    entry: RealmTokenScriptData,
    file: TokenScriptFile,
    td: TokenDefinition,
    hasEvents: Boolean,
    fileHash: String
) {
    try {
        entry.fileHash = fileHash
        entry.filePath = file.absolutePath
        entry.setNames(td.tokenNameList)
        entry.setViewList(td.views)
        entry.setHasEvents(hasEvents)
        entry.schemaUID = td.attestationSchemaUID
    } catch (e: Exception) {
        Timber.e(e)
    }
}
```

### 8. 签名和证书处理

#### `processSignatureAndCertificate()` - 处理签名和证书

```kotlin
private fun processSignatureAndCertificate(tsf: TokenScriptFile) {
    try {
        val hash = tsf.calcMD5()
        val awSignature = XMLDsigDescriptor().apply {
            result = "pass"
            issuer = "AlphaWallet"
            keyName = "AlphaWallet"
            type = SigReturnType.SIGNATURE_PASS
        }

        tsf.determineSignatureType(awSignature)
        storeCertificateData(hash, awSignature)

    } catch (e: Exception) {
        Timber.e(e)
    }
}
```

## 📊 方法调用关系图

```
AssetDefinitionService 初始化
├── 构造函数注入依赖
├── 创建协程作用域 (serviceScope)
└── 初始化数据成员

loadAssetScripts() [主入口]
├── assetLoadingLock.acquire() [获取信号量]
├── loadInternalAssets() [加载内部资产]
│   ├── deleteAllInternalScriptFromRealm() [删除旧脚本]
│   ├── getLocalTSMLFiles() [获取本地文件列表]
│   └── addContractAssets() [处理每个文件]
│       ├── parseFile(input) [解析TokenScript]
│       ├── updateRealmForBundledScript() [更新数据库]
│       └── processSignatureAndCertificate() [处理签名]
│
├── loadNewFiles() [加载外部文件] (已注释)
│   ├── buildFileList() [构建文件列表]
│   ├── parseFile() [解析文件]
│   ├── cacheSignature() [缓存签名]
│   ├── getOriginContracts() [获取原始合约]
│   └── fileLoadComplete() [完成文件加载]
│       └── processRealmDatabaseUpdate() [处理数据库更新]
│           └── processContractData() [处理合约数据]
│               └── updateTokenScriptData() [更新数据]
│
└── finishLoading() [完成加载]
```

## 🔧 关键方法说明

### 核心加载方法

- **`loadAssetScripts()`**: 主加载入口
- **`loadInternalAssets()`**: 加载捆绑脚本
- **`loadNewFiles()`**: 加载外部文件
- **`addContractAssets()`**: 处理单个资产文件

### 文件处理方法

- **`buildFileList()`**: 构建文件列表
- **`parseFile()`**: 解析TokenScript文件
- **`getLocalTSMLFiles()`**: 获取本地TSML文件
- **`fileLoadComplete()`**: 完成文件加载

### 数据处理方法

- **`getOriginContracts()`**: 获取原始合约信息
- **`processRealmDatabaseUpdate()`**: 处理数据库更新
- **`processContractData()`**: 处理合约数据
- **`updateTokenScriptData()`**: 更新TokenScript数据

### 签名和证书方法

- **`processSignatureAndCertificate()`**: 处理签名和证书
- **`storeCertificateData()`**: 存储证书数据

## ⚡ 性能优化特点

1. **协程优化**: 使用协程替代RxJava
2. **并发控制**: 使用信号量防止竞态条件
3. **批量处理**: 文件分批处理
4. **缓存机制**: 避免重复解析
5. **错误处理**: 完善的异常处理
6. **资源管理**: 自动资源释放

## 📁 文件来源优先级

1. **AlphaWallet目录** (最高优先级)
2. **应用外部目录**
3. **应用内部目录**
4. **Assets目录** (最低优先级)
