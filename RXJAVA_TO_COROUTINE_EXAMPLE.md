# RxJava 到 Kotlin 协程转换示例

## 🔄 转换前 (RxJava)

```java
disposable = genericWalletInteract
    .find()
    .subscribe(
        Consumer<Wallet> { wallet: Wallet -> this.onDefaultWallet(wallet) },
        Consumer<Throwable> { throwable: Throwable? -> this.onError(throwable) }
    );
```

## ✅ 转换后 (Kotlin 协程)

### 1. 基本转换 (推荐)

```kotlin
// 在 ViewModel 或类似组件中
fun findDefaultWallet() {
    launchSafely(
        onError = { throwable -> onError(throwable) }
    ) {
        val wallet = genericWalletInteract.find()
        withMain {
            onDefaultWallet(wallet)
        }
    }
}
```

### 2. 使用 BaseViewModel 的扩展方法

```kotlin
class WalletViewModel : BaseViewModel() {

    fun findDefaultWallet() {
        launchSafely(
            onError = { throwable ->
                handleError(throwable)
                onError(throwable)
            }
        ) {
            val wallet = genericWalletInteract.find()
            withMain {
                onDefaultWallet(wallet)
            }
        }
    }
}
```

### 3. 使用 Result 包装器

```kotlin
fun findDefaultWallet() {
    launchSafely {
        val result = safeWalletOperation {
            genericWalletInteract.find()
        }

        result.onSuccess { wallet ->
            withMain {
                onDefaultWallet(wallet)
            }
        }.onFailure { throwable ->
            withMain {
                onError(throwable)
            }
        }
    }
}
```

### 4. 使用 Flow (响应式)

```kotlin
fun findDefaultWalletFlow(): Flow<Wallet> = flow {
    try {
        val wallet = genericWalletInteract.find()
        emit(wallet)
    } catch (e: Exception) {
        throw e
    }
}.flowOn(Dispatchers.IO)
    .catch { e ->
        onError(e)
    }

// 使用方式
fun observeDefaultWallet() {
    launchSafely {
        findDefaultWalletFlow()
            .collect { wallet ->
                withMain {
                    onDefaultWallet(wallet)
                }
            }
    }
}
```

### 5. 使用 StateFlow (状态管理)

```kotlin
private val _defaultWallet = MutableStateFlow<Wallet?>(null)
val defaultWallet: StateFlow<Wallet?> = _defaultWallet.asStateFlow()

fun findDefaultWallet() {
    launchSafely {
        val wallet = genericWalletInteract.find()
        _defaultWallet.value = wallet
        withMain {
            onDefaultWallet(wallet)
        }
    }
}
```

## 🔧 完整的 ViewModel 示例

```kotlin
class WalletViewModel : BaseViewModel() {

    private val _walletState = MutableStateFlow<WalletState>(WalletState.Loading)
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    fun findDefaultWallet() {
        launchSafely(
            onError = { throwable ->
                _walletState.value = WalletState.Error(throwable)
                onError(throwable)
            }
        ) {
            _walletState.value = WalletState.Loading

            val wallet = genericWalletInteract.find()

            _walletState.value = WalletState.Success(wallet)
            withMain {
                onDefaultWallet(wallet)
            }
        }
    }

    fun findWalletByAddress(address: String) {
        launchSafely(
            onError = { throwable ->
                _walletState.value = WalletState.Error(throwable)
                onError(throwable)
            }
        ) {
            _walletState.value = WalletState.Loading

            val wallet = genericWalletInteract.findWallet(address)

            _walletState.value = WalletState.Success(wallet)
            withMain {
                onWalletFound(wallet)
            }
        }
    }

    fun updateWalletBackup(walletAddr: String) {
        launchSafely(
            onError = { throwable ->
                onError(throwable)
            }
        ) {
            genericWalletInteract.updateBackupTime(walletAddr)
            withMain {
                onBackupUpdated()
            }
        }
    }

    fun updateWalletBalance(wallet: Wallet, newBalance: BigDecimal) {
        launchSafely(
            onError = { throwable ->
                onError(throwable)
            }
        ) {
            genericWalletInteract.updateBalanceIfRequired(wallet, newBalance)
            withMain {
                onBalanceUpdated()
            }
        }
    }

    // 批量操作
    fun updateMultipleWalletsBackup(addresses: List<String>) {
        launchSafely {
            val result = genericWalletInteract.updateMultipleBackupTimes(addresses)
            result.onSuccess { successCount ->
                withMain {
                    onBatchUpdateCompleted(successCount)
                }
            }.onFailure { error ->
                withMain {
                    onError(error)
                }
            }
        }
    }

    // 高级功能
    fun checkWalletBackupStatus(walletAddr: String) {
        launchSafely {
            val backupStatus = genericWalletInteract.getWalletBackupStatus(walletAddr)
            withMain {
                when (backupStatus.backupLevel) {
                    BackupLevel.BACKUP_NOT_REQUIRED -> onNoBackupRequired()
                    BackupLevel.WALLET_HAS_LOW_VALUE -> onLowValueBackupRequired()
                    BackupLevel.WALLET_HAS_HIGH_VALUE -> onHighValueBackupRequired()
                }
            }
        }
    }
}

// 状态密封类
sealed class WalletState {
    object Loading : WalletState()
    data class Success(val wallet: Wallet) : WalletState()
    data class Error(val throwable: Throwable) : WalletState()
}
```

