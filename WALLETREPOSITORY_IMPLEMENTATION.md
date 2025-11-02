# WalletRepository 实现转换对比

## 🎯 转换概述

将 `WalletRepository.java` 转换为 `WalletRepository.kt`，使用协程替代 RxJava，并实现新的 `WalletRepositoryType` 接口。

## 📊 主要变化

### 1. 语言转换 (Java → Kotlin)

#### 类声明

```java
// Java
public class WalletRepository implements WalletRepositoryType {
    private final PreferenceRepositoryType preferenceRepositoryType;
    private final AccountKeystoreService accountKeystoreService;
    // ...

    public WalletRepository(PreferenceRepositoryType preferenceRepositoryType,
                          AccountKeystoreService accountKeystoreService,
                          EthereumNetworkRepositoryType networkRepository,
                          WalletDataRealmSource walletDataRealmSource,
                          KeyService keyService) {
        // 构造函数
    }
}
```

```kotlin
// Kotlin
class WalletRepository(
    private val preferenceRepositoryType: PreferenceRepositoryType,
    private val accountKeystoreService: AccountKeystoreService,
    private val networkRepository: EthereumNetworkRepositoryType,
    private val walletDataRealmSource: WalletDataRealmSource,
    private val keyService: KeyService
) : WalletRepositoryType {
    // 主构造函数
}
```

#### 方法实现

```java
// Java + RxJava
@Override
public Single<Wallet[]> fetchWallets() {
    return accountKeystoreService.fetchAccounts()
            .flatMap(wallets -> walletDataRealmSource.populateWalletData(wallets, keyService))
            .map(wallets -> {
                if (preferenceRepositoryType.getCurrentWalletAddress() == null && wallets.length > 0) {
                    preferenceRepositoryType.setCurrentWalletAddress(wallets[0].address);
                }
                return wallets;
            });
}
```

```kotlin
// Kotlin + 协程
override suspend fun fetchWallets(): Array<Wallet> {
    return withContext(Dispatchers.IO) {
        try {
            val wallets = accountKeystoreService.fetchAccounts()
            val populatedWallets = walletDataRealmSource.populateWalletData(wallets, keyService)

            // 设置默认钱包
            if (preferenceRepositoryType.getCurrentWalletAddress() == null && populatedWallets.isNotEmpty()) {
                preferenceRepositoryType.setCurrentWalletAddress(populatedWallets[0].address)
            }

            populatedWallets
        } catch (e: Exception) {
            Timber.e(e, "Error fetching wallets")
            throw e
        }
    }
}
```

### 2. RxJava → 协程转换

#### 异步操作转换

```java
// Java + RxJava
@Override
public Single<Wallet> findWallet(String address) {
    return fetchWallets()
            .flatMap(wallets -> {
                if (wallets.length == 0) return Single.error(new NoWallets("No wallets"));
                Wallet firstWallet = null;
                for (Wallet wallet : wallets) {
                    if (address == null || wallet.sameAddress(address)) {
                        return Single.just(wallet);
                    }
                    if (firstWallet == null) firstWallet = wallet;
                }
                return Single.just(firstWallet);
            });
}
```

```kotlin
// Kotlin + 协程
override suspend fun findWallet(address: String): Wallet {
    return withContext(Dispatchers.IO) {
        try {
            val wallets = fetchWallets()
            if (wallets.isEmpty()) {
                throw NoWallets("No wallets")
            }

            // 如果地址为空，返回第一个钱包
            if (address.isNullOrEmpty()) {
                return@withContext wallets[0]
            }

            // 查找指定地址的钱包
            wallets.find { it.sameAddress(address) }
                ?: wallets[0] // 如果没找到，返回第一个钱包
        } catch (e: Exception) {
            Timber.e(e, "Error finding wallet: $address")
            throw e
        }
    }
}
```

#### 错误处理改进

```java
// Java + RxJava
@Override
public Single<Wallet> createWallet(String password) {
    return accountKeystoreService.createAccount(password);
}
```

