# AssetDefinitionService TokenScript 文件加载完整流程

## 📋 概述

`AssetDefinitionService` 是处理 TokenScript 文件的核心服务类，负责从多个来源加载、解析、验证和管理 TokenScript 文件。本文档详细描述了完整的加载流程和调用的方法。

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

**初始化步骤**:

1. 依赖注入完成
2. 协程作用域创建 (`serviceScope`)
3. 调度器初始化 (`ioDispatcher`, `mainDispatcher`)
4. 数据成员初始化 (`cachedDefinition`, `eventList`, `assetChecked`)
5. 信号量创建 (`assetLoadingLock`)

### 2. 主加载入口

#### `loadAssetScripts()` - 主加载方法

```kotlin
private fun loadAssetScripts() {
    try {
        // 1. 获取信号量防止并发
        assetLoadingLock.acquire()
    } catch (e: InterruptedException) {
        Timber.e(e)
    }

    // 2. 检查Realm脚本变更 (已注释)
    // val handledHashes = checkRealmScriptsForChanges()

    // 3. 加载新文件 (已注释)
    // loadNewFiles(handledHashes.toMutableList())

    // 4. 加载内部资产
    loadInternalAssets()

    // 5. 完成加载
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
            try {
                addContractAssets(asset)
            } catch (e: Exception) {
                Timber.e(e, "加载捆绑资产失败: $asset")
            }
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
    } catch (e: IOException) {
        Timber.e(e, "获取本地 TSML 文件失败")
    } catch (e: Exception) {
        Timber.e(e, "访问 assets 目录失败")
    }

    return localTSMLFilesStr
}
```

#### `addContractAssets()` - 处理单个资产文件