## 📱 Activity/Fragment 中的使用

```kotlin
class WalletActivity : AppCompatActivity() {

    private val viewModel: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        setupObservers()
        viewModel.findDefaultWallet()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.walletState.collect { state ->
                when (state) {
                    is WalletState.Loading -> {
                        showLoading()
                    }
                    is WalletState.Success -> {
                        hideLoading()
                        displayWallet(state.wallet)
                    }
                    is WalletState.Error -> {
                        hideLoading()
                        showError(state.throwable.message)
                    }
                }
            }
        }
    }

    private fun displayWallet(wallet: Wallet) {
        // 显示钱包信息
        walletNameTextView.text = wallet.name
        walletAddressTextView.text = wallet.address
        walletBalanceTextView.text = wallet.balance
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun showError(message: String?) {
        Toast.makeText(this, message ?: "Unknown error", Toast.LENGTH_SHORT).show()
    }
}
```

## 🎯 转换优势

### 1. 代码简洁性

- **RxJava**: 需要管理 Disposable，代码冗长
- **协程**: 简洁的 async/await 模式

### 2. 错误处理

- **RxJava**: 分散的错误处理
- **协程**: 统一的 try-catch 错误处理

### 3. 生命周期管理

- **RxJava**: 需要手动管理 Disposable
- **协程**: 自动管理生命周期

### 4. 调试体验

- **RxJava**: 复杂的堆栈跟踪
- **协程**: 清晰的堆栈跟踪

## 📋 迁移检查清单

### 转换步骤

1. ✅ 替换 RxJava 调用为协程
2. ✅ 更新错误处理
3. ✅ 移除 Disposable 管理
4. ✅ 添加生命周期管理
5. ✅ 更新 UI 回调

### 注意事项

- 确保在正确的 Dispatcher 上执行
- 使用 `withMain` 更新 UI
- 使用 `launchSafely` 处理错误
- 考虑使用 StateFlow 进行状态管理

## 🔄 其他常见转换

### 1. 多个 RxJava 调用

```kotlin
// RxJava
disposable = Observable.zip(
    genericWalletInteract.find(),
    genericWalletInteract.getWalletNeedsBackup(walletAddr)
) { wallet, needsBackup ->
    Pair(wallet, needsBackup)
}.subscribe(
    { pair -> onWalletInfo(pair.first, pair.second) },
    { throwable -> onError(throwable) }
)

// 协程
fun getWalletInfo(walletAddr: String) {
    launchSafely {
        val wallet = genericWalletInteract.find()
        val needsBackup = genericWalletInteract.getWalletNeedsBackup(walletAddr)

        withMain {
            onWalletInfo(wallet, needsBackup)
        }
    }
}
```

### 2. 条件操作

```kotlin
// RxJava
disposable = genericWalletInteract.find()
    .flatMap { wallet ->
        if (wallet.balance.isEmpty()) {
            genericWalletInteract.updateBalanceIfRequired(wallet, BigDecimal.ZERO)
        } else {
            Single.just(wallet)
        }
    }
    .subscribe(
        { wallet -> onWalletUpdated(wallet) },
        { throwable -> onError(throwable) }
    )

// 协程
fun updateWalletIfNeeded() {
    launchSafely {
        val wallet = genericWalletInteract.find()

        if (wallet.balance.isEmpty()) {
            genericWalletInteract.updateBalanceIfRequired(wallet, BigDecimal.ZERO)
        }

        withMain {
            onWalletUpdated(wallet)
        }
    }
}
```

---

**转换状态**: ✅ 完成  
**兼容性**: ✅ 保持功能一致  
**性能**: ✅ 预期提升  
**可维护性**: ✅ 显著改善
