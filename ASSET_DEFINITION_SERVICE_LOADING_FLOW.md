# AssetDefinitionService 加载流程及方法调用图

## 📋 概述

`AssetDefinitionService` 是处理 TokenScript 文件的核心服务类，负责加载、解析、验证和管理 TokenScript 文件。

## 🏗️ 类结构

### 核心组件

- **协程作用域**: `serviceScope` - 管理所有协程生命周期
- **调度器**: `ioDispatcher`, `mainDispatcher` - 线程调度
- **数据缓存**: `cachedDefinition`, `eventList`, `assetChecked`
- **锁机制**: `assetLoadingLock` - 防止并发加载竞态条件

## 🔄 主要加载流程

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

**初始化顺序**:

1. 依赖注入完成
2. 协程作用域创建
3. 数据成员初始化
4. 事件监听器准备

### 2. 主要加载入口

#### `loadAssetScripts()` - 主加载方法

```kotlin
private fun loadAssetScripts() {
    // 1. 获取信号量防止并发
    assetLoadingLock.acquire()

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

        // 2. 加载本地TSML文件
        val localFiles = localTSMLFiles

        // 3. 处理每个文件
        localFiles.forEach { asset ->
            addContractAssets(asset)
        }
    }
}
```

#### `addContractAssets()` - 处理单个资产文件

```kotlin
private fun addContractAssets(asset: String): Boolean {
    return try {
        // 1. 从assets目录打开文件并解析
        context.resources.assets.open(asset).use { input ->
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

### 4. 新文件加载流程

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

### 5. 文件完成处理流程

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

### 6. 事件监听流程

#### `startEventListener()` - 启动事件监听

```kotlin
fun startEventListener() {
    if (assetLoadingLock.availablePermits() == 0) return

    stopEventListener()

    eventListenerJob = CoroutineUtils.launchSafely(
        scope = serviceScope,
        dispatcher = ioDispatcher,
        onError = { error ->
            Timber.e(error, "事件监听器启动失败")
        }
    ) {
        while (isActive) {
            try {
                checkEventsAsync()
                delay(CHECK_TX_LOGS_INTERVAL * 1000)
            } catch (e: Exception) {
                Timber.e(e, "事件检查过程中发生错误")
                delay(CHECK_TX_LOGS_INTERVAL * 1000)
            }
        }
    }
}
```

#### `checkEventsAsync()` - 异步检查事件

```kotlin
private suspend fun checkEventsAsync() {
    withContext(ioDispatcher) {
        for (ev in eventList.values) {
            try {
                getEventAsync(ev)
            } catch (e: Exception) {
                Timber.e(e, "处理事件失败: ${ev.getEventKey()}")
            }
        }
    }
}
```

## 📊 方法调用关系图

```
AssetDefinitionService
├── 初始化
│   ├── 构造函数注入依赖
│   ├── 创建协程作用域
│   └── 初始化数据成员
│
├── 主要加载流程
│   ├── loadAssetScripts()
│   │   ├── loadInternalAssets()
│   │   │   ├── deleteAllInternalScriptFromRealm()
│   │   │   ├── localTSMLFiles (getter)
│   │   │   └── addContractAssets()
│   │   │       ├── parseFile()
│   │   │       ├── updateRealmForBundledScript()
│   │   │       └── processSignatureAndCertificate()
│   │   │
│   │   ├── loadNewFiles() (已注释)
│   │   │   ├── buildFileList()
│   │   │   ├── parseFile()
│   │   │   ├── cacheSignature()
│   │   │   ├── getOriginContracts()
│   │   │   └── fileLoadComplete()
│   │   │
│   │   └── finishLoading()
│   │
│   └── 事件监听
│       ├── startEventListener()
│       │   ├── checkEventsAsync()
│       │   │   └── getEventAsync()
│       │   │       └── handleLogsAsync()
│       │   └── stopEventListener()
│
└── 辅助方法
    ├── parseFile()
    ├── getOriginContracts()
    ├── processRealmDatabaseUpdate()
    ├── processContractData()
    ├── updateTokenScriptData()
    └── processSignatureAndCertificate()
```

## 🔧 关键方法说明

### 核心加载方法

- **`loadAssetScripts()`**: 主加载入口，协调整个加载流程
- **`loadInternalAssets()`**: 加载捆绑的TokenScript文件
- **`loadNewFiles()`**: 加载外部TokenScript文件
- **`fileLoadComplete()`**: 完成文件加载后的处理

### 数据处理方法

- **`parseFile()`**: 解析TokenScript XML文件
- **`getOriginContracts()`**: 获取原始合约信息
- **`processRealmDatabaseUpdate()`**: 更新Realm数据库
- **`addContractAssets()`**: 处理单个资产文件

### 事件处理方法

- **`startEventListener()`**: 启动事件监听器
- **`checkEventsAsync()`**: 异步检查事件
- **`getEventAsync()`**: 异步获取事件
- **`handleLogsAsync()`**: 异步处理日志

### 辅助方法

- **`buildFileList()`**: 构建文件列表
- **`allowableExtension()`**: 检查文件扩展名
- **`getPrimaryChainId()`**: 获取主要链ID
- **`processSignatureAndCertificate()`**: 处理签名和证书

## ⚡ 性能优化特点

1. **协程优化**: 使用协程替代RxJava，提供更好的异步处理
2. **并发控制**: 使用信号量防止并发加载竞态条件
3. **批量处理**: 文件分批处理，避免资源过载
4. **缓存机制**: 缓存TokenDefinition避免重复解析
5. **错误处理**: 完善的异常处理和日志记录
6. **资源管理**: 自动资源释放和协程生命周期管理

## 🔒 线程安全

- 使用 `ConcurrentHashMap` 保证线程安全
- 使用信号量控制并发访问
- 协程作用域管理生命周期
- 明确的线程调度器使用