```kotlin
// Kotlin + 协程
override suspend fun createWallet(password: String): Wallet {
    return withContext(Dispatchers.IO) {
        try {
            accountKeystoreService.createAccount(password)
        } catch (e: Exception) {
            Timber.e(e, "Error creating wallet")
            throw e
        }
    }
}
```

### 3. 新增功能

#### Flow 支持

```kotlin
// 新增：响应式数据流
override fun getWalletsFlow(): Flow<Array<Wallet>> = flow {
    try {
        val wallets = fetchWallets()
        emit(wallets)
    } catch (e: Exception) {
        Timber.e(e, "Error in wallets flow")
        emit(emptyArray())
    }
}

override fun getDefaultWalletFlow(): Flow<Wallet?> = flow {
    try {
        val defaultWallet = getDefaultWallet()
        emit(defaultWallet)
    } catch (e: Exception) {
        Timber.e(e, "Error in default wallet flow")
        emit(null)
    }
}
```

#### 扩展方法

```kotlin
// 新增：安全的钱包操作包装器
suspend fun <T> safeWalletOperation(operation: suspend () -> T): Result<T> {
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        Timber.e(e, "Wallet operation failed")
        Result.failure(e)
    }
}

// 新增：批量导入钱包
suspend fun importMultipleWallets(walletDataList: List<WalletImportData>): Result<Array<Wallet>> {
    return safeWalletOperation {
        val importedWallets = walletDataList.map { walletData ->
            when (walletData.type) {
                WalletImportType.KEYSTORE -> importKeystoreToWallet(
                    walletData.data,
                    walletData.password,
                    walletData.newPassword
                )
                WalletImportType.PRIVATE_KEY -> importPrivateKeyToWallet(
                    walletData.data,
                    walletData.newPassword
                )
            }
        }

        storeWallets(importedWallets.toTypedArray())
    }
}
```

#### 数据类和枚举

```kotlin
// 新增：钱包导入数据类型
data class WalletImportData(
    val type: WalletImportType,
    val data: String,
    val password: String = "",
    val newPassword: String
)

// 新增：钱包导入类型
enum class WalletImportType {
    KEYSTORE,
    PRIVATE_KEY
}

// 新增：无钱包异常
class NoWallets(message: String) : Exception(message)
```

## 🚀 使用示例

### 1. 基本使用

#### 转换前 (Java + RxJava)

```java
// 获取钱包列表
walletRepository.fetchWallets()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        wallets -> updateWallets(wallets),
        error -> handleError(error)
    );

// 创建钱包
walletRepository.createWallet(password)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        wallet -> onWalletCreated(wallet),
        error -> handleError(error)
    );
```

#### 转换后 (Kotlin + 协程)

```kotlin
// 获取钱包列表
viewModel.launchSafely(
    onError = { error -> handleError(error) }
) {
    val wallets = walletRepository.fetchWallets()
    withMain {
        updateWallets(wallets)
    }
}

// 创建钱包
viewModel.launchSafely(
    onError = { error -> handleError(error) }
) {
    val wallet = walletRepository.createWallet(password)
    withMain {
        onWalletCreated(wallet)
    }
}
```

### 2. Flow 监听

```kotlin
// 监听钱包变化
viewModel.launchSafely {
    walletRepository.getWalletsFlow()
        .collect { wallets ->
            withMain {
                updateWallets(wallets)
            }
        }
}

// 监听默认钱包变化
viewModel.launchSafely {
    walletRepository.getDefaultWalletFlow()
        .collect { wallet ->
            withMain {
                updateDefaultWallet(wallet)
            }
        }
}
```

### 3. 批量操作

```kotlin
// 批量导入钱包
val walletDataList = listOf(
    WalletImportData(
        type = WalletImportType.KEYSTORE,
        data = keystoreData,
        password = oldPassword,
        newPassword = newPassword
    ),
    WalletImportData(
        type = WalletImportType.PRIVATE_KEY,
        data = privateKey,
        newPassword = newPassword
    )
)

val result = walletRepository.importMultipleWallets(walletDataList)
result.onSuccess { wallets ->
    // 处理成功
}.onFailure { error ->
    // 处理错误
}
```

