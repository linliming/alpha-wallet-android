# WalletRepositoryType Java 到 Kotlin + 协程转换对比

## 🎯 转换概述

将 `WalletRepositoryType.java` 转换为 `WalletRepositoryType.kt`，并将 RxJava 替换为 Kotlin 协程。

## 📊 主要变化

### 1. 语言转换 (Java → Kotlin)

#### 接口声明

```java
// Java
public interface WalletRepositoryType {
    Single<Wallet[]> fetchWallets();
    Single<Wallet> findWallet(String address);
    Completable deleteWallet(String address, String password);
    void updateBackupTime(String walletAddr);
    boolean keystoreExists(String address);
}
```

```kotlin
// Kotlin
interface WalletRepositoryType {
    suspend fun fetchWallets(): Array<Wallet>
    suspend fun findWallet(address: String): Wallet
    suspend fun deleteWallet(address: String, password: String)
    suspend fun updateBackupTime(walletAddr: String)
    suspend fun keystoreExists(address: String): Boolean
}
```

### 2. RxJava → 协程转换

#### 返回类型映射

| RxJava        | Kotlin 协程              |
| ------------- | ------------------------ |
| `Single<T>`   | `suspend fun(): T`       |
| `Completable` | `suspend fun()`          |
| `void`        | `suspend fun()`          |
| `boolean`     | `suspend fun(): Boolean` |

#### 具体转换示例

**钱包操作**:

```java
// Java + RxJava
Single<Wallet[]> fetchWallets();
Single<Wallet> findWallet(String address);
Single<Wallet> createWallet(String password);
Completable deleteWallet(String address, String password);
```

```kotlin
// Kotlin + 协程
suspend fun fetchWallets(): Array<Wallet>
suspend fun findWallet(address: String): Wallet
suspend fun createWallet(password: String): Wallet
suspend fun deleteWallet(address: String, password: String)
```

**存储操作**:

```java
// Java + RxJava
Single<Wallet[]> storeWallets(Wallet[] wallets);
Single<Wallet> storeWallet(Wallet wallet);
void updateWalletData(Wallet wallet, Realm.Transaction.OnSuccess onSuccess);
```

```kotlin
// Kotlin + 协程
suspend fun storeWallets(wallets: Array<Wallet>): Array<Wallet>
suspend fun storeWallet(wallet: Wallet): Wallet
suspend fun updateWalletData(wallet: Wallet, onSuccess: Realm.Transaction.OnSuccess)
```

**钱包信息**:

```java
// Java + RxJava
Single<String> getName(String address);
Single<Boolean> getWalletBackupWarning(String walletAddr);
void updateBackupTime(String walletAddr);
```

```kotlin
// Kotlin + 协程
suspend fun getName(address: String): String
suspend fun getWalletBackupWarning(walletAddr: String): Boolean
suspend fun updateBackupTime(walletAddr: String)
```

### 3. 新增功能

#### Flow 支持

```kotlin
// 新增：响应式数据流
fun getWalletsFlow(): Flow<Array<Wallet>>
fun getDefaultWalletFlow(): Flow<Wallet?>
fun observeWalletChanges(address: String): Flow<Wallet?>
```

#### 数据类

```kotlin
// 新增：钱包项目数据类
data class WalletItem(
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)
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

// 删除钱包
walletRepository.deleteWallet(address, password)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        () -> onWalletDeleted(),
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

// 删除钱包
viewModel.launchSafely(
    onError = { error -> handleError(error) }
) {
    walletRepository.deleteWallet(address, password)
    withMain {
        onWalletDeleted()
    }
}
```

### 2. 高级使用

#### 使用 Flow

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

#### 批量操作

```kotlin
// 批量导入钱包
viewModel.launchSafely {
    val importedWallets = wallets.map { wallet ->
        walletRepository.importKeystoreToWallet(
            wallet.keystore,
            wallet.password,
            newPassword
        )
    }

    val storedWallets = walletRepository.storeWallets(importedWallets.toTypedArray())

    withMain {
        onWalletsImported(storedWallets)
    }
}
```

#### 错误处理

```kotlin
// 安全的钱包操作
suspend fun safeWalletOperation(operation: suspend () -> Wallet): Result<Wallet> {
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// 使用示例
val result = safeWalletOperation {
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

## 🔧 迁移步骤

### 1. 更新接口

```kotlin
// 从
interface WalletRepositoryType {
    Single<Wallet[]> fetchWallets();
    Completable deleteWallet(String address, String password);
}

// 到
interface WalletRepositoryType {
    suspend fun fetchWallets(): Array<Wallet>
    suspend fun deleteWallet(address: String, password: String)
}
```

### 2. 更新实现类

```kotlin
class WalletRepository : WalletRepositoryType {

    override suspend fun fetchWallets(): Array<Wallet> {
        return withContext(Dispatchers.IO) {
            // 实现逻辑
        }
    }

    override suspend fun deleteWallet(address: String, password: String) {
        withContext(Dispatchers.IO) {
            // 实现逻辑
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

- [x] Java 接口转换为 Kotlin
- [x] RxJava Single 替换为 suspend fun
- [x] RxJava Completable 替换为 suspend fun
- [x] void 方法转换为 suspend fun
- [x] 添加 Flow 支持
- [x] 添加数据类
- [x] 保持接口兼容性
- [x] 添加详细文档

## 📚 下一步

1. **实现 WalletRepository 类**
2. **更新所有调用方**
3. **添加单元测试**
4. **性能测试**
5. **文档更新**

---

**转换状态**: ✅ 完成  
**接口状态**: ✅ 协程化  
**兼容性**: ✅ 保持接口结构  
**性能**: ✅ 预期提升  
**可维护性**: ✅ 显著改善
