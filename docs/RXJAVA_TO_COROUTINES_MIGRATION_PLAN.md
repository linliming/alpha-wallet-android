# RxJava 到 Kotlin 协程迁移计划

## 概述

本计划将 AlphaWallet Android 项目从 RxJava 逐步迁移到 Kotlin 协程，确保项目稳定性和性能提升。

## 第一阶段：RxJava3 降级到 RxJava2

### 目标

- 确保项目能够正常编译和运行
- 统一 RxJava 版本，避免版本冲突

### 步骤

1. **运行降级脚本**

    ```bash
    chmod +x scripts/downgrade-rxjava3-to-rxjava2.sh
    ./scripts/downgrade-rxjava3-to-rxjava2.sh
    ```

2. **验证编译**

    ```bash
    ./gradlew clean build
    ```

3. **修复编译错误**
    - 检查 API 差异
    - 修复不兼容的方法调用

## 第二阶段：创建协程基础设施

### 目标

- 建立协程工具类和扩展函数
- 为 Web3j 创建协程包装器

### 1. 创建协程工具类

```kotlin
// app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt
object CoroutineUtils {
    /**
     * 将 RxJava Single 转换为协程
     */
    suspend fun <T> Single<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            subscribe(
                { result -> continuation.resume(result) },
                { error -> continuation.resumeWithException(error) }
            )
        }
    }

    /**
     * 将 RxJava Observable 转换为 Flow
     */
    fun <T> Observable<T>.toFlow(): Flow<T> = flow {
        val disposable = subscribe(
            { emit(it) },
            { throw it }
        )
        try {
            awaitCancellation()
        } finally {
            disposable.dispose()
        }
    }
}
```

### 2. 创建 Web3j 协程扩展

```kotlin
// app/src/main/java/com/alphawallet/app/web3j/Web3jCoroutineExtensions.kt
class Web3jCoroutineExtensions(private val web3j: Web3j) {

    suspend fun getBalance(address: String): BigInteger {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.value)
                            }
                        }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    suspend fun callSmartContract(
        to: String,
        data: String,
        from: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val request = Transaction.createFunctionCallTransaction(
                        from, null, null, null, to, null, data
                    )

                    web3j.ethCall(request, DefaultBlockParameterName.LATEST)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.value)
                            }
                        }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
```

## 第三阶段：模块迁移（按优先级）

### 优先级 1：Repository 层

1. **TokenRepository** - 核心数据层
2. **TransactionRepository** - 交易相关
3. **WalletRepository** - 钱包管理

### 优先级 2：Service 层

1. **GasService** - Gas 估算
2. **TokensService** - Token 管理
3. **TransactionsService** - 交易服务

### 优先级 3：Interact 层

1. **GenericWalletInteract** - 钱包交互
2. **FetchTransactionsInteract** - 交易获取

### 优先级 4：UI 层

1. **ViewModel** 类
2. **Widget** 组件

## 第四阶段：测试和验证

### 单元测试

- 为每个迁移的模块编写协程测试
- 验证功能一致性

### 集成测试

- 端到端功能测试
- 性能对比测试

### 手动测试

- UI 交互测试
- 钱包操作测试

## 第五阶段：清理和优化

### 移除 RxJava 依赖

1. 删除 RxJava 相关导入
2. 移除 RxJava 依赖配置
3. 清理未使用的代码

### 性能优化

1. 协程作用域优化
2. 异常处理优化
3. 内存使用优化

## 迁移模板

### Repository 迁移模板

```kotlin
// 原 RxJava 版本
interface TokenRepositoryType {
    fun getTokens(): Single<List<Token>>
    fun addToken(token: Token): Single<Token>
}

// 协程版本
interface TokenRepositoryType {
    suspend fun getTokens(): List<Token>
    suspend fun addToken(token: Token): Token
}
```

### Service 迁移模板

```kotlin
// 原 RxJava 版本
class TokenService {
    fun fetchTokens(): Single<List<Token>> {
        return tokenRepository.getTokens()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}

// 协程版本
class TokenService {
    suspend fun fetchTokens(): List<Token> {
        return withContext(Dispatchers.IO) {
            tokenRepository.getTokens()
        }
    }
}
```

### ViewModel 迁移模板

```kotlin
// 原 RxJava 版本
class TokenViewModel : ViewModel() {
    fun loadTokens() {
        tokenService.fetchTokens()
            .subscribe(
                { tokens -> _tokens.value = tokens },
                { error -> _error.value = error.message }
            )
    }
}

// 协程版本
class TokenViewModel : ViewModel() {
    fun loadTokens() {
        viewModelScope.launch {
            try {
                val tokens = tokenService.fetchTokens()
                _tokens.value = tokens
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
```

## 风险控制

### 回滚策略

1. 保持 Git 分支备份
2. 每个阶段完成后提交
3. 准备快速回滚脚本

### 监控指标

1. 编译时间
2. 运行时性能
3. 内存使用情况
4. 崩溃率

## 时间安排

- **第一阶段**：1-2 天
- **第二阶段**：2-3 天
- **第三阶段**：1-2 周
- **第四阶段**：3-5 天
- **第五阶段**：2-3 天

**总计**：约 3-4 周

## 成功标准

1. ✅ 项目正常编译和运行
2. ✅ 所有功能测试通过
3. ✅ 性能不降低或有所提升
4. ✅ 代码可读性和维护性提升
5. ✅ 完全移除 RxJava 依赖
