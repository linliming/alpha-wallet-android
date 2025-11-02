# 模块迁移示例

本文档提供了具体的模块迁移示例，展示如何将 RxJava 代码迁移到 Kotlin 协程。

## 示例 1：TokenRepository 迁移

### 迁移前（RxJava 版本）

```java
// TokenRepositoryType.java
public interface TokenRepositoryType {
    Single<List<Token>> getTokens();
    Single<Token> addToken(Token token);
    Single<Token> updateToken(Token token);
    Single<Boolean> deleteToken(Token token);
}

// TokenRepository.java
public class TokenRepository implements TokenRepositoryType {

    @Override
    public Single<List<Token>> getTokens() {
        return localSource.getTokens()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<Token> addToken(Token token) {
        return localSource.addToken(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<Token> updateToken(Token token) {
        return localSource.updateToken(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<Boolean> deleteToken(Token token) {
        return localSource.deleteToken(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }
}
```

### 迁移后（协程版本）

```kotlin
// TokenRepositoryType.kt
interface TokenRepositoryType {
    suspend fun getTokens(): List<Token>
    suspend fun addToken(token: Token): Token
    suspend fun updateToken(token: Token): Token
    suspend fun deleteToken(token: Token): Boolean
}

// TokenRepository.kt
class TokenRepository @Inject constructor(
    private val localSource: TokenLocalSource,
    private val web3jExtensions: Web3jCoroutineExtensions
) : TokenRepositoryType {

    override suspend fun getTokens(): List<Token> {
        return withContext(Dispatchers.IO) {
            try {
                localSource.getTokens()
            } catch (e: Exception) {
                Timber.e(e, "Error getting tokens")
                throw e
            }
        }
    }

    override suspend fun addToken(token: Token): Token {
        return withContext(Dispatchers.IO) {
            try {
                localSource.addToken(token)
            } catch (e: Exception) {
                Timber.e(e, "Error adding token")
                throw e
            }
        }
    }

    override suspend fun updateToken(token: Token): Token {
        return withContext(Dispatchers.IO) {
            try {
                localSource.updateToken(token)
            } catch (e: Exception) {
                Timber.e(e, "Error updating token")
                throw e
            }
        }
    }

    override suspend fun deleteToken(token: Token): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                localSource.deleteToken(token)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting token")
                throw e
            }
        }
    }

    // 新增：使用协程的 Web3j 调用
    suspend fun getTokenBalance(tokenAddress: String, walletAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                // 调用智能合约获取余额
                val balanceData = web3jExtensions.callSmartContract(
                    to = tokenAddress,
                    data = "0x70a08231000000000000000000000000${walletAddress.substring(2)}"
                )

                // 解析余额数据
                if (balanceData != "0x") {
                    BigInteger(balanceData.substring(2), 16)
                } else {
                    BigInteger.ZERO
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting token balance")
                BigInteger.ZERO
            }
        }
    }
}
```

## 示例 2：GasService 迁移

### 迁移前（RxJava 版本）

```java
// GasService.java
public class GasService {

    public Single<BigInteger> getGasPrice(long chainId) {
        return Single.fromCallable(() -> {
            Web3j web3j = TokenRepository.getWeb3jService(chainId);
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            return gasPrice.getGasPrice();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<BigInteger> estimateGas(long chainId, String from, String to, String data) {
        return Single.fromCallable(() -> {
            Web3j web3j = TokenRepository.getWeb3jService(chainId);
            Transaction transaction = Transaction.createFunctionCallTransaction(
                from, null, null, null, to, null, data
            );
            EthEstimateGas estimate = web3j.ethEstimateGas(transaction).send();
            return estimate.getAmountUsed();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
    }
}
```

### 迁移后（协程版本）

