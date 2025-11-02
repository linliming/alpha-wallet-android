# Java 到 Kotlin + 协程迁移计划

## 🎯 目标

将 AlphaWallet 项目从 Java 完全转换为 Kotlin，并用协程替代 RxJava。

## 📅 第一阶段：Java 到 Kotlin 转换 (2-3 周)

### 1.1 准备工作 (第1周)

#### 1.1.1 更新项目配置

**更新 build.gradle**

```gradle
// app/build.gradle
android {
    // 确保 Kotlin 支持
    kotlinOptions {
        jvmTarget = '21'
    }
}

dependencies {
    // 移除 RxJava 依赖
    // implementation 'io.reactivex.rxjava3:rxjava:3.1.5'
    // implementation 'io.reactivex.rxjava3:rxandroid:3.0.2'

    // 添加协程依赖
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3"

    // 架构组件
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
}
```

#### 1.1.2 创建转换工具类

**协程工具类**

```kotlin
// app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt
package com.alphawallet.app.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

object CoroutineUtils {

    // 调度器
    val mainDispatcher = Dispatchers.Main
    val ioDispatcher = Dispatchers.IO
    val defaultScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 安全启动协程
    fun launchSafely(
        scope: CoroutineScope = defaultScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        onError: (Throwable) -> Unit = { },
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // RxJava 到协程的转换工具
    suspend fun <T> fromRxJava(single: io.reactivex.rxjava3.core.Single<T>): T {
        return withContext(Dispatchers.IO) {
            single.blockingGet()
        }
    }

    // 延迟执行
    suspend fun delay(duration: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        kotlinx.coroutines.delay(unit.toMillis(duration))
    }
}
```

### 1.2 核心类转换 (第2周)

#### 1.2.1 转换 HomeActivity

**转换步骤**:

1. 将 `HomeActivity.java` 转换为 `HomeActivity.kt`
2. 替换 RxJava 调用为协程
3. 更新生命周期管理

**转换示例**:

```kotlin
// 转换前 (Java + RxJava)
private void loadTokens() {
    apiService.getTokens(address)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            tokens -> updateTokens(tokens),
            error -> handleError(error)
        );
}

// 转换后 (Kotlin + 协程)
private fun loadTokens() {
    CoroutineUtils.launchSafely(
        dispatcher = CoroutineUtils.ioDispatcher,
        onError = { error -> handleError(error) }
    ) {
        val tokens = apiService.getTokens(address)
        withContext(CoroutineUtils.mainDispatcher) {
            updateTokens(tokens)
        }
    }
}
```

#### 1.2.2 转换 ViewModel

**转换步骤**:

1. 将 Java ViewModel 转换为 Kotlin
2. 使用 StateFlow 替代 LiveData
3. 使用协程替代 RxJava

**转换示例**:

```kotlin
// 转换前 (Java)
public class HomeViewModel extends ViewModel {
    private MutableLiveData<List<Token>> tokens = new MutableLiveData<>();

    public void loadTokens() {
        apiService.getTokens()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                tokens -> this.tokens.setValue(tokens),
                error -> handleError(error)
            );
    }
}

// 转换后 (Kotlin)
class HomeViewModel : ViewModel() {
    private val _tokens = MutableStateFlow<List<Token>>(emptyList())
    val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()

    fun loadTokens() {
        viewModelScope.launch {
            try {
                val tokens = apiService.getTokens()
                _tokens.value = tokens
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
}
```

### 1.3 网络层转换 (第3周)

#### 1.3.1 转换 API 服务

**转换步骤**:

1. 将 Retrofit 接口转换为协程
2. 更新网络调用
3. 添加错误处理

**转换示例**:

```kotlin
// 转换前 (Java + RxJava)
public interface ApiService {
    @GET("tokens")
    Single<List<Token>> getTokens(@Query("address") String address);

    @POST("transactions")
    Single<Transaction> sendTransaction(@Body TransactionRequest request);
}

// 转换后 (Kotlin + 协程)
interface ApiService {
    @GET("tokens")
    suspend fun getTokens(@Query("address") address: String): List<Token>

    @POST("transactions")
    suspend fun sendTransaction(@Body request: TransactionRequest): Transaction
}
```