### 4. 安全操作

```kotlin
// 使用安全操作包装器
val result = walletRepository.safeWalletOperation {
    walletRepository.createWallet(password)
}

result.onSuccess { wallet ->
    // 处理成功
}.onFailure { error ->
    // 处理错误
}
```

## 📈 性能改进

### 1. 内存使用

- **RxJava**: 需要管理 Disposable，容易内存泄漏
- **协程**: 自动管理生命周期，减少内存泄漏风险

### 2. 启动时间

- **RxJava**: 需要初始化 Schedulers
- **协程**: 轻量级，启动更快

### 3. 调试体验

- **RxJava**: 调试复杂，堆栈跟踪困难
- **协程**: 调试简单，堆栈跟踪清晰

### 4. 错误处理

- **RxJava**: 错误处理分散，难以统一管理
- **协程**: 统一的 try-catch 错误处理

## 🔧 迁移步骤

### 1. 更新构造函数

```kotlin
// 从 Java 构造函数
public WalletRepository(PreferenceRepositoryType preferenceRepositoryType,
                       AccountKeystoreService accountKeystoreService,
                       EthereumNetworkRepositoryType networkRepository,
                       WalletDataRealmSource walletDataRealmSource,
                       KeyService keyService) {
    // 构造函数实现
}

// 到 Kotlin 主构造函数
class WalletRepository(
    private val preferenceRepositoryType: PreferenceRepositoryType,
    private val accountKeystoreService: AccountKeystoreService,
    private val networkRepository: EthereumNetworkRepositoryType,
    private val walletDataRealmSource: WalletDataRealmSource,
    private val keyService: KeyService
) : WalletRepositoryType
```

### 2. 更新方法实现

```kotlin
// 从 RxJava 方法
@Override
public Single<Wallet[]> fetchWallets() {
    return accountKeystoreService.fetchAccounts()
            .flatMap(wallets -> walletDataRealmSource.populateWalletData(wallets, keyService))
            .map(wallets -> {
                // 处理逻辑
                return wallets;
            });
}

// 到协程方法
override suspend fun fetchWallets(): Array<Wallet> {
    return withContext(Dispatchers.IO) {
        try {
            val wallets = accountKeystoreService.fetchAccounts()
            val populatedWallets = walletDataRealmSource.populateWalletData(wallets, keyService)
            // 处理逻辑
            populatedWallets
        } catch (e: Exception) {
            Timber.e(e, "Error fetching wallets")
            throw e
        }
    }
}
```

### 3. 更新调用方

```kotlin
// 从 RxJava 调用
walletRepository.fetchWallets()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { wallets -> updateWallets(wallets) },
        { error -> handleError(error) }
    )

// 到协程调用
viewModel.launchSafely(
    onError = { error -> handleError(error) }
) {
    val wallets = walletRepository.fetchWallets()
    withMain {
        updateWallets(wallets)
    }
}
```

## ✅ 转换完成

- [x] Java 类转换为 Kotlin
- [x] RxJava Single 替换为 suspend fun
- [x] RxJava Completable 替换为 suspend fun
- [x] void 方法转换为 suspend fun
- [x] 添加 Flow 支持
- [x] 添加扩展方法
- [x] 添加数据类和枚举
- [x] 改进错误处理
- [x] 实现 WalletRepositoryType 接口
- [x] 添加详细文档
- [x] 保持功能兼容性

## 📚 下一步

1. **更新依赖注入配置**
2. **更新所有调用方**
3. **添加单元测试**
4. **性能测试**
5. **文档更新**

---

**转换状态**: ✅ 完成  
**实现状态**: ✅ 协程化  
**接口实现**: ✅ 完整  
**兼容性**: ✅ 保持功能  
**性能**: ✅ 预期提升  
**可维护性**: ✅ 显著改善