```kotlin
// GasService.kt
@Singleton
class GasService @Inject constructor(
    private val web3jExtensions: Web3jCoroutineExtensions
) {

    suspend fun getGasPrice(chainId: Long): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                web3jExtensions.getGasPrice()
            } catch (e: Exception) {
                Timber.e(e, "Error getting gas price for chain: $chainId")
                // 返回默认 gas price
                BigInteger.valueOf(20_000_000_000L) // 20 Gwei
            }
        }
    }

    suspend fun estimateGas(
        chainId: Long,
        from: String,
        to: String?,
        data: String? = null
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                web3jExtensions.estimateGas(from, to, data)
            } catch (e: Exception) {
                Timber.e(e, "Error estimating gas for chain: $chainId")
                // 返回默认 gas limit
                BigInteger.valueOf(21000L)
            }
        }
    }

    suspend fun getGasEstimate(
        chainId: Long,
        from: String,
        to: String?,
        amount: BigInteger? = null,
        data: String? = null
    ): GasEstimate {
        return withContext(Dispatchers.IO) {
            try {
                val gasPrice = getGasPrice(chainId)
                val gasLimit = estimateGas(chainId, from, to, data)

                GasEstimate(
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    totalGas = gasPrice.multiply(gasLimit)
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting gas estimate")
                throw e
            }
        }
    }
}

// GasEstimate.kt
data class GasEstimate(
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val totalGas: BigInteger
)
```

## 示例 3：ViewModel 迁移

### 迁移前（RxJava 版本）

```java
// TokenViewModel.java
public class TokenViewModel extends ViewModel {

    private MutableLiveData<List<Token>> tokens = new MutableLiveData<>();
    private MutableLiveData<String> error = new MutableLiveData<>();

    public void loadTokens() {
        tokenRepository.getTokens()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                tokens -> this.tokens.setValue(tokens),
                error -> this.error.setValue(error.getMessage())
            );
    }

    public void addToken(Token token) {
        tokenRepository.addToken(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                newToken -> {
                    List<Token> currentTokens = tokens.getValue();
                    if (currentTokens != null) {
                        currentTokens.add(newToken);
                        tokens.setValue(currentTokens);
                    }
                },
                error -> this.error.setValue(error.getMessage())
            );
    }
}
```

### 迁移后（协程版本）

```kotlin
// TokenViewModel.kt
@HiltViewModel
class TokenViewModel @Inject constructor(
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _tokens = MutableLiveData<List<Token>>()
    val tokens: LiveData<List<Token>> = _tokens

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun loadTokens() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val tokenList = tokenRepository.getTokens()
                _tokens.value = tokenList
            } catch (e: Exception) {
                Timber.e(e, "Error loading tokens")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun addToken(token: Token) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val newToken = tokenRepository.addToken(token)
                val currentTokens = _tokens.value?.toMutableList() ?: mutableListOf()
                currentTokens.add(newToken)
                _tokens.value = currentTokens
            } catch (e: Exception) {
                Timber.e(e, "Error adding token")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun refreshTokenBalances() {
        viewModelScope.launch {
            try {
                val currentTokens = _tokens.value ?: return@launch
                val updatedTokens = currentTokens.map { token ->
                    try {
                        val balance = tokenRepository.getTokenBalance(
                            tokenAddress = token.tokenInfo.address,
                            walletAddress = getCurrentWalletAddress()
                        )
                        token.copy(balance = balance)
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting balance for token: ${token.tokenInfo.address}")
                        token
                    }
                }
                _tokens.value = updatedTokens
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing token balances")
                _error.value = e.message ?: "Unknown error"
            }
        }
    }
}
```

## 迁移检查清单

### 代码层面

- [ ] 将所有 `Single<T>` 改为 `suspend fun(): T`
- [ ] 将所有 `Observable<T>` 改为 `Flow<T>`
- [ ] 将所有 `Completable` 改为 `suspend fun()`
- [ ] 移除 `subscribeOn()` 和 `observeOn()` 调用
- [ ] 使用 `withContext(Dispatchers.IO)` 替代 IO 调度器
- [ ] 使用 `viewModelScope.launch` 替代 RxJava 订阅

### 测试层面

- [ ] 更新单元测试以支持协程
- [ ] 使用 `runTest` 或 `runBlocking` 测试协程
- [ ] 验证异步行为正确性
- [ ] 检查错误处理逻辑

### 性能层面

- [ ] 验证内存使用情况
- [ ] 检查协程作用域管理
- [ ] 确保没有内存泄漏
- [ ] 验证线程调度正确性

## 常见问题和解决方案

### 问题 1：协程作用域管理

**解决方案**：使用 `viewModelScope`、`lifecycleScope` 或自定义 `CoroutineScope`

### 问题 2：错误处理

**解决方案**：使用 try-catch 或 `Result<T>` 类型

### 问题 3：取消操作

**解决方案**：使用 `suspendCancellableCoroutine` 和 `invokeOnCancellation`

### 问题 4：测试协程

**解决方案**：使用 `runTest` 和 `TestCoroutineDispatcher`