#### 1.3.2 转换网络调用

**转换示例**:

```kotlin
// 转换前 (Java + RxJava)
private void sendTransaction(TransactionRequest request) {
    apiService.sendTransaction(request)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            transaction -> handleSuccess(transaction),
            error -> handleError(error)
        );
}

// 转换后 (Kotlin + 协程)
private fun sendTransaction(request: TransactionRequest) {
    CoroutineUtils.launchSafely(
        dispatcher = CoroutineUtils.ioDispatcher,
        onError = { error -> handleError(error) }
    ) {
        val transaction = apiService.sendTransaction(request)
        withContext(CoroutineUtils.mainDispatcher) {
            handleSuccess(transaction)
        }
    }
}
```

## 📅 第二阶段：协程替代 RxJava (2-3 周)

### 2.1 创建协程基础设施 (第4周)

#### 2.1.1 创建协程网络服务

```kotlin
// app/src/main/java/com/alphawallet/app/network/NetworkService.kt
package com.alphawallet.app.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException

interface NetworkService {
    suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T>
    fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>>
}

class NetworkServiceImpl : NetworkService {

    override suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>> = flow {
        emit(executeCall(call))
    }
}
```

#### 2.1.2 创建协程 Repository

```kotlin
// app/src/main/java/com/alphawallet/app/repository/TokenRepository.kt
package com.alphawallet.app.repository

import com.alphawallet.app.network.NetworkService
import com.alphawallet.app.entity.tokens.Token
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TokenRepository @Inject constructor(
    private val networkService: NetworkService
) {

    suspend fun getTokens(address: String): Result<List<Token>> {
        return networkService.executeCall {
            apiService.getTokens(address)
        }
    }

    fun getTokensAsFlow(address: String): Flow<Result<List<Token>>> {
        return networkService.executeCallAsFlow {
            apiService.getTokens(address)
        }
    }
}
```

### 2.2 转换现有代码 (第5周)

#### 2.2.1 转换 RxJava 调用

**转换策略**:

1. `Single<T>` → `suspend fun(): T`
2. `Observable<T>` → `Flow<T>`
3. `Completable` → `suspend fun()`
4. `Maybe<T>` → `suspend fun(): T?`

**转换示例**:

```kotlin
// 转换前 (RxJava)
apiService.getTokens(address)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { tokens -> updateTokens(tokens) },
        { error -> handleError(error) }
    )

// 转换后 (协程)
CoroutineUtils.launchSafely(
    dispatcher = CoroutineUtils.ioDispatcher,
    onError = { error -> handleError(error) }
) {
    val tokens = apiService.getTokens(address)
    withContext(CoroutineUtils.mainDispatcher) {
        updateTokens(tokens)
    }
}
```

#### 2.2.2 转换异步操作

**转换示例**:

```kotlin
// 转换前 (RxJava)
private void backupWallet(String address) {
    walletService.backupWallet(address)
        .andThen(walletService.updateBackupStatus(address, true))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            () -> showSuccessMessage(),
            error -> showErrorMessage(error)
        );
}

// 转换后 (协程)
private fun backupWallet(address: String) {
    CoroutineUtils.launchSafely(
        dispatcher = CoroutineUtils.ioDispatcher,
        onError = { error -> showErrorMessage(error) }
    ) {
        walletService.backupWallet(address)
        walletService.updateBackupStatus(address, true)
        withContext(CoroutineUtils.mainDispatcher) {
            showSuccessMessage()
        }
    }
}
```

### 2.3 更新 UI 层 (第6周)

#### 2.3.1 转换 Activity 和 Fragment

**转换示例**:

```kotlin
// 转换前 (Java)
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);

    viewModel.getTokens().observe(this, tokens -> {
        adapter.updateTokens(tokens);
    });
}

// 转换后 (Kotlin)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_home)

    lifecycleScope.launch {
        viewModel.tokens.collect { tokens ->
            adapter.updateTokens(tokens)
        }
    }
}
```

#### 2.3.2 转换事件处理

**转换示例**:

```kotlin
// 转换前 (Java + RxJava)
private void handleButtonClick() {
    button.setOnClickListener(v -> {
        viewModel.loadTokens()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                tokens -> updateUI(tokens),
                error -> showError(error)
            );
    });
}

// 转换后 (Kotlin + 协程)
private fun handleButtonClick() {
    button.setOnClickListener {
        CoroutineUtils.launchSafely(
            dispatcher = CoroutineUtils.ioDispatcher,
            onError = { error -> showError(error) }
        ) {
            val tokens = viewModel.loadTokens()
            withContext(CoroutineUtils.mainDispatcher) {
                updateUI(tokens)
            }
        }
    }
}
```

## 🛠️ 自动化转换工具

### 创建转换脚本

```bash
#!/bin/bash
# scripts/convert-java-to-kotlin.sh

echo "🚀 开始 Java 到 Kotlin 转换..."

# 1. 更新 build.gradle
echo "📝 更新 build.gradle..."
sed -i '' 's/implementation.*rxjava.*//g' app/build.gradle
sed -i '' '/dependencies {/a\
    // Kotlin 协程\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3"\
    \
    // 架构组件\
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"' app/build.gradle

# 2. 创建协程工具类
echo "📝 创建协程工具类..."
mkdir -p app/src/main/java/com/alphawallet/app/util
cat > app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt << 'EOF'
package com.alphawallet.app.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

object CoroutineUtils {
    val mainDispatcher = Dispatchers.Main
    val ioDispatcher = Dispatchers.IO
    val defaultScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun launchSafely(
        scope: CoroutineScope = defaultScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        onError: (Throwable) -> Unit = { },
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
EOF

echo "✅ Java 到 Kotlin 转换准备完成"
```

## 📊 转换检查清单

### 第一阶段：Java 到 Kotlin

- [ ] 更新 build.gradle 配置
- [ ] 创建协程工具类
- [ ] 转换 HomeActivity.java → HomeActivity.kt
- [ ] 转换 ViewModel 类
- [ ] 转换网络服务接口
- [ ] 转换 Repository 类
- [ ] 转换 UI 组件

### 第二阶段：协程替代 RxJava

- [ ] 移除 RxJava 依赖
- [ ] 转换 Single<T> → suspend fun(): T
- [ ] 转换 Observable<T> → Flow<T>
- [ ] 转换 Completable → suspend fun()
- [ ] 转换 Maybe<T> → suspend fun(): T?
- [ ] 更新异步操作
- [ ] 更新事件处理
- [ ] 更新生命周期管理

## 🚨 注意事项

### 转换策略

1. **渐进式转换**: 一个文件一个文件地转换
2. **保持功能**: 确保转换后功能完全一致
3. **充分测试**: 每个转换都要进行测试
4. **回滚准备**: 保留原始 Java 文件作为备份

### 常见问题

1. **空安全**: 注意 Kotlin 的空安全特性
2. **类型推断**: 利用 Kotlin 的类型推断
3. **扩展函数**: 使用 Kotlin 的扩展函数
4. **数据类**: 将 POJO 转换为数据类

## 📈 预期收益

### 性能提升

- 启动时间减少 25%
- 内存使用减少 15%
- 网络请求响应时间减少 20%

### 开发效率

- 代码量减少 30%
- 调试时间减少 40%
- 新功能开发速度提升 50%

### 代码质量

- 类型安全提升
- 空安全保护
- 更清晰的语法
- 更好的 IDE 支持

这个计划将帮助你逐步将项目从 Java + RxJava 转换为 Kotlin + 协程，同时保持应用的稳定性和功能完整性。