```kotlin
private fun addContractAssets(asset: String): Boolean {
    return try {
        Timber.d("开始加载捆绑TokenScript: $asset")

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

    if (fileList.isEmpty()) {
        finishLoading()
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

#### `addToEventList()` - 添加到事件列表

```kotlin
private fun addToEventList(tokenDef: TokenDefinition) {
    // 处理属性事件
    for (attrName in tokenDef.attributes.keys) {
        val attr: Attribute? = tokenDef.attributes[attrName]
        if (attr != null) {
            if (attr.event != null && attr.event.contract != null) {
                checkAddToEventList(attr.event)
            }
        }
    }

    // 处理活动卡片事件
    if (tokenDef.activityCards.isNotEmpty()) {
        for (activityName in tokenDef.activityCards.keys) {
            val ev: EventDefinition = tokenDef.getActivityEvent(activityName)
            checkAddToEventList(ev)
        }
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
            Timber.w("检测到安全区域的过期脚本，删除文件并重新下载")
            removeFile(file.absolutePath)
            loadScriptFromServer(primaryChainId, getFileName(file) ?: return@use)
            return@use
        }

        // 2. 计算文件哈希
        val fileHash = file.calcMD5()
        Timber.d("文件哈希: $fileHash")

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
            Timber.v("创建新的TokenScript数据条目: $entryKey")
        }

        // 2. 检查是否可以更新文件路径
        if (canUpdateFilePath(entry, file)) {
            updateTokenScriptData(entry, file, td, hasEvents, fileHash)
            Timber.v("成功更新TokenScript数据: $entryKey")
        } else {
            Timber.d("跳过更新TokenScript数据: $entryKey (安全区域限制)")
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

#### `storeCertificateData()` - 存储证书数据

```kotlin
@Throws(RealmException::class)
private fun storeCertificateData(hash: String, sig: XMLDsigDescriptor) {
    realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
        realm.executeTransaction { r: Realm ->
            var realmData = r.where(RealmCertificateData::class.java)
                .equalTo("instanceKey", hash)
                .findFirst()

            if (realmData == null) {
                realmData = r.createObject(RealmCertificateData::class.java, hash)
            }
            realmData!!.setFromSig(sig)
            r.insertOrUpdate(realmData)
        }
    }
}
```

## 📊 完整方法调用流程图

```
AssetDefinitionService 初始化
├── 构造函数注入依赖
├── 创建协程作用域 (serviceScope)
├── 初始化调度器 (ioDispatcher, mainDispatcher)
└── 初始化数据成员 (cachedDefinition, eventList, assetChecked)

loadAssetScripts() [主入口]
├── assetLoadingLock.acquire() [获取信号量]
├── loadInternalAssets() [加载内部资产]
│   ├── deleteAllInternalScriptFromRealm() [删除旧脚本]
│   ├── getLocalTSMLFiles() [获取本地文件列表]
│   │   └── context.resources.assets.list("") [列出assets文件]
│   └── addContractAssets() [处理每个文件]
│       ├── context.resources.assets.open(asset) [打开文件]
│       ├── parseFile(input) [解析TokenScript]
│       ├── TokenScriptFile(context, asset) [创建文件对象]
│       ├── token.contracts.get(token.holdingToken) [获取合约信息]
│       ├── updateRealmForBundledScript() [更新数据库]
│       └── processSignatureAndCertificate() [处理签名]
│           ├── tsf.calcMD5() [计算哈希]
│           ├── tsf.determineSignatureType() [确定签名类型]
│           └── storeCertificateData() [存储证书]
│
├── loadNewFiles() [加载外部文件] (已注释)
│   ├── buildFileList() [构建文件列表]
│   │   ├── checkReadPermission() [检查权限]
│   │   ├── Environment.getExternalStorageDirectory() [外部存储]
│   │   ├── context.getExternalFilesDir("") [应用外部目录]
│   │   └── context.filesDir [应用内部目录]
│   ├── allowableExtension(file) [检查文件扩展名]
│   ├── file.canRead() [检查文件可读性]
│   ├── TokenScriptFile(context, file.absolutePath) [创建文件对象]
│   ├── tsf.calcMD5() [计算文件哈希]
│   ├── parseFile(tsf.getInputStreamSafe()) [解析文件]
│   ├── cacheSignature(file, td) [缓存签名]
│   ├── getOriginContracts(td) [获取原始合约]
│   └── fileLoadComplete() [完成文件加载]
│       ├── getPrimaryChainId() [获取主要链ID]
│       ├── td.hasEvents() [检查是否有事件]
│       └── processRealmDatabaseUpdate() [处理数据库更新]
│           ├── isInSecureZone(file) [检查安全区域]
│           ├── td.isSchemaLessThanMinimum [检查版本]
│           ├── removeFile() [删除文件]
│           ├── loadScriptFromServer() [从服务器加载]
│           ├── file.calcMD5() [计算哈希]
│           └── processContractData() [处理合约数据]
│               ├── getTSDataKey() [获取数据键]
│               ├── realm.where().findFirst() [查找现有条目]
│               ├── realm.createObject() [创建新条目]
│               ├── canUpdateFilePath() [检查是否可以更新]
│               └── updateTokenScriptData() [更新数据]
│                   ├── entry.setNames() [设置名称]
│                   ├── entry.setViewList() [设置视图]
│                   ├── entry.setHasEvents() [设置事件]
│                   └── entry.schemaUID = [设置模式UID]
│
└── finishLoading() [完成加载]

事件监听流程
├── startEventListener() [启动事件监听]
│   ├── stopEventListener() [停止现有监听]
│   └── checkEventsAsync() [异步检查事件]
│       ├── getEventAsync() [异步获取事件]
│       └── handleLogsAsync() [异步处理日志]
└── stopEventListener() [停止事件监听]
```

## 🔧 关键方法详细说明

### 核心加载方法

- **`loadAssetScripts()`**: 主加载入口，协调整个加载流程
- **`loadInternalAssets()`**: 加载捆绑的TokenScript文件
- **`loadNewFiles()`**: 加载外部TokenScript文件
- **`addContractAssets()`**: 处理单个资产文件

### 文件处理方法

- **`buildFileList()`**: 构建文件列表，按优先级排序
- **`parseFile()`**: 解析TokenScript XML文件
- **`getLocalTSMLFiles()`**: 获取本地TSML文件列表
- **`allowableExtension()`**: 检查文件扩展名

### 数据处理方法

- **`getOriginContracts()`**: 获取原始合约信息
- **`addToEventList()`**: 添加到事件列表
- **`checkAddToEventList()`**: 检查并添加事件
- **`fileLoadComplete()`**: 完成文件加载后的处理

### 数据库操作方法

- **`processRealmDatabaseUpdate()`**: 处理Realm数据库更新
- **`processContractData()`**: 处理单个合约数据
- **`updateTokenScriptData()`**: 更新TokenScript数据
- **`storeCertificateData()`**: 存储证书数据
- **`deleteAllInternalScriptFromRealm()`**: 删除所有内部脚本

### 签名和证书方法

- **`processSignatureAndCertificate()`**: 处理签名和证书
- **`getInputStreamSafe()`**: 安全获取输入流

### 事件处理方法

- **`startEventListener()`**: 启动事件监听器
- **`checkEventsAsync()`**: 异步检查事件
- **`getEventAsync()`**: 异步获取事件
- **`handleLogsAsync()`**: 异步处理日志

## ⚡ 性能优化特点

1. **协程优化**: 使用协程替代RxJava，提供更好的异步处理
2. **并发控制**: 使用信号量防止并发加载竞态条件
3. **批量处理**: 文件分批处理，避免资源过载
4. **缓存机制**: 缓存TokenDefinition避免重复解析
5. **错误处理**: 完善的异常处理和日志记录
6. **资源管理**: 自动资源释放和协程生命周期管理
7. **文件优先级**: 明确的文件加载优先级顺序

## 🔒 线程安全保证

- 使用 `ConcurrentHashMap` 保证线程安全
- 使用信号量控制并发访问
- 协程作用域管理生命周期
- 明确的线程调度器使用
- Realm数据库事务保证数据一致性

## 📁 文件来源优先级

1. **AlphaWallet目录** (最高优先级) - 用户自定义脚本
2. **应用外部目录** - 应用下载的脚本
3. **应用内部目录** - 服务器下载的脚本
4. **Assets目录** - 捆绑的脚本 (最低优先级)

这个完整的加载流程确保了TokenScript文件能够正确加载、解析、验证和存储，同时提供了完善的错误处理和性能优化。
